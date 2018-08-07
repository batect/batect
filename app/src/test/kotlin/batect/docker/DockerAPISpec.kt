/*
   Copyright 2017-2018 Charles Korn.

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

package batect.docker

import batect.config.HealthCheckConfig
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.mockDelete
import batect.testutils.mockGet
import batect.testutils.mockPost
import batect.testutils.withMessage
import batect.utils.Version
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTreeParser
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.content
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.util.UUID
import java.util.concurrent.TimeUnit

object DockerAPISpec : Spek({
    describe("a Docker API client") {
        val dockerBaseUrl = "http://the-docker-daemon"
        val httpClient by createForEachTest { mock<OkHttpClient>() }
        val httpConfig by createForEachTest {
            mock<DockerHttpConfig> {
                on { client } doReturn httpClient
                on { baseUrl } doReturn HttpUrl.get(dockerBaseUrl)
            }
        }

        val logger by createLoggerForEachTest()
        val api by createForEachTest { DockerAPI(httpConfig, logger) }

        describe("creating a container") {
            val expectedUrl = "$dockerBaseUrl/v1.12/containers/create"

            given("a container configuration and a built image") {
                val image = DockerImage("the-image")
                val network = DockerNetwork("the-network")
                val command = listOf("doStuff")
                val request = DockerContainerCreationRequest(image, network, command, "some-host", "some-host", emptyMap(), "/some-dir", emptySet(), emptySet(), HealthCheckConfig(), null)

                on("a successful creation") {
                    val call = httpClient.mockPost(expectedUrl, """{"Id": "abc123"}""", 201)
                    val result = api.createContainer(request)

                    it("creates the container") {
                        verify(call).execute()
                    }

                    it("creates the container with the expected settings") {
                        verify(httpClient).newCall(requestWithJsonBody { body ->
                            assertThat(body, equalTo(JsonTreeParser(request.toJson()).readFully()))
                        })
                    }

                    it("returns the ID of the created container") {
                        assertThat(result.id, equalTo("abc123"))
                    }
                }

                on("a failed creation") {
                    httpClient.mockPost(expectedUrl, """{"message": "Something went wrong."}""", 418)

                    it("raises an appropriate exception") {
                        assertThat({ api.createContainer(request) }, throws<ContainerCreationFailedException>(withMessage("Output from Docker was: Something went wrong.")))
                    }
                }
            }
        }

        describe("starting a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")
                val expectedUrl = "$dockerBaseUrl/v1.12/containers/the-container-id/start"

                on("starting that container") {
                    val call = httpClient.mockPost(expectedUrl, "", 204)
                    api.startContainer(container)

                    it("sends a request to the Docker daemon to start the container") {
                        verify(call).execute()
                    }
                }

                on("an unsuccessful start attempt") {
                    httpClient.mockPost(expectedUrl, """{"message": "Something went wrong."}""", 418)

                    it("raises an appropriate exception") {
                        assertThat({ api.startContainer(container) }, throws<ContainerStartFailedException>(withMessage("Starting container 'the-container-id' failed: Something went wrong.")))
                    }
                }
            }
        }

        describe("inspecting a container") {
            given("an existing container") {
                val container = DockerContainer("some-container")
                val expectedUrl = "$dockerBaseUrl/v1.12/containers/some-container/json"

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
                          "Test": ["some-test"]
                        }
                      }
                    }""".trimIndent()

                    beforeEachTest { httpClient.mockGet(expectedUrl, response, 200) }

                    on("getting the details of that container") {
                        val details = api.inspectContainer(container)

                        it("returns the details of the container") {
                            assertThat(details, equalTo(DockerContainerInfo(
                                DockerContainerState(
                                    DockerContainerHealthCheckState(listOf(
                                        DockerHealthCheckResult(1, "something went wrong")
                                    ))
                                ),
                                DockerContainerConfiguration(
                                    DockerContainerHealthCheckConfig(listOf("some-test"))
                                )
                            )))
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
                    }""".trimIndent()

                    beforeEachTest { httpClient.mockGet(expectedUrl, response, 200) }

                    on("getting the details of that container") {
                        val details = api.inspectContainer(container)

                        it("returns the details of the container") {
                            assertThat(details, equalTo(DockerContainerInfo(
                                DockerContainerState(health = null),
                                DockerContainerConfiguration(healthCheck = DockerContainerHealthCheckConfig(null))
                            )))
                        }
                    }
                }

                on("getting the container's details failing") {
                    httpClient.mockGet(expectedUrl, """{"message": "Something went wrong."}""", 418)

                    it("throws an appropriate exception") {
                        assertThat({ api.inspectContainer(container) },
                            throws<ContainerInspectionFailedException>(withMessage("Could not inspect container 'some-container': Something went wrong.")))
                    }
                }
            }
        }

        describe("stopping a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")
                val expectedUrl = "$dockerBaseUrl/v1.12/containers/the-container-id/stop"
                val clientWithLongTimeout = mock<OkHttpClient>()
                val longTimeoutClientBuilder = mock<OkHttpClient.Builder> { mock ->
                    on { readTimeout(any(), any()) } doReturn mock
                    on { build() } doReturn clientWithLongTimeout
                }

                beforeEachTest {
                    whenever(httpClient.newBuilder()).doReturn(longTimeoutClientBuilder)
                }

                on("stopping that container") {
                    val call = clientWithLongTimeout.mockPost(expectedUrl, "", 204)
                    api.stopContainer(container)

                    it("sends a request to the Docker daemon to stop the container") {
                        verify(call).execute()
                    }

                    it("configures the Docker client with a longer timeout to allow for the default container stop timeout period of 10 seconds") {
                        verify(longTimeoutClientBuilder).readTimeout(11, TimeUnit.SECONDS)
                    }
                }

                on("an unsuccessful stop attempt") {
                    clientWithLongTimeout.mockPost(expectedUrl, """{"message": "Something went wrong."}""", 418)

                    it("raises an appropriate exception") {
                        assertThat({ api.stopContainer(container) }, throws<ContainerStopFailedException>(withMessage("Stopping container 'the-container-id' failed: Something went wrong.")))
                    }
                }
            }
        }

        describe("removing a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")
                val expectedUrl = "$dockerBaseUrl/v1.12/containers/the-container-id?v=true"

                on("a successful removal") {
                    val call = httpClient.mockDelete(expectedUrl, "", 204)
                    api.removeContainer(container)

                    it("sends a request to the Docker daemon to remove the container") {
                        verify(call).execute()
                    }
                }

                on("an unsuccessful deletion") {
                    httpClient.mockDelete(expectedUrl, """{"message": "Something went wrong."}""", 418)

                    it("throws an appropriate exception") {
                        assertThat({ api.removeContainer(container) }, throws<ContainerRemovalFailedException>(withMessage("Removal of container 'the-container-id' failed: Something went wrong.")))
                    }
                }
            }
        }

        describe("creating a network") {
            val expectedUrl = "$dockerBaseUrl/v1.12/networks/create"

            on("a successful creation") {
                val call = httpClient.mockPost(expectedUrl, """{"Id": "the-network-ID"}""", 201)
                val result = api.createNetwork()

                it("creates the network") {
                    verify(call).execute()
                }

                it("creates the network with the expected settings") {
                    verify(httpClient).newCall(requestWithJsonBody { body ->
                        assertThat(body["Name"].content, isUUID)
                        assertThat(body["CheckDuplicate"].boolean, equalTo(true))
                        assertThat(body["Driver"].content, equalTo("bridge"))
                    })
                }

                it("returns the ID of the created network") {
                    assertThat(result.id, equalTo("the-network-ID"))
                }
            }

            on("an unsuccessful creation") {
                httpClient.mockPost(expectedUrl, """{"message": "Something went wrong."}""", 418)

                it("throws an appropriate exception") {
                    assertThat({ api.createNetwork() }, throws<NetworkCreationFailedException>(withMessage("Creation of network failed: Something went wrong.")))
                }
            }
        }

        describe("deleting a network") {
            given("an existing network") {
                val network = DockerNetwork("abc123")
                val expectedUrl = "$dockerBaseUrl/v1.12/networks/abc123"

                on("a successful deletion") {
                    val call = httpClient.mockDelete(expectedUrl, "", 204)
                    api.deleteNetwork(network)

                    it("sends a request to the Docker daemon to delete the network") {
                        verify(call).execute()
                    }
                }

                on("an unsuccessful deletion") {
                    httpClient.mockDelete(expectedUrl, """{"message": "Something went wrong."}""", 418)

                    it("throws an appropriate exception") {
                        assertThat({ api.deleteNetwork(network) }, throws<NetworkDeletionFailedException>(withMessage("Deletion of network 'abc123' failed: Something went wrong.")))
                    }
                }
            }
        }

        describe("getting server version information") {
            val expectedUrl = "$dockerBaseUrl/v1.12/version"

            on("the Docker version command invocation succeeding") {
                httpClient.mockGet(expectedUrl, """
                    |{
                    |  "Version": "17.04.0",
                    |  "Os": "linux",
                    |  "KernelVersion": "3.19.0-23-generic",
                    |  "GoVersion": "go1.7.5",
                    |  "GitCommit": "deadbee",
                    |  "Arch": "amd64",
                    |  "ApiVersion": "1.27",
                    |  "MinAPIVersion": "1.12",
                    |  "BuildTime": "2016-06-14T07:09:13.444803460+00:00",
                    |  "Experimental": true
                    |}""".trimMargin(), 200)

                it("returns the version information from Docker") {
                    assertThat(api.getServerVersionInfo(), equalTo(DockerVersionInfo(
                        Version(17, 4, 0), "1.27", "1.12", "deadbee"
                    )))
                }
            }

            on("the Docker version command invocation failing") {
                httpClient.mockGet(expectedUrl, """{"message": "Something went wrong."}""", 418)

                it("throws an appropriate exception") {
                    assertThat({ api.getServerVersionInfo() }, throws<DockerVersionInfoRetrievalException>(withMessage("The request failed: Something went wrong.")))
                }
            }
        }
    }
})

private val isUUID = Matcher(::validUUID)
private fun validUUID(value: String): Boolean {
    try {
        UUID.fromString(value)
        return true
    } catch (_: IllegalArgumentException) {
        return false
    }
}

private fun requestWithJsonBody(predicate: (JsonObject) -> Unit) = check<Request> { request ->
    assertThat(request.body()!!.contentType(), equalTo(MediaType.get("application/json; charset=utf-8")))

    val buffer = Buffer()
    request.body()!!.writeTo(buffer)
    val parsedBody = JsonTreeParser(buffer.readUtf8()).readFully() as JsonObject
    predicate(parsedBody)
}
