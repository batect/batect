/*
   Copyright 2017-2020 Charles Korn.

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

import batect.docker.build.buildkit.services.Endpoint
import batect.docker.build.buildkit.services.ServiceWithEndpointMetadata
import batect.docker.build.buildkit.services.UnsupportedGrpcMethodException
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.internal.GrpcMessageSink
import com.squareup.wire.internal.GrpcMessageSource
import okhttp3.Headers
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.Header
import okhttp3.internal.http2.Http2Connection
import okhttp3.internal.http2.Http2Stream
import okio.buffer

// This was inspired by Wire's mockwebserver GrpcDispatcher.
// https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md and https://github.com/grpc/grpc/blob/master/doc/statuscodes.md
// are also useful references.
class GrpcListener(val services: Set<ServiceWithEndpointMetadata>) : Http2Connection.Listener() {
    private val endpoints: Map<String, Endpoint<*, *>> = services.flatMap { it.getEndpoints().entries }.associate { it.toPair() }

    private val Class<*>.protoAdapter: ProtoAdapter<*>
        get() = declaredFields.single { it.name == "ADAPTER" }.get(null) as ProtoAdapter<*>

    override fun onStream(stream: Http2Stream) {
        val headers = stream.takeHeaders()

        // println("Stream ${stream.id} received request:")
        // println(headers)

        if (headers[Header.TARGET_METHOD_UTF8] != "POST") {
            stream.sendHttpError(HttpStatus.MethodNotAllowed)
            return
        }

        if (headers["content-type"] != grpcContentType) {
            stream.sendHttpError(HttpStatus.UnsupportedMediaType)
            return
        }

        val path = headers[Header.TARGET_PATH_UTF8]
        val endpoint = endpoints[path]

        if (endpoint == null) {
            stream.sendGrpcError(GrpcStatus.Unimplemented)
            return
        }

        executeCall(endpoint, headers, stream)
    }

    private fun <RequestType : Any, ResponseType : Any> executeCall(endpoint: Endpoint<RequestType, ResponseType>, headers: Headers, stream: Http2Stream) {
        stream.sendResponseHeaders()

        try {
            val source = GrpcMessageSource(stream.getSource().buffer(), endpoint.requestAdaptor, headers[grpcEncoding])
            val request = source.readExactlyOneAndClose()
            val response = endpoint.method.invoke(request)

            // Important: don't call close() on GrpcMessageSink: it closes the underlying stream, causing writing the trailers
            // to silently fail later on.
            val messageSink = GrpcMessageSink(stream.getSink().buffer(), endpoint.responseAdaptor, null, identityEncoding)
            messageSink.write(response)

            stream.sendResponseTrailers(GrpcStatus.OK)
        } catch (e: UnsupportedGrpcMethodException) {
            // TODO: logging
            stream.sendResponseTrailers(GrpcStatus.Unimplemented)
        } catch (t: Throwable) {
            // TODO: logging
            stream.sendResponseTrailers(GrpcStatus.Unknown)
        }
    }

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

        private fun Http2Stream.sendResponseTrailers(status: GrpcStatus) {
            val responseHeaders = listOf(
                Header("grpc-status", status.code.toString())
            )

            writeHeaders(responseHeaders, true, true)
        }

        private fun Http2Stream.sendGrpcError(status: GrpcStatus) {
            val responseHeaders = listOf(
                Header(Header.RESPONSE_STATUS_UTF8, "200"),
                Header("content-type", grpcContentType),
                Header("grpc-status", status.code.toString())
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

private enum class GrpcStatus(val code: Int) {
    OK(0),
    Unknown(2),
    Unimplemented(12),
}
