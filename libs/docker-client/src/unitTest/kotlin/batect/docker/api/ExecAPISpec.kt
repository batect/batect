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

package batect.docker.api

import batect.docker.DockerContainer
import batect.docker.DockerExecInstance
import batect.docker.DockerExecInstanceInfo
import batect.docker.DockerHttpConfig
import batect.docker.ExecCreationRequest
import batect.docker.ExecFailedException
import batect.docker.Json
import batect.docker.run.ConnectionHijacker
import batect.docker.run.ContainerOutputStream
import batect.os.SystemInfo
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.mock
import batect.testutils.mockGet
import batect.testutils.mockPost
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.BufferedSink
import okio.BufferedSource
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.TimeUnit

object ExecAPISpec : Spek({
    describe("a Docker container exec API") {
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
        val api by createForEachTest { ExecAPI(httpConfig, systemInfo, logger, { hijacker }) }

        val errorResponse = """{"message": "Something went wrong.\nMore details on next line."}"""
        val errorMessageWithCorrectLineEndings = "Something went wrong.SYSTEM_LINE_SEPARATORMore details on next line."

        describe("creating an exec instance") {
            val clientWithLongTimeout by createForEachTest { mock<OkHttpClient>() }
            val longTimeoutClientBuilder by createForEachTest {
                mock<OkHttpClient.Builder> { mock ->
                    on { readTimeout(any(), any()) } doReturn mock
                    on { build() } doReturn clientWithLongTimeout
                }
            }

            beforeEachTest {
                whenever(httpClient.newBuilder()).doReturn(longTimeoutClientBuilder)
            }

            given("a container and exec creation request") {
                val container = DockerContainer("abc123")
                val request = ExecCreationRequest(false, true, true, true, emptyMap(), emptyList(), false, null, "/some/work/dir")
                val expectedUrl = "$dockerBaseUrl/v1.37/containers/abc123/exec"

                on("a successful creation") {
                    val call by createForEachTest { clientWithLongTimeout.mockPost(expectedUrl, """{"Id": "xyz456"}""", 201) }
                    val result by runForEachTest { api.create(container, request) }

                    it("creates the exec instance") {
                        verify(call).execute()
                    }

                    it("creates the container with the expected settings") {
                        verify(clientWithLongTimeout).newCall(
                            requestWithJsonBody { body ->
                                assertThat(body, equalTo(Json.default.parseToJsonElement(request.toJson())))
                            }
                        )
                    }

                    it("returns the ID of the created exec instance") {
                        assertThat(result.id, equalTo("xyz456"))
                    }

                    it("configures the HTTP client with a longer timeout to allow for the exec instance to be created") {
                        verify(longTimeoutClientBuilder).readTimeout(30, TimeUnit.SECONDS)
                    }
                }

                on("a failed creation") {
                    beforeEachTest { clientWithLongTimeout.mockPost(expectedUrl, errorResponse, 418) }

                    it("raises an appropriate exception") {
                        assertThat({ api.create(container, request) }, throws<ExecFailedException>(withMessage("Output from Docker was: $errorMessageWithCorrectLineEndings")))
                    }
                }
            }
        }

        describe("starting an exec instance") {
            val source by createForEachTest { mock<BufferedSource>() }
            val sink by createForEachTest { mock<BufferedSink>() }
            val hijackableHttpClient by createForEachTest { mock<OkHttpClient>() }

            val clientBuilder by createForEachTest {
                mock<OkHttpClient.Builder> { mock ->
                    on { readTimeout(any(), any()) } doReturn mock
                    on { connectionPool(any()) } doReturn mock
                    on { addNetworkInterceptor(hijacker) } doAnswer {
                        whenever(hijacker.source).doReturn(source)
                        whenever(hijacker.sink).doReturn(sink)

                        mock
                    }
                    on { build() } doReturn hijackableHttpClient
                }
            }

            beforeEachTest {
                whenever(httpClient.newBuilder()).doReturn(clientBuilder)
            }

            given("an exec instance and creation request") {
                val attachTty = true
                val request = ExecCreationRequest(false, false, false, attachTty, emptyMap(), emptyList(), false, null, "/some/work/dir")
                val instance = DockerExecInstance("the-exec-instance")
                val expectedHeaders = Headers.Builder()
                    .add("Connection", "Upgrade")
                    .add("Upgrade", "tcp")
                    .build()

                val expectedUrl = "$dockerBaseUrl/v1.37/exec/the-exec-instance/start"

                on("starting the exec instance succeeding") {
                    val response = mock<Response> {
                        on { code } doReturn 200
                        on { isSuccessful } doReturn true
                    }

                    beforeEachTest { hijackableHttpClient.mock("POST", expectedUrl, response, expectedHeaders) }

                    val stream by runForEachTest { api.start(request, instance) }

                    it("starts the instance with the expected settings") {
                        verify(hijackableHttpClient).newCall(
                            requestWithJsonBody { body ->
                                assertThat(
                                    body,
                                    equalTo(
                                        buildJsonObject {
                                            put("Detach", false)
                                            put("Tty", attachTty)
                                        }
                                    )
                                )
                            }
                        )
                    }

                    it("returns the stream from the underlying connection") {
                        assertThat(stream, equalTo(ContainerOutputStream(response, source)))
                    }

                    it("does not close the underlying connection") {
                        verify(response, never()).close()
                    }

                    it("configures the HTTP client with no timeout") {
                        verify(clientBuilder).readTimeout(eq(0), any())
                    }

                    it("configures the HTTP client with a separate connection pool that does not evict connections (because the underlying connection cannot be reused and because we don't want to evict the connection just because there hasn't been any output for a while)") {
                        verify(clientBuilder).connectionPool(connectionPoolWithNoEviction())
                    }
                }

                on("an unsuccessful start attempt") {
                    beforeEachTest { hijackableHttpClient.mockPost(expectedUrl, errorResponse, 418, expectedHeaders) }

                    it("raises an appropriate exception") {
                        assertThat({ api.start(request, instance) }, throws<ExecFailedException>(withMessage("Starting exec instance 'the-exec-instance' failed: $errorMessageWithCorrectLineEndings")))
                    }
                }
            }
        }

        describe("inspecting an exec instance") {
            given("an exec instance") {
                val execInstance = DockerExecInstance("some-instance-id")
                val expectedUrl = "$dockerBaseUrl/v1.37/exec/some-instance-id/json"

                given("inspecting the exec instance succeeds") {
                    val response = """
                    {
                        "CanRemove": false,
                        "ContainerID": "b53ee82b53a40c7dca428523e34f741f3abc51d9f297a14ff874bf761b995126",
                        "DetachKeys": "",
                        "ExitCode": 2,
                        "ID": "f33bbfb39f5b142420f4759b2348913bd4a8d1a6d7fd56499cb41a1bb91d7b3b",
                        "OpenStderr": true,
                        "OpenStdin": true,
                        "OpenStdout": true,
                        "ProcessConfig": {
                            "arguments": ["-c", "exit 2"],
                            "entrypoint": "sh",
                            "privileged": false,
                            "tty": true,
                            "user": "1000"
                        },
                        "Running": false,
                        "Pid": 42000
                    }
                    """.trimIndent()

                    beforeEachTest { httpClient.mockGet(expectedUrl, response, 200) }

                    on("inspecting the exec instance") {
                        val details by runForEachTest { api.inspect(execInstance) }

                        it("returns the current state of the instance") {
                            assertThat(details, equalTo(DockerExecInstanceInfo(2, false)))
                        }
                    }
                }

                on("inspecting the exec instance failing") {
                    beforeEachTest { httpClient.mockGet(expectedUrl, errorResponse, 418) }

                    it("throws an appropriate exception") {
                        assertThat(
                            { api.inspect(execInstance) },
                            throws<ExecInstanceInspectionFailedException>(withMessage("Could not inspect exec instance 'some-instance-id': $errorMessageWithCorrectLineEndings"))
                        )
                    }
                }
            }
        }
    }
})
