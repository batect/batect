/*
   Copyright 2017-2021 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.docker.build.buildkit

import batect.docker.build.buildkit.services.UnsupportedGrpcMethodException
import batect.logging.LogMessageBuilder
import batect.logging.Logger
import com.squareup.wire.internal.GrpcMessageSink
import com.squareup.wire.internal.GrpcMessageSource
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.Headers
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.Header
import okhttp3.internal.http2.Http2Connection
import okhttp3.internal.http2.Http2Stream
import okio.buffer
import java.util.concurrent.ConcurrentLinkedQueue

// This was inspired by Wire's mockwebserver GrpcDispatcher.
// https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md and https://github.com/grpc/grpc/blob/master/doc/statuscodes.md
// are also useful references.
class GrpcListener(
    val sessionId: String,
    val endpoints: Map<String, GrpcEndpoint<*, *, *>>,
    val logger: Logger
) : Http2Connection.Listener() {
    private val exceptionsCollector = ConcurrentLinkedQueue<Throwable>()
    val exceptionsThrownDuringProcessing: List<Throwable>
        get() = exceptionsCollector.toList()

    override fun onStream(stream: Http2Stream) {
        val headers = stream.takeHeaders()

        logger.info {
            message("Received request")
            data("streamId", stream.id)
            data("sessionId", sessionId)
            data("headers", headers.toMultimap())
        }

        val method = headers[Header.TARGET_METHOD_UTF8]

        if (method != "POST") {
            logger.warn {
                message("Request has unexpected HTTP method, returning error")
                data("streamId", stream.id)
                data("sessionId", sessionId)
                data("method", method)
            }

            stream.sendHttpError(HttpStatus.MethodNotAllowed)
            return
        }

        val contentType = headers["content-type"]

        if (contentType != grpcContentType) {
            logger.warn {
                message("Request has unexpected content type, returning error")
                data("streamId", stream.id)
                data("sessionId", sessionId)
                data("contentType", contentType)
            }

            stream.sendHttpError(HttpStatus.UnsupportedMediaType)
            return
        }

        val path = headers[Header.TARGET_PATH_UTF8]

        if (path == null) {
            logger.error {
                message("Request does not contain a path")
                data("streamId", stream.id)
                data("sessionId", sessionId)
            }

            stream.sendGrpcError(GrpcStatus.Internal, "No path provided")
            return
        }

        val endpoint = endpoints[path]

        if (endpoint == null) {
            logger.warn {
                message("Request is for an unknown endpoint, returning error")
                data("streamId", stream.id)
                data("sessionId", sessionId)
                data("path", path)
            }

            stream.sendGrpcError(GrpcStatus.Unimplemented, "No handler for this service or method")
            return
        }

        executeCall(endpoint, headers, stream)
    }

    private fun <ServiceType : Any, RequestType : Any, ResponseType : Any> executeCall(
        endpoint: GrpcEndpoint<ServiceType, RequestType, ResponseType>,
        headers: Headers,
        stream: Http2Stream
    ) {
        stream.sendResponseHeaders()

        try {
            val serviceInstance = endpoint.serviceInstanceFactory.invoke(headers)

            GrpcMessageSource(stream.getSource().buffer(), endpoint.requestAdaptor, headers[grpcEncoding]).use { source ->
                // Important: don't call close() on GrpcMessageSink: it closes the underlying stream, causing writing the trailers
                // to silently fail later on.
                val sink = GrpcMessageSink(stream.getSink().buffer(), endpoint.responseAdaptor, null, identityEncoding)

                endpoint.method.invoke(serviceInstance, source, sink)
                stream.sendResponseTrailers(GrpcStatus.OK)

                logger.warn {
                    message("Request processed successfully")
                    data("streamId", stream.id)
                    data("sessionId", sessionId)
                }
            }
        } catch (e: UnsupportedGrpcMethodException) {
            logger.warn {
                message("Endpoint handler threw ${UnsupportedGrpcMethodException::class.simpleName}, returning error")
                data("streamId", stream.id)
                data("sessionId", sessionId)
                exception(e)
            }

            stream.sendResponseTrailers(GrpcStatus.Unimplemented, "Service does not support this method")
        } catch (t: Throwable) {
            logger.error {
                message("Endpoint handler threw exception, returning error")
                data("streamId", stream.id)
                data("sessionId", sessionId)
                exception(t)
            }

            stream.sendResponseTrailers(GrpcStatus.Unknown, t.javaClass.name)

            exceptionsCollector.add(t)
        }
    }

    private fun LogMessageBuilder.data(key: String, value: Map<String, List<String>>) = data(key, value, MapSerializer(String.serializer(), ListSerializer(String.serializer())))

    private companion object {
        const val identityEncoding = "identity"
        const val grpcEncoding = "grpc-encoding"
        const val grpcContentType = "application/grpc"

        private fun Http2Stream.sendHttpError(status: HttpStatus) {
            val responseHeaders = listOf(Header(Header.RESPONSE_STATUS_UTF8, status.code.toString()))
            writeHeaders(responseHeaders, true, true)
        }

        private fun Http2Stream.sendResponseHeaders() {
            val responseHeaders = listOf(
                Header(Header.RESPONSE_STATUS_UTF8, "200"),
                Header(grpcEncoding, identityEncoding),
                Header("content-type", grpcContentType)
            )

            writeHeaders(responseHeaders, false, true)
        }

        private fun Http2Stream.sendResponseTrailers(status: GrpcStatus, message: String = status.name) {
            val responseHeaders = listOf(
                Header("grpc-status", status.code.toString()),
                Header("grpc-message", message)
            )

            writeHeaders(responseHeaders, true, true)
        }

        private fun Http2Stream.sendGrpcError(status: GrpcStatus, message: String) {
            val responseHeaders = listOf(
                Header(Header.RESPONSE_STATUS_UTF8, "200"),
                Header("content-type", grpcContentType),
                Header("grpc-status", status.code.toString()),
                Header("grpc-message", message)
            )

            writeHeaders(responseHeaders, true, true)
        }

        private fun Http2Stream.closeWithNoError() {
            close(ErrorCode.NO_ERROR, null)
        }
    }
}

private enum class HttpStatus(val code: Int) {
    OK(200),
    MethodNotAllowed(405),
    UnsupportedMediaType(415)
}

// See https://github.com/grpc/grpc/blob/master/doc/statuscodes.md for definitions.
private enum class GrpcStatus(val code: Int) {
    OK(0),
    Unknown(2),
    Internal(13),
    Unimplemented(12),
}
