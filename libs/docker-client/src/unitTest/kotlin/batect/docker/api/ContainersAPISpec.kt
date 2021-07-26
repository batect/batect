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

package batect.docker.api

import batect.docker.ContainerCreationFailedException
import batect.docker.ContainerCreationRequest
import batect.docker.ContainerDirectory
import batect.docker.ContainerFile
import batect.docker.DockerContainer
import batect.docker.DockerContainerConfiguration
import batect.docker.DockerContainerHealthCheckConfig
import batect.docker.DockerContainerHealthCheckState
import batect.docker.DockerContainerInfo
import batect.docker.DockerContainerState
import batect.docker.DockerEvent
import batect.docker.DockerException
import batect.docker.DockerHealthCheckResult
import batect.docker.DockerHttpConfig
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.docker.HealthCheckConfig
import batect.docker.Json
import batect.docker.run.ConnectionHijacker
import batect.docker.run.ContainerInputStream
import batect.docker.run.ContainerOutputDecoder
import batect.docker.run.ContainerOutputStream
import batect.os.Dimensions
import batect.os.SystemInfo
import batect.primitives.CancellationContext
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.mock
import batect.testutils.mockDelete
import batect.testutils.mockGet
import batect.testutils.mockPost
import batect.testutils.mockPut
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import jnr.constants.platform.Signal
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
import java.time.Duration
import java.util.concurrent.TimeUnit

object ContainersAPISpec : Spek({
    describe("a Docker containers API client") {
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
        val api by createForEachTest { ContainersAPI(httpConfig, systemInfo, logger, { hijacker }) }

        val errorResponse = """{"message": "Something went wrong.\nMore details on next line."}"""
        val errorMessageWithCorrectLineEndings = "Something went wrong.SYSTEM_LINE_SEPARATORMore details on next line."

        describe("creating a container") {
            val expectedUrl = "$dockerBaseUrl/v1.37/containers/create?name=the-container"
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

            given("a container configuration and a built image") {
                val image = DockerImage("the-image")
                val network = DockerNetwork("the-network")
                val command = listOf("doStuff")
                val entrypoint = listOf("sh")
                val request = ContainerCreationRequest("the-container", image, network, command, entrypoint, "some-host", setOf("some-host"), emptyMap(), emptyMap(), "/some-dir", emptySet(), emptySet(), emptySet(), HealthCheckConfig(), null, false, false, emptySet(), emptySet(), true, true, "json-file", emptyMap(), null)

                on("a successful creation") {
                    val call by createForEachTest { clientWithLongTimeout.mockPost(expectedUrl, """{"Id": "abc123"}""", 201) }
                    val result by runForEachTest { api.create(request) }

                    it("creates the container") {
                        verify(call).execute()
                    }

                    it("creates the container with the expected settings") {
                        verify(clientWithLongTimeout).newCall(
                            requestWithJsonBody { body ->
                                assertThat(body, equalTo(Json.default.parseToJsonElement(request.toJson())))
                            }
                        )
                    }

                    it("returns the ID of the created container") {
                        assertThat(result.id, equalTo("abc123"))
                    }

                    it("returns the name of the created container") {
                        assertThat(result.name, equalTo("the-container"))
                    }

                    it("configures the HTTP client with a longer timeout to allow for the container to be created") {
                        verify(longTimeoutClientBuilder).readTimeout(90, TimeUnit.SECONDS)
                    }
                }

                on("a failed creation") {
                    beforeEachTest { clientWithLongTimeout.mockPost(expectedUrl, errorResponse, 418) }

                    it("raises an appropriate exception") {
                        assertThat({ api.create(request) }, throws<ContainerCreationFailedException>(withMessage("Output from Docker was: $errorMessageWithCorrectLineEndings")))
                    }
                }
            }
        }

        describe("starting a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")
                val expectedUrl = "$dockerBaseUrl/v1.37/containers/the-container-id/start"
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

                on("starting that container") {
                    val call by createForEachTest { clientWithLongTimeout.mockPost(expectedUrl, "", 204) }
                    beforeEachTest { api.start(container) }

                    it("sends a request to the Docker daemon to start the container") {
                        verify(call).execute()
                    }

                    it("configures the HTTP client with a longer timeout") {
                        verify(longTimeoutClientBuilder).readTimeout(60, TimeUnit.SECONDS)
                    }
                }

                on("an unsuccessful start attempt") {
                    beforeEachTest { clientWithLongTimeout.mockPost(expectedUrl, errorResponse, 418) }

                    it("raises an appropriate exception") {
                        assertThat({ api.start(container) }, throws<ContainerStartFailedException>(withMessage("Starting container 'the-container-id' failed: $errorMessageWithCorrectLineEndings")))
                    }
                }
            }
        }

        describe("inspecting a container") {
            given("an existing container") {
                val container = DockerContainer("some-container")
                val expectedUrl = "$dockerBaseUrl/v1.37/containers/some-container/json"

                given("the container has previous health check results") {
                    val response = """
                    {
                      "State": {
                        "Health": {
                          "Status": "unhealthy",
                          "FailingStreak": 130,
                          "Log": [
                            {
                              "Start": "2017-10-04T00:54:23.608075352Z",
                              "End": "2017-10-04T00:54:23.646606606Z",
                              "ExitCode": 1,
                              "Output": "something went wrong"
                            }
                          ]
                        }
                      },
                      "Config": {
                        "Healthcheck": {
                          "Test": ["some-test"],
                          "Interval": 20,
                          "Timeout": 30,
                          "StartPeriod": 100,
                          "Retries": 4
                        }
                      }
                    }
                    """.trimIndent()

                    beforeEachTest { httpClient.mockGet(expectedUrl, response, 200) }

                    on("getting the details of that container") {
                        val details by runForEachTest { api.inspect(container) }

                        it("returns the details of the container") {
                            assertThat(
                                details,
                                equalTo(
                                    DockerContainerInfo(
                                        DockerContainerState(
                                            DockerContainerHealthCheckState(
                                                listOf(
                                                    DockerHealthCheckResult(1, "something went wrong")
                                                )
                                            )
                                        ),
                                        DockerContainerConfiguration(
                                            DockerContainerHealthCheckConfig(
                                                listOf("some-test"),
                                                Duration.ofNanos(20),
                                                Duration.ofNanos(30),
                                                Duration.ofNanos(100),
                                                4
                                            )
                                        )
                                    )
                                )
                            )
                        }
                    }
                }

                given("the container does not have a health check") {
                    val response = """
                    {
                      "State": {},
                      "Config": {
                        "Healthcheck": {}
                      }
                    }
                    """.trimIndent()

                    beforeEachTest { httpClient.mockGet(expectedUrl, response, 200) }

                    on("getting the details of that container") {
                        val details by runForEachTest { api.inspect(container) }

                        it("returns the details of the container") {
                            assertThat(
                                details,
                                equalTo(
                                    DockerContainerInfo(
                                        DockerContainerState(health = null),
                                        DockerContainerConfiguration(healthCheck = DockerContainerHealthCheckConfig())
                                    )
                                )
                            )
                        }
                    }
                }

                on("getting the container's details failing") {
                    beforeEachTest { httpClient.mockGet(expectedUrl, errorResponse, 418) }

                    it("throws an appropriate exception") {
                        assertThat(
                            { api.inspect(container) },
                            throws<ContainerInspectionFailedException>(withMessage("Could not inspect container 'some-container': $errorMessageWithCorrectLineEndings"))
                        )
                    }
                }
            }
        }

        describe("stopping a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")
                val expectedUrl = "$dockerBaseUrl/v1.37/containers/the-container-id/stop?timeout=10"
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

                on("stopping that container") {
                    val call by createForEachTest { clientWithLongTimeout.mockPost(expectedUrl, "", 204) }
                    beforeEachTest { api.stop(container) }

                    it("sends a request to the Docker daemon to stop the container") {
                        verify(call).execute()
                    }

                    it("configures the HTTP client with a longer timeout to allow for the specified container stop timeout period") {
                        verify(longTimeoutClientBuilder).readTimeout(20, TimeUnit.SECONDS)
                    }
                }

                on("that container already being stopped") {
                    val call by createForEachTest { clientWithLongTimeout.mockPost(expectedUrl, "", 304) }
                    beforeEachTest { api.stop(container) }

                    it("sends a request to the Docker daemon to stop the container") {
                        verify(call).execute()
                    }
                }

                on("an unsuccessful stop attempt") {
                    beforeEachTest { clientWithLongTimeout.mockPost(expectedUrl, errorResponse, 418) }

                    it("raises an appropriate exception") {
                        assertThat({ api.stop(container) }, throws<ContainerStopFailedException>(withMessage("Stopping container 'the-container-id' failed: $errorMessageWithCorrectLineEndings")))
                    }
                }
            }
        }

        describe("removing a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")
                val expectedUrl = "$dockerBaseUrl/v1.37/containers/the-container-id?v=true&force=true"
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

                on("a successful removal") {
                    val call by createForEachTest { clientWithLongTimeout.mockDelete(expectedUrl, "", 204) }
                    beforeEachTest { api.remove(container) }

                    it("sends a request to the Docker daemon to remove the container") {
                        verify(call).execute()
                    }

                    it("configures the HTTP client with a longer timeout") {
                        verify(longTimeoutClientBuilder).readTimeout(60, TimeUnit.SECONDS)
                    }
                }

                on("an unsuccessful deletion") {
                    beforeEachTest { clientWithLongTimeout.mockDelete(expectedUrl, errorResponse, 418) }

                    it("throws an appropriate exception") {
                        assertThat({ api.remove(container) }, throws<ContainerRemovalFailedException>(withMessage("Removal of container 'the-container-id' failed: $errorMessageWithCorrectLineEndings")))
                    }
                }
            }
        }

        describe("waiting for the next event from a container") {
            given("a Docker container and a list of event types to listen for") {
                val container = DockerContainer("the-container-id")
                val eventTypes = listOf("die", "health_status")
                val cancellationContext by createForEachTest { mock<CancellationContext>() }

                val expectedUrl = hasScheme("http") and
                    hasHost(dockerHost) and
                    hasPath("/v1.37/events") and
                    hasQueryParameter("since", "0") and
                    hasQueryParameter("filters", """{"event":["die","health_status"],"container":["the-container-id"]}""")

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

                on("the Docker daemon returning a single event followed by a new line character") {
                    val responseBody = """
                        |{"status":"health_status: healthy","id":"f09004d33c0892ed74718bd0c1166b28a8d4788bea6449bb6ea8c4d402b20db7","from":"12ff4615e7ff","Type":"container","Action":"health_status: healthy","Actor":{"ID":"f09004d33c0892ed74718bd0c1166b28a8d4788bea6449bb6ea8c4d402b20db7","Attributes":{"image":"12ff4615e7ff","name":"distracted_stonebraker"}},"scope":"local","time":1533986037,"timeNano":1533986037977811448}
                        |
                    """.trimMargin()

                    val call by createForEachTest { clientWithLongTimeout.mock("GET", expectedUrl, responseBody, 200) }
                    val event by runForEachTest { api.waitForNextEvent(container, eventTypes, Duration.ofNanos(123), cancellationContext) }

                    it("returns that event") {
                        assertThat(event, equalTo(DockerEvent("health_status: healthy")))
                    }

                    it("configures the HTTP client with the timeout provided") {
                        verify(longTimeoutClientBuilder).readTimeout(123, TimeUnit.NANOSECONDS)
                    }

                    it("registers the API call with the cancellation context") {
                        verify(cancellationContext).addCancellationCallback(call::cancel)
                    }
                }

                on("the Docker daemon returning a single event but followed by a new line character") {
                    val responseBody = """
                        |{"status":"health_status: healthy","id":"f09004d33c0892ed74718bd0c1166b28a8d4788bea6449bb6ea8c4d402b20db7","from":"12ff4615e7ff","Type":"container","Action":"health_status: healthy","Actor":{"ID":"f09004d33c0892ed74718bd0c1166b28a8d4788bea6449bb6ea8c4d402b20db7","Attributes":{"image":"12ff4615e7ff","name":"distracted_stonebraker"}},"scope":"local","time":1533986037,"timeNano":1533986037977811448}
                    """.trimMargin()

                    val call by createForEachTest { clientWithLongTimeout.mock("GET", expectedUrl, responseBody, 200) }
                    val event by runForEachTest { api.waitForNextEvent(container, eventTypes, Duration.ofNanos(123), cancellationContext) }

                    it("returns that event") {
                        assertThat(event, equalTo(DockerEvent("health_status: healthy")))
                    }

                    it("configures the HTTP client with the timeout provided") {
                        verify(longTimeoutClientBuilder).readTimeout(123, TimeUnit.NANOSECONDS)
                    }

                    it("registers the API call with the cancellation context") {
                        verify(cancellationContext).addCancellationCallback(call::cancel)
                    }
                }

                on("the Docker daemon returning multiple events") {
                    val responseBody = """
                        |{"status":"die","id":"4c44dd62529c1037111ea460fc445f8c7acaa92832b2f991a13052ebac6b50d0","from":"alpine:3.7","Type":"container","Action":"die","Actor":{"ID":"4c44dd62529c1037111ea460fc445f8c7acaa92832b2f991a13052ebac6b50d0","Attributes":{"exitCode":"0","image":"alpine:3.7","name":"nostalgic_lovelace"}},"scope":"local","time":1533986035,"timeNano":1533986035779321663}
                        |{"status":"health_status: healthy","id":"f09004d33c0892ed74718bd0c1166b28a8d4788bea6449bb6ea8c4d402b20db7","from":"12ff4615e7ff","Type":"container","Action":"health_status: healthy","Actor":{"ID":"f09004d33c0892ed74718bd0c1166b28a8d4788bea6449bb6ea8c4d402b20db7","Attributes":{"image":"12ff4615e7ff","name":"distracted_stonebraker"}},"scope":"local","time":1533986037,"timeNano":1533986037977811448}
                        |
                    """.trimMargin()

                    beforeEachTest { clientWithLongTimeout.mock("GET", expectedUrl, responseBody, 200) }

                    val event by runForEachTest { api.waitForNextEvent(container, eventTypes, Duration.ofNanos(123), cancellationContext) }

                    it("returns the first event") {
                        assertThat(event, equalTo(DockerEvent("die")))
                    }
                }

                on("the API call failing") {
                    beforeEachTest { clientWithLongTimeout.mock("GET", expectedUrl, errorResponse, 418) }

                    it("throws an appropriate exception") {
                        assertThat({ api.waitForNextEvent(container, eventTypes, Duration.ofNanos(123), cancellationContext) }, throws<DockerException>(withMessage("Getting events for container 'the-container-id' failed: $errorMessageWithCorrectLineEndings")))
                    }
                }
            }
        }

        describe("waiting for a container to exit") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")
                val cancellationContext by createForEachTest { mock<CancellationContext>() }

                val expectedUrl = "$dockerBaseUrl/v1.37/containers/the-container-id/wait?condition=next-exit"

                val clientWithNoTimeout by createForEachTest { mock<OkHttpClient>() }
                val noTimeoutClientBuilder by createForEachTest {
                    mock<OkHttpClient.Builder> { mock ->
                        on { readTimeout(any(), any()) } doReturn mock
                        on { build() } doReturn clientWithNoTimeout
                    }
                }

                beforeEachTest {
                    whenever(httpClient.newBuilder()).doReturn(noTimeoutClientBuilder)
                }

                given("the wait succeeds") {
                    on("the response not containing any error information") {
                        val responseBody = """{"StatusCode": 123}"""

                        val call by createForEachTest { clientWithNoTimeout.mockPost(expectedUrl, responseBody, 200) }
                        val exitCode by runForEachTest { api.waitForExit(container, cancellationContext) }

                        it("returns the exit code from the container") {
                            assertThat(exitCode, equalTo(123))
                        }

                        it("configures the HTTP client with no timeout") {
                            verify(noTimeoutClientBuilder).readTimeout(eq(0), any())
                        }

                        it("registers the API call with the cancellation context") {
                            verify(cancellationContext).addCancellationCallback(call::cancel)
                        }
                    }

                    on("the response containing an empty error") {
                        val responseBody = """{"StatusCode": 123, "Error": null}"""

                        val call by createForEachTest { clientWithNoTimeout.mockPost(expectedUrl, responseBody, 200) }
                        val exitCode by runForEachTest { api.waitForExit(container, cancellationContext) }

                        it("returns the exit code from the container") {
                            assertThat(exitCode, equalTo(123))
                        }

                        it("configures the HTTP client with no timeout") {
                            verify(noTimeoutClientBuilder).readTimeout(eq(0), any())
                        }

                        it("registers the API call with the cancellation context") {
                            verify(cancellationContext).addCancellationCallback(call::cancel)
                        }
                    }

                    on("the response containing a status code outside the 0-255 range") {
                        val responseBody = """{"StatusCode": 3221225794}"""

                        val call by createForEachTest { clientWithNoTimeout.mockPost(expectedUrl, responseBody, 200) }
                        val exitCode by runForEachTest { api.waitForExit(container, cancellationContext) }

                        it("returns the exit code from the container") {
                            assertThat(exitCode, equalTo(3221225794))
                        }

                        it("configures the HTTP client with no timeout") {
                            verify(noTimeoutClientBuilder).readTimeout(eq(0), any())
                        }

                        it("registers the API call with the cancellation context") {
                            verify(cancellationContext).addCancellationCallback(call::cancel)
                        }
                    }
                }

                on("the wait returning an error in the body of the response") {
                    val responseBody = """{"StatusCode": 123, "Error": {"Message": "Something might have gone wrong."}}"""

                    beforeEachTest { clientWithNoTimeout.mockPost(expectedUrl, responseBody, 200) }

                    it("throws an appropriate exception") {
                        assertThat({ api.waitForExit(container, cancellationContext) }, throws<DockerException>(withMessage("Waiting for container 'the-container-id' to exit succeeded but returned an error: Something might have gone wrong.")))
                    }
                }

                on("the API call failing") {
                    beforeEachTest { clientWithNoTimeout.mockPost(expectedUrl, errorResponse, 418) }

                    it("throws an appropriate exception") {
                        assertThat({ api.waitForExit(container, cancellationContext) }, throws<DockerException>(withMessage("Waiting for container 'the-container-id' to exit failed: $errorMessageWithCorrectLineEndings")))
                    }
                }
            }
        }

        describe("attaching to a container") {
            val source by createForEachTest { mock<BufferedSource>() }
            val sink by createForEachTest { mock<BufferedSink>() }
            val attachHttpClient by createForEachTest { mock<OkHttpClient>() }

            val clientBuilder by createForEachTest {
                mock<OkHttpClient.Builder> { mock ->
                    on { readTimeout(any(), any()) } doReturn mock
                    on { connectionPool(any()) } doReturn mock
                    on { addNetworkInterceptor(hijacker) } doAnswer {
                        whenever(hijacker.source).doReturn(source)
                        whenever(hijacker.sink).doReturn(sink)

                        mock
                    }
                    on { build() } doReturn attachHttpClient
                }
            }

            beforeEachTest {
                whenever(httpClient.newBuilder()).doReturn(clientBuilder)
            }

            given("a Docker container") {
                val container = DockerContainer("the-container-id")
                val expectedHeaders = Headers.Builder()
                    .add("Connection", "Upgrade")
                    .add("Upgrade", "tcp")
                    .build()

                describe("attaching to output") {
                    val expectedUrl = "$dockerBaseUrl/v1.37/containers/the-container-id/attach?logs=true&stream=true&stdout=true&stderr=true"

                    given("attaching succeeds") {
                        val response = mock<Response> {
                            on { code } doReturn 101
                        }

                        val call by createForEachTest { attachHttpClient.mock("POST", expectedUrl, response) }

                        given("a TTY is being used") {
                            val streams by runForEachTest { api.attachToOutput(container, isTTY = true) }

                            it("returns the stream from the underlying connection") {
                                assertThat(streams, equalTo(ContainerOutputStream(response, source)))
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

                            it("sends headers to instruct the daemon to switch to raw sockets") {
                                assertThat(call.request().headers, equalTo(expectedHeaders))
                            }
                        }

                        given("a TTY is not being used") {
                            val streams by runForEachTest { api.attachToOutput(container, isTTY = false) }

                            it("returns the stream from the underlying connection") {
                                assertThat(streams, equalTo(ContainerOutputStream(response, ContainerOutputDecoder(source))))
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

                            it("sends headers to instruct the daemon to switch to raw sockets") {
                                assertThat(call.request().headers, equalTo(expectedHeaders))
                            }
                        }
                    }

                    on("an unsuccessful attach attempt") {
                        beforeEachTest { attachHttpClient.mockPost(expectedUrl, errorResponse, 418) }

                        it("raises an appropriate exception") {
                            assertThat({ api.attachToOutput(container, true) }, throws<DockerException>(withMessage("Attaching to output from container 'the-container-id' failed: $errorMessageWithCorrectLineEndings")))
                        }
                    }
                }

                describe("attaching to stdin") {
                    val expectedUrl = "$dockerBaseUrl/v1.37/containers/the-container-id/attach?logs=true&stream=true&stdin=true"

                    on("the attach succeeding") {
                        val response = mock<Response> {
                            on { code } doReturn 101
                        }

                        val call by createForEachTest { attachHttpClient.mock("POST", expectedUrl, response) }

                        val streams by runForEachTest { api.attachToInput(container) }

                        it("returns the stream from the underlying connection") {
                            assertThat(streams, equalTo(ContainerInputStream(response, sink)))
                        }

                        it("does not close the underlying connection") {
                            verify(response, never()).close()
                        }

                        it("configures the HTTP client with no timeout") {
                            verify(clientBuilder).readTimeout(eq(0), any())
                        }

                        it("configures the HTTP client with a separate connection pool that does not evict connections (because the underlying connection cannot be reused and because we don't want to evict the connection just because there hasn't been any input for a while)") {
                            verify(clientBuilder).connectionPool(connectionPoolWithNoEviction())
                        }

                        it("sends headers to instruct the daemon to switch to raw sockets") {
                            assertThat(call.request().headers, equalTo(expectedHeaders))
                        }
                    }

                    on("an unsuccessful attach attempt") {
                        beforeEachTest { attachHttpClient.mockPost(expectedUrl, errorResponse, 418) }

                        it("raises an appropriate exception") {
                            assertThat({ api.attachToInput(container) }, throws<DockerException>(withMessage("Attaching to input for container 'the-container-id' failed: $errorMessageWithCorrectLineEndings")))
                        }
                    }
                }
            }
        }

        describe("sending a signal to a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")
                val signal = Signal.SIGINT
                val expectedUrl = "$dockerBaseUrl/v1.37/containers/the-container-id/kill?signal=SIGINT"

                on("the API call succeeding") {
                    val call by createForEachTest { httpClient.mockPost(expectedUrl, "", 204) }
                    beforeEachTest { api.sendSignal(container, signal) }

                    it("sends a request to the Docker daemon to send the signal to the container") {
                        verify(call).execute()
                    }
                }

                on("the API call failing") {
                    beforeEachTest { httpClient.mockPost(expectedUrl, errorResponse, 418) }

                    it("throws an appropriate exception") {
                        assertThat({ api.sendSignal(container, signal) }, throws<DockerException>(withMessage("Sending signal SIGINT to container 'the-container-id' failed: $errorMessageWithCorrectLineEndings")))
                    }
                }
            }
        }

        describe("resizing a container TTY") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")
                val dimensions = Dimensions(123, 456)
                val expectedUrl = "$dockerBaseUrl/v1.37/containers/the-container-id/resize?h=123&w=456"

                on("the API call succeeding") {
                    val call by createForEachTest { httpClient.mockPost(expectedUrl, "", 200) }
                    beforeEachTest { api.resizeTTY(container, dimensions) }

                    it("sends a request to the Docker daemon to resize the TTY") {
                        verify(call).execute()
                    }
                }

                on("the container being stopped on an older version of the Docker daemon") {
                    beforeEachTest { httpClient.mockPost(expectedUrl, """{"message": "cannot resize a stopped container: unknown"}""", 418) }

                    it("throws an appropriate exception") {
                        assertThat({ api.resizeTTY(container, dimensions) }, throws<ContainerStoppedException>(withMessage("Resizing TTY for container 'the-container-id' failed: cannot resize a stopped container: unknown")))
                    }
                }

                on("the container being stopped on a newer version of the Docker daemon") {
                    beforeEachTest { httpClient.mockPost(expectedUrl, """{"message": "Container 4a83bfdf8bc3c6f2c60feb4880ea2319d58c6eeb558a21119f7493d7b95d215b is not running"}""", 418) }

                    it("throws an appropriate exception") {
                        assertThat({ api.resizeTTY(container, dimensions) }, throws<ContainerStoppedException>(withMessage("Resizing TTY for container 'the-container-id' failed: Container 4a83bfdf8bc3c6f2c60feb4880ea2319d58c6eeb558a21119f7493d7b95d215b is not running")))
                    }
                }

                on("the container being stopped and the daemon returning a 'bad file descriptor' error") {
                    beforeEachTest { httpClient.mockPost(expectedUrl, """{"message": "bad file descriptor: unknown"}""", 500) }

                    it("throws an appropriate exception") {
                        assertThat({ api.resizeTTY(container, dimensions) }, throws<ContainerStoppedException>(withMessage("Resizing TTY for container 'the-container-id' failed: bad file descriptor: unknown (the container may have stopped quickly after starting)")))
                    }
                }

                on("the container being stopped when running a Windows container") {
                    beforeEachTest { httpClient.mockPost(expectedUrl, """{"message": "process 2676 in container e766a7947b2e03a5bc02103a9a88b8c1ab914c9ed488205134f3fcc98e77a877 encountered an error during hcsshim::Process::ResizeConsole: hcsshim: the handle has already been closed"}""", 500) }

                    it("throws an appropriate exception") {
                        assertThat({ api.resizeTTY(container, dimensions) }, throws<ContainerStoppedException>(withMessage("Resizing TTY for container 'the-container-id' failed: process 2676 in container e766a7947b2e03a5bc02103a9a88b8c1ab914c9ed488205134f3fcc98e77a877 encountered an error during hcsshim::Process::ResizeConsole: hcsshim: the handle has already been closed (the container may have stopped quickly after starting)")))
                    }
                }

                on("the API call failing") {
                    beforeEachTest { httpClient.mockPost(expectedUrl, errorResponse, 418) }

                    it("throws an appropriate exception") {
                        assertThat({ api.resizeTTY(container, dimensions) }, throws<DockerException>(withMessage("Resizing TTY for container 'the-container-id' failed: $errorMessageWithCorrectLineEndings")))
                    }
                }
            }
        }

        describe("uploading files or folders to a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")

                val expectedUrl = hasScheme("http") and
                    hasHost(dockerHost) and
                    hasPath("/v1.37/containers/the-container-id/archive")

                val itemsToUpload = setOf(
                    ContainerFile("file-1", 100, 200, "file contents".toByteArray(Charsets.UTF_8)),
                    ContainerDirectory("some-dir", 100, 200)
                )

                given("the API call succeeds") {
                    val call by createForEachTest { httpClient.mockPut(expectedUrl, "", 200) }

                    beforeEachTest { api.upload(container, itemsToUpload, "/some-dir") }

                    it("sends a request to the Docker daemon to upload the files and folders") {
                        verify(call).execute()
                    }

                    it("includes the target directory in the request") {
                        assertThat(call.request().url, hasQueryParameter("path", "/some-dir"))
                    }

                    it("instructs the Docker daemon to copy UID and GID information from the uploaded files and folders") {
                        assertThat(call.request().url, hasQueryParameter("copyUIDGID", "1"))
                    }

                    it("includes the files and folders in the request body") {
                        assertThat(call.request().body, equalTo(FilesystemUploadRequestBody(itemsToUpload)))
                    }
                }

                given("the API call fails") {
                    beforeEachTest { httpClient.mockPut(expectedUrl, errorResponse, 418) }

                    it("throws an appropriate exception") {
                        assertThat({ api.upload(container, itemsToUpload, "/some-dir") }, throws<DockerException>(withMessage("Uploading 2 items to container 'the-container-id' failed: $errorMessageWithCorrectLineEndings")))
                    }
                }
            }
        }
    }
})
