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
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.squareup.wire.MessageSink
import io.grpc.health.v1.HealthBlockingServer
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse
import okhttp3.Headers
import okhttp3.internal.http2.Header
import okhttp3.internal.http2.Http2Stream
import okio.ByteString.Companion.toByteString
import okio.sink
import okio.source
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object GrpcListenerSpec : Spek({
    describe("a gRPC listener") {
        val service by createForEachTest { MockHealthService() }
        val listener by createForEachTest { GrpcListener(setOf(service)) }
        val stream by createForEachTest { mock<Http2Stream>() }

        fun Suite.itImmediatelyRespondsWithHttpError(code: Int, message: String) {
            it("immediately responds with a HTTP $code ($message)") {
                verify(stream).writeHeaders(listOf(Header(":status", code.toString())), true, true)
            }

            it("does not write a response body") {
                verify(stream, never()).getSink()
            }

            it("does not write any trailers") {
                verify(stream, never()).enqueueTrailers(any())
            }

            it("does not close the stream") {
                verify(stream, never()).close(any(), anyOrNull())
            }
        }

        fun Suite.itImmediatelyRespondsWithGrpcError(code: Int, message: String) {
            it("immediately responds with a gRPC $code ($message)") {
                verify(stream).writeHeaders(
                    listOf(
                        Header(":status", "200"),
                        Header("content-type", "application/grpc"),
                        Header("grpc-status", code.toString())
                    ),
                    true,
                    true
                )
            }

            it("does not write a response body") {
                verify(stream, never()).getSink()
            }

            it("does not write any trailers") {
                verify(stream, never()).enqueueTrailers(any())
            }

            it("does not close the stream") {
                verify(stream, never()).close(any(), anyOrNull())
            }
        }

        fun Suite.itRespondsWithHTTPHeaders() {
            it("responds with a HTTP 200 status code and content headers") {
                verify(stream).writeHeaders(
                    listOf(
                        Header(":status", "200"),
                        Header("grpc-encoding", "identity"),
                        Header("content-type", "application/grpc")
                    ),
                    false,
                    true
                )
            }
        }

        fun Suite.itRespondsWithGrpcStatusCode(code: Int, message: String) {
            it("writes the gRPC $message status code as a trailer") {
                verify(stream).writeHeaders(listOf(Header("grpc-status", code.toString())), true, true)
            }
        }

        describe("handling incoming requests") {
            given("the request is a HTTP POST") {
                given("the request has the gRPC content type") {
                    given("the request is for an available service and method") {
                        data class TestCase(
                            val description: String,
                            val requestBytes: ByteArray,
                            val expectedRequestBody: HealthCheckRequest,
                            val expectedResponseBytes: ByteArray,
                            val responseBody: HealthCheckResponse
                        )

                        val emptyRequestBody = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00)

                        setOf(
                            TestCase(
                                "an empty request body and empty response body",
                                emptyRequestBody,
                                HealthCheckRequest(),
                                byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00),
                                HealthCheckResponse()
                            ),
                            TestCase(
                                "an empty request body and non-empty response body",
                                emptyRequestBody,
                                HealthCheckRequest(),
                                byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x02, 0x08, 0x01),
                                HealthCheckResponse(HealthCheckResponse.ServingStatus.SERVING)
                            ),
                            TestCase(
                                "a non-empty request body and empty response body",
                                byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x06, 0x0a, 0x04, 0x74, 0x65, 0x73, 0x74),
                                HealthCheckRequest("test"),
                                byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00),
                                HealthCheckResponse()
                            ),
                            TestCase(
                                "a non-empty request body and non-empty response body",
                                byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x06, 0x0a, 0x04, 0x74, 0x65, 0x73, 0x74),
                                HealthCheckRequest("test"),
                                byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x02, 0x08, 0x01),
                                HealthCheckResponse(HealthCheckResponse.ServingStatus.SERVING)
                            ),
                        ).forEach { testCase ->
                            given(testCase.description) {
                                val bodyWritten by createForEachTest { ByteArrayOutputStream() }

                                beforeEachTest {
                                    whenever(stream.takeHeaders()).doReturn(headersForRequest("/grpc.health.v1.Health/Check"))
                                    whenever(stream.getSource()).doReturn(ByteArrayInputStream(testCase.requestBytes).source())
                                    whenever(stream.getSink()).doReturn(bodyWritten.sink())

                                    service.responseToSend = testCase.responseBody
                                }

                                beforeEachTest { listener.onStream(stream) }

                                itRespondsWithHTTPHeaders()

                                it("calls the service with the decoded request body") {
                                    assertThat(service.requestReceived, equalTo(testCase.expectedRequestBody))
                                }

                                it("writes the expected response body") {
                                    assertThat(bodyWritten.toByteArray().toByteString(), equalTo(testCase.expectedResponseBytes.toByteString()))
                                }

                                itRespondsWithGrpcStatusCode(0, "OK")

                                it("does not close the stream") {
                                    verify(stream, never()).close(any(), anyOrNull())
                                }
                            }
                        }

                        given("the service throws an UnsupportedGrpcMethodException") {
                            beforeEachTest {
                                whenever(stream.takeHeaders()).doReturn(headersForRequest("/grpc.health.v1.Health/Check"))
                                whenever(stream.getSource()).doReturn(ByteArrayInputStream(emptyRequestBody).source())

                                service.exceptionToThrow = UnsupportedGrpcMethodException("/grpc.health.v1.Health/Blah")
                            }

                            beforeEachTest { listener.onStream(stream) }

                            itRespondsWithHTTPHeaders()
                            itRespondsWithGrpcStatusCode(12, "unimplemented error")
                        }

                        given("the service throws another kind of exception") {
                            beforeEachTest {
                                whenever(stream.takeHeaders()).doReturn(headersForRequest("/grpc.health.v1.Health/Check"))
                                whenever(stream.getSource()).doReturn(ByteArrayInputStream(emptyRequestBody).source())

                                service.exceptionToThrow = RuntimeException("Something went wrong.")
                            }

                            beforeEachTest { listener.onStream(stream) }

                            itRespondsWithHTTPHeaders()
                            itRespondsWithGrpcStatusCode(2, "unknown error")
                        }
                    }

                    given("the request is for an unknown service or method") {
                        beforeEachTest { whenever(stream.takeHeaders()).doReturn(headersForRequest("/grpc.health.v1.Health/SomethingElse")) }
                        beforeEachTest { listener.onStream(stream) }

                        itImmediatelyRespondsWithGrpcError(12, "unimplemented")
                    }
                }

                given("the request does not hae the gRPC content type") {
                    beforeEachTest {
                        whenever(stream.takeHeaders()).doReturn(
                            headersForRequest(
                                "/grpc.health.v1.Health/Check",
                                contentType = "application/json"
                            )
                        )
                    }
                    beforeEachTest { listener.onStream(stream) }

                    itImmediatelyRespondsWithHttpError(415, "unsupported media type")
                }
            }

            given("the request is not a HTTP POST") {
                beforeEachTest { whenever(stream.takeHeaders()).doReturn(headersForRequest("/grpc.health.v1.Health/Check", method = "GET")) }
                beforeEachTest { listener.onStream(stream) }

                itImmediatelyRespondsWithHttpError(405, "method not allowed")
            }
        }
    }
})

private fun headersForRequest(path: String, method: String = "POST", contentType: String = "application/grpc"): Headers =
    Headers.headersOf(
        ":method", method,
        ":scheme", "http",
        ":path", path,
        ":authority", "localhost",
        "content-type", contentType
    )

private class MockHealthService : HealthBlockingServer, ServiceWithEndpointMetadata {
    var requestReceived: HealthCheckRequest? = null
        private set

    var responseToSend: HealthCheckResponse = HealthCheckResponse()
    var exceptionToThrow: Exception? = null

    override fun Check(request: HealthCheckRequest): HealthCheckResponse {
        if (exceptionToThrow != null) {
            throw exceptionToThrow!!
        }

        requestReceived = request
        return responseToSend
    }

    override fun Watch(request: HealthCheckRequest, response: MessageSink<HealthCheckResponse>) {
        throw UnsupportedOperationException("Watch() not supported")
    }

    override fun getEndpoints(): Map<String, Endpoint<*, *>> {
        return mapOf(
            HealthBlockingServer::Check.path to Endpoint(::Check, HealthCheckRequest.ADAPTER, HealthCheckResponse.ADAPTER)
        )
    }
}
