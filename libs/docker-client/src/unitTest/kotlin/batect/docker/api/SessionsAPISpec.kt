/*
    Copyright 2017-2022 Charles Korn.

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

package batect.docker.api

import batect.docker.DockerException
import batect.docker.DockerHttpConfig
import batect.docker.build.buildkit.BuildKitSession
import batect.docker.build.buildkit.GrpcListener
import batect.docker.run.ConnectionHijacker
import batect.os.SystemInfo
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.mock
import batect.testutils.mockPost
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.BufferedSink
import okio.BufferedSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.Socket

object SessionsAPISpec : Spek({
    describe("a Docker sessions API client") {
        val dockerHost = "the-docker-daemon"
        val dockerBaseUrl = "http://$dockerHost"
        val httpClient by createForEachTest { mock<OkHttpClient>() }
        val httpConfig by createForEachTest {
            mock<DockerHttpConfig> {
                on { client } doReturn httpClient
                on { baseUrl } doReturn dockerBaseUrl.toHttpUrl()
            }
        }

        val systemInfo by createForEachTest {
            mock<SystemInfo> {
                on { lineSeparator } doReturn "SYSTEM_LINE_SEPARATOR"
            }
        }

        val logger by createLoggerForEachTestWithoutCustomSerializers()
        val hijacker by createForEachTest { mock<ConnectionHijacker>() }
        val api by createForEachTest { SessionsAPI(httpConfig, systemInfo, logger, { hijacker }) }

        val errorResponse = """{"message": "Something went wrong.\nMore details on next line."}"""
        val errorMessageWithCorrectLineEndings = "Something went wrong.SYSTEM_LINE_SEPARATORMore details on next line."

        describe("creating a session") {
            val socket by createForEachTest { mock<Socket>() }
            val sink by createForEachTest { mock<BufferedSink>() }
            val source by createForEachTest { mock<BufferedSource>() }
            val attachHttpClient by createForEachTest { mock<OkHttpClient>() }

            val clientBuilder by createForEachTest {
                mock<OkHttpClient.Builder> { mock ->
                    on { readTimeout(any(), any()) } doReturn mock
                    on { connectionPool(any()) } doReturn mock
                    on { addNetworkInterceptor(hijacker) } doAnswer {
                        whenever(hijacker.socket).doReturn(socket)
                        whenever(hijacker.sink).doReturn(sink)
                        whenever(hijacker.source).doReturn(source)

                        mock
                    }
                    on { build() } doReturn attachHttpClient
                }
            }

            beforeEachTest {
                whenever(httpClient.newBuilder()).doReturn(clientBuilder)
            }

            val grpcListener by createForEachTest {
                mock<GrpcListener>() {
                    on { endpoints } doReturn mapOf("/my.v1.Service/SomeMethod" to mock(), "/my.v1.Service/SomeOtherMethod" to mock())
                }
            }

            val session by createForEachTest { BuildKitSession("session-id-123", "build-id-123", "session-name-123", "session-shared-key-123", grpcListener, mock()) }
            val expectedUrl = "$dockerBaseUrl/v1.37/session"

            given("creating the session succeeds") {
                val response = mock<Response> {
                    on { code } doReturn 101
                }

                val call by createForEachTest { attachHttpClient.mock("POST", expectedUrl, response) }

                val streams by runForEachTest { api.create(session) }

                it("returns the stream from the underlying connection") {
                    assertThat(streams, equalTo(SessionStreams(socket, source, sink)))
                }

                it("does not close the underlying connection") {
                    verify(response, never()).close()
                }

                it("configures the HTTP client with no timeout") {
                    verify(clientBuilder).readTimeout(eq(0), any())
                }

                it("configures the HTTP client with a separate connection pool that does not evict connections (because the underlying connection cannot be reused and because we don't want to evict the connection just because there hasn't been any activity for a while)") {
                    verify(clientBuilder).connectionPool(connectionPoolWithNoEviction())
                }

                it("sends headers to instruct the daemon to switch to HTTP/2 sockets and includes details of the session") {
                    val expectedHeaders = Headers.Builder()
                        .add("Connection", "Upgrade")
                        .add("Upgrade", "h2c")
                        .add("X-Docker-Expose-Session-Uuid", "session-id-123")
                        .add("X-Docker-Expose-Session-Name", "session-name-123")
                        .add("X-Docker-Expose-Session-Sharedkey", "session-shared-key-123")
                        .add("X-Docker-Expose-Session-Grpc-Method", "/my.v1.Service/SomeMethod")
                        .add("X-Docker-Expose-Session-Grpc-Method", "/my.v1.Service/SomeOtherMethod")
                        .build()

                    assertThat(call.request().headers, equalTo(expectedHeaders))
                }
            }

            given("creating the session fails") {
                beforeEachTest { attachHttpClient.mockPost(expectedUrl, errorResponse, 418) }

                it("raises an appropriate exception") {
                    assertThat({ api.create(session) }, throws<DockerException>(withMessage("Creating session failed: $errorMessageWithCorrectLineEndings")))
                }
            }
        }
    }
})
