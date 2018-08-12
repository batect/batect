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
import batect.docker.build.DockerImageBuildContext
import batect.docker.build.DockerImageBuildContextRequestBody
import batect.docker.pull.DockerRegistryCredentials
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.mock
import batect.testutils.mockDelete
import batect.testutils.mockGet
import batect.testutils.mockPost
import batect.testutils.withMessage
import batect.utils.Version
import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
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
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.Buffer
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

object DockerAPISpec : Spek({
    describe("a Docker API client") {
        val dockerHost = "the-docker-daemon"
        val dockerBaseUrl = "http://$dockerHost"
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
            val expectedUrl = "$dockerBaseUrl/v1.30/containers/create"

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
                val expectedUrl = "$dockerBaseUrl/v1.30/containers/the-container-id/start"

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
                val expectedUrl = "$dockerBaseUrl/v1.30/containers/some-container/json"

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
                                    DockerContainerHealthCheckConfig(
                                        listOf("some-test"),
                                        Duration.ofNanos(20),
                                        Duration.ofNanos(30),
                                        Duration.ofNanos(100),
                                        4
                                    )
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
                                DockerContainerConfiguration(healthCheck = DockerContainerHealthCheckConfig())
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
                val expectedUrl = "$dockerBaseUrl/v1.30/containers/the-container-id/stop"
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
                val expectedUrl = "$dockerBaseUrl/v1.30/containers/the-container-id?v=true"

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

        describe("waiting for the next event from a container") {
            given("a Docker container and a list of event types to listen for") {
                val container = DockerContainer("the-container-id")
                val eventTypes = listOf("die", "health_status")
                val expectedUrl = hasScheme("http") and
                    hasHost(dockerHost) and
                    hasPath("/v1.30/events") and
                    hasQueryParameter("since", "0") and
                    hasQueryParameter("filters", """{"event": ["die", "health_status"], "container": ["the-container-id"]}""")

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

                on("the Docker daemon returning a single event") {
                    val responseBody = """
                        |{"status":"health_status: healthy","id":"f09004d33c0892ed74718bd0c1166b28a8d4788bea6449bb6ea8c4d402b20db7","from":"12ff4615e7ff","Type":"container","Action":"health_status: healthy","Actor":{"ID":"f09004d33c0892ed74718bd0c1166b28a8d4788bea6449bb6ea8c4d402b20db7","Attributes":{"image":"12ff4615e7ff","name":"distracted_stonebraker"}},"scope":"local","time":1533986037,"timeNano":1533986037977811448}
                        |
                    """.trimMargin()

                    clientWithLongTimeout.mock("GET", expectedUrl, responseBody, 200)
                    val event = api.waitForNextEventForContainer(container, eventTypes, Duration.ofNanos(123))

                    it("returns that event") {
                        assertThat(event, equalTo(DockerEvent("health_status: healthy")))
                    }

                    it("configures the Docker client with the timeout provided") {
                        verify(longTimeoutClientBuilder).readTimeout(123, TimeUnit.NANOSECONDS)
                    }
                }

                on("the Docker daemon returning multiple events") {
                    val responseBody = """
                        |{"status":"die","id":"4c44dd62529c1037111ea460fc445f8c7acaa92832b2f991a13052ebac6b50d0","from":"alpine:3.7","Type":"container","Action":"die","Actor":{"ID":"4c44dd62529c1037111ea460fc445f8c7acaa92832b2f991a13052ebac6b50d0","Attributes":{"exitCode":"0","image":"alpine:3.7","name":"nostalgic_lovelace"}},"scope":"local","time":1533986035,"timeNano":1533986035779321663}
                        |{"status":"health_status: healthy","id":"f09004d33c0892ed74718bd0c1166b28a8d4788bea6449bb6ea8c4d402b20db7","from":"12ff4615e7ff","Type":"container","Action":"health_status: healthy","Actor":{"ID":"f09004d33c0892ed74718bd0c1166b28a8d4788bea6449bb6ea8c4d402b20db7","Attributes":{"image":"12ff4615e7ff","name":"distracted_stonebraker"}},"scope":"local","time":1533986037,"timeNano":1533986037977811448}
                        |
                    """.trimMargin()

                    clientWithLongTimeout.mock("GET", expectedUrl, responseBody, 200)
                    val event = api.waitForNextEventForContainer(container, eventTypes, Duration.ofNanos(123))

                    it("returns the first event") {
                        assertThat(event, equalTo(DockerEvent("die")))
                    }
                }

                on("the API call failing") {
                    clientWithLongTimeout.mock("GET", expectedUrl, """{"message": "Something went wrong."}""", 418)

                    it("throws an appropriate exception") {
                        assertThat({ api.waitForNextEventForContainer(container, eventTypes, Duration.ofNanos(123)) }, throws<DockerException>(withMessage("Getting events for container 'the-container-id' failed: Something went wrong.")))
                    }
                }
            }
        }

        describe("creating a network") {
            val expectedUrl = "$dockerBaseUrl/v1.30/networks/create"

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
                val expectedUrl = "$dockerBaseUrl/v1.30/networks/abc123"

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

        describe("building an image") {
            val clientWithLongTimeout = mock<OkHttpClient>()
            val longTimeoutClientBuilder = mock<OkHttpClient.Builder> { mock ->
                on { readTimeout(any(), any()) } doReturn mock
                on { build() } doReturn clientWithLongTimeout
            }

            beforeEachTest {
                whenever(httpClient.newBuilder()).doReturn(longTimeoutClientBuilder)
            }

            val expectedUrl = hasScheme("http") and
                hasHost(dockerHost) and
                hasPath("/v1.30/build") and
                hasQueryParameter("buildargs", """{"someArg": "someValue"}""")

            val context = DockerImageBuildContext(emptySet())
            val buildArgs = mapOf("someArg" to "someValue")
            val registryCredentials = mock<DockerRegistryCredentials> {
                on { toJSON() } doReturn "some json credentials"
            }

            val base64EncodedJSONCredentials = "c29tZSBqc29uIGNyZWRlbnRpYWxz"
            val expectedHeadersForAuthentication = Headers.Builder().set("X-Registry-Auth", base64EncodedJSONCredentials).build()

            val successResponse = """
                |{"stream":"Step 1/5 : FROM nginx:1.13.0"}
                |{"stream":"\n"}
                |{"stream":" ---\u003e 3448f27c273f\n"}
                |{"stream":"Step 2/5 : RUN apt update \u0026\u0026 apt install -y curl \u0026\u0026 rm -rf /var/lib/apt/lists/*"}
                |{"stream":"\n"}
                |{"stream":" ---\u003e Using cache\n"}
                |{"stream":" ---\u003e 0ceae477da9d\n"}
                |{"stream":"Step 3/5 : COPY index.html /usr/share/nginx/html"}
                |{"stream":"\n"}
                |{"stream":" ---\u003e b288a67b828c\n"}
                |{"stream":"Step 4/5 : COPY health-check.sh /tools/"}
                |{"stream":"\n"}
                |{"stream":" ---\u003e 951e32ae4f76\n"}
                |{"stream":"Step 5/5 : HEALTHCHECK --interval=2s --retries=1 CMD /tools/health-check.sh"}
                |{"stream":"\n"}
                |{"stream":" ---\u003e Running in 3de7e4521d69\n"}
                |{"stream":"Removing intermediate container 3de7e4521d69\n"}
                |{"stream":" ---\u003e 24125bbc6cbe\n"}
                |{"aux":{"ID":"sha256:24125bbc6cbe08f530e97c81ee461357fa3ba56f4d7693d7895ec86671cf3540"}}
                |{"stream":"Successfully built 24125bbc6cbe\n"}
            """.trimMargin()

            on("the build succeeding") {
                val call = clientWithLongTimeout.mock("POST", expectedUrl, successResponse, 200, expectedHeadersForAuthentication)
                val progressReceiver = ProgressReceiver()
                val image = api.buildImage(context, buildArgs, registryCredentials, progressReceiver::onProgressUpdate)

                it("sends a request to the Docker daemon to build the image") {
                    verify(call).execute()
                }

                it("configures the HTTP client with no timeout to allow for slow build output") {
                    verify(longTimeoutClientBuilder).readTimeout(0, TimeUnit.MILLISECONDS)
                }

                it("builds the image with the expected context") {
                    verify(clientWithLongTimeout).newCall(requestWithBody(DockerImageBuildContextRequestBody(context)))
                }

                it("sends all progress updates to the receiver") {
                    assertThat(progressReceiver, receivedAllUpdatesFrom(successResponse))
                }

                it("returns the build image") {
                    assertThat(image, equalTo(DockerImage("sha256:24125bbc6cbe08f530e97c81ee461357fa3ba56f4d7693d7895ec86671cf3540")))
                }
            }

            on("the build having no registry credentials") {
                val expectedHeadersForNoAuthentication = Headers.Builder().build()
                val call = clientWithLongTimeout.mock("POST", expectedUrl, successResponse, 200, expectedHeadersForNoAuthentication)
                api.buildImage(context, buildArgs, null, {})

                it("sends a request to the Docker daemon to build the image with no authentication header") {
                    verify(call).execute()
                }
            }

            on("the build having no build args") {
                val expectedUrlWithNoBuildArgs = hasScheme("http") and
                    hasHost(dockerHost) and
                    hasPath("/v1.30/build") and
                    hasQueryParameter("buildargs", """{}""")

                val call = clientWithLongTimeout.mock("POST", expectedUrlWithNoBuildArgs, successResponse, 200, expectedHeadersForAuthentication)
                api.buildImage(context, emptyMap(), registryCredentials, {})

                it("sends a request to the Docker daemon to build the image with an empty set of build args") {
                    verify(call).execute()
                }
            }

            on("the build failing immediately") {
                clientWithLongTimeout.mock("POST", expectedUrl, """{"message": "Something went wrong."}""", 418, expectedHeadersForAuthentication)

                it("throws an appropriate exception") {
                    assertThat({ api.buildImage(context, buildArgs, registryCredentials, {}) }, throws<ImageBuildFailedException>(
                        withMessage("Building image failed: Something went wrong."))
                    )
                }
            }

            on("the build failing during the build process") {
                val errorResponse = """
                    |{"stream":"Step 1/6 : FROM nginx:1.13.0"}
                    |{"stream":"\n"}
                    |{"stream":" ---\u003e 3448f27c273f\n"}
                    |{"stream":"Step 2/6 : RUN exit 1"}
                    |{"stream":"\n"}
                    |{"stream":" ---\u003e Running in 4427f9f56fad\n"}
                    |{"errorDetail":{"code":1,"message":"The command '/bin/sh -c exit 1' returned a non-zero code: 1"},"error":"The command '/bin/sh -c exit 1' returned a non-zero code: 1"}
                """.trimMargin()

                clientWithLongTimeout.mock("POST", expectedUrl, errorResponse, 200, expectedHeadersForAuthentication)

                it("throws an appropriate exception") {
                    assertThat({ api.buildImage(context, buildArgs, registryCredentials, {}) }, throws<ImageBuildFailedException>(
                        withMessage(
                            "Building image failed: The command '/bin/sh -c exit 1' returned a non-zero code: 1. Output from build process was:\n" +
                                "Step 1/6 : FROM nginx:1.13.0\n" +
                                " ---> 3448f27c273f\n" +
                                "Step 2/6 : RUN exit 1\n" +
                                " ---> Running in 4427f9f56fad\n" +
                                "The command '/bin/sh -c exit 1' returned a non-zero code: 1"
                        ))
                    )
                }
            }

            on("the build process never sending an output line with the built image ID") {
                val malformedResponse = """
                    |{"stream":"Step 1/6 : FROM nginx:1.13.0"}
                """.trimMargin()

                clientWithLongTimeout.mock("POST", expectedUrl, malformedResponse, 200, expectedHeadersForAuthentication)

                it("throws an appropriate exception") {
                    assertThat({ api.buildImage(context, buildArgs, registryCredentials, {}) }, throws<ImageBuildFailedException>(
                        withMessage("Building image failed: daemon never sent built image ID."))
                    )
                }
            }
        }

        describe("pulling an image") {
            val imageName = "some-image"
            val expectedUrl = "$dockerBaseUrl/v1.30/images/create?fromImage=some-image"
            val registryCredentials = mock<DockerRegistryCredentials> {
                on { toJSON() } doReturn "some json credentials"
            }

            val base64EncodedJSONCredentials = "c29tZSBqc29uIGNyZWRlbnRpYWxz"
            val expectedHeadersForAuthentication = Headers.Builder().set("X-Registry-Auth", base64EncodedJSONCredentials).build()

            on("the pull succeeding because the image is already present") {
                val response = """
                    |{"status":"Pulling from library/some-image","id":"latest"}
                    |{"status":"Status: Image is up to date for some-image"}
                """.trimMargin()

                val call = httpClient.mockPost(expectedUrl, response, 200, expectedHeadersForAuthentication)
                val progressReceiver = ProgressReceiver()
                api.pullImage(imageName, registryCredentials, progressReceiver::onProgressUpdate)

                it("sends a request to the Docker daemon to pull the image") {
                    verify(call).execute()
                }

                it("sends all progress updates to the receiver") {
                    assertThat(progressReceiver, receivedAllUpdatesFrom(response))
                }
            }

            on("the pull succeeding because the image was pulled") {
                val response = """
                    |{"status":"Pulling from library/some-image","id":"latest"}
                    |{"status":"Pulling fs layer","progressDetail":{},"id":"d660b1f15b9b"}
                    |{"status":"Pulling fs layer","progressDetail":{},"id":"46dde23c37b3"}
                    |{"status":"Pulling fs layer","progressDetail":{},"id":"6ebaeb074589"}
                    |{"status":"Pulling fs layer","progressDetail":{},"id":"e7428f935583"}
                    |{"status":"Status: Downloaded newer image for some-image:latest"}
                """.trimMargin()

                val call = httpClient.mockPost(expectedUrl, response, 200, expectedHeadersForAuthentication)
                val progressReceiver = ProgressReceiver()
                api.pullImage(imageName, registryCredentials, progressReceiver::onProgressUpdate)

                it("sends a request to the Docker daemon to pull the image") {
                    verify(call).execute()
                }

                it("sends all progress updates to the receiver") {
                    assertThat(progressReceiver, receivedAllUpdatesFrom(response))
                }
            }

            on("the pull request having no registry credentials") {
                val expectedHeadersForNoAuthentication = Headers.Builder().build()
                val call = httpClient.mockPost(expectedUrl, "", 200, expectedHeadersForNoAuthentication)
                api.pullImage(imageName, null, {})

                it("sends a request to the Docker daemon to pull the image with no authentication header") {
                    verify(call).execute()
                }
            }

            on("the pull failing immediately") {
                httpClient.mockPost(expectedUrl, """{"message": "Something went wrong."}""", 418, expectedHeadersForAuthentication)

                it("throws an appropriate exception") {
                    assertThat({ api.pullImage(imageName, registryCredentials, {}) }, throws<ImagePullFailedException>(
                        withMessage("Pulling image 'some-image' failed: Something went wrong."))
                    )
                }
            }

            on("the pull failing part-way through the process") {
                val response = """
                    |{"status":"Pulling from library/some-image","id":"latest"}
                    |{"status":"Pulling fs layer","progressDetail":{},"id":"d660b1f15b9b"}
                    |{"status":"Pulling fs layer","progressDetail":{},"id":"46dde23c37b3"}
                    |{"status":"Pulling fs layer","progressDetail":{},"id":"6ebaeb074589"}
                    |{"status":"Pulling fs layer","progressDetail":{},"id":"e7428f935583"}
                    |{"error":"Server error: 404 trying to fetch remote history for some-image","errorDetail":{"code":404,"message":"Server error: 404 trying to fetch remote history for some-image"}}
                """.trimMargin()

                httpClient.mockPost(expectedUrl, response, 200, expectedHeadersForAuthentication)
                val progressReceiver = ProgressReceiver()

                it("throws an appropriate exception") {
                    assertThat({ api.pullImage(imageName, registryCredentials, progressReceiver::onProgressUpdate) }, throws<ImagePullFailedException>(
                        withMessage("Pulling image 'some-image' failed: Server error: 404 trying to fetch remote history for some-image"))
                    )
                }

                it("sends all progress updates to the receiver except for the error") {
                    assertThat(progressReceiver, receivedAllUpdatesFrom(response.lines().dropLast(1)))
                }
            }
        }

        describe("checking if an image has been pulled") {
            val imageName = "some:image"
            val expectedUrl = hasScheme("http") and
                hasHost(dockerHost) and
                hasPath("/v1.30/images/json") and
                hasQueryParameter("filters", """{"reference": ["some:image"]}""")

            on("the image already having been pulled") {
                httpClient.mock("GET", expectedUrl, """[{"Id": "abc123"}]""")
                val hasImage = api.hasImage(imageName)

                it("returns true") {
                    assertThat(hasImage, equalTo(true))
                }
            }

            on("the image not having been pulled") {
                httpClient.mock("GET", expectedUrl, """[]""")
                val hasImage = api.hasImage(imageName)

                it("returns false") {
                    assertThat(hasImage, equalTo(false))
                }
            }

            on("the HTTP call failing") {
                httpClient.mock("GET", expectedUrl, """{"message": "Something went wrong."}""", 418)

                it("throws an appropriate exception") {
                    assertThat({ api.hasImage(imageName) }, throws<ImagePullFailedException>(withMessage("Checking if image 'some:image' has already been pulled failed: Something went wrong.")))
                }
            }
        }

        describe("getting server version information") {
            val expectedUrl = "$dockerBaseUrl/v1.30/version"

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

        describe("pinging the server") {
            val expectedUrl = "$dockerBaseUrl/_ping"

            on("the ping succeeding") {
                httpClient.mockGet(expectedUrl, "OK")

                it("does not throw an exception") {
                    assertThat({ api.ping() }, !throws<Throwable>())
                }
            }

            on("the ping failing") {
                httpClient.mockGet(expectedUrl, """{"message": "Something went wrong."}""", 418)

                it("throws an appropriate exception") {
                    assertThat({ api.ping() }, throws<DockerException>(withMessage("Could not ping Docker daemon, daemon responded with HTTP 418: Something went wrong.")))
                }
            }

            on("the ping returning an unexpected response") {
                httpClient.mockGet(expectedUrl, "Something went wrong.", 200)

                it("throws an appropriate exception") {
                    assertThat({ api.ping() }, throws<DockerException>(withMessage("Could not ping Docker daemon, daemon responded with HTTP 200: Something went wrong.")))
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

private fun hasScheme(expectedScheme: String) = has(HttpUrl::scheme, equalTo(expectedScheme))
private fun hasHost(expectedHost: String) = has(HttpUrl::host, equalTo(expectedHost))
private fun hasPath(expectedPath: String) = has(HttpUrl::encodedPath, equalTo(expectedPath))

private fun hasQueryParameter(key: String, expectedValue: String) = object : Matcher<HttpUrl> {
    override fun invoke(actual: HttpUrl): MatchResult {
        val actualParameterValue = actual.queryParameter(key)

        if (actualParameterValue == null) {
            return MatchResult.Mismatch("'$actual' does not have query parameter '$key'")
        }

        if (actualParameterValue == expectedValue) {
            return MatchResult.Match
        } else {
            return MatchResult.Mismatch("'$actual' has query parameter '$key' with value '$actualParameterValue'")
        }
    }

    override val description: String
        get() = "has query parameter '$key' with value '$expectedValue'"
}

private fun requestWithJsonBody(predicate: (JsonObject) -> Unit) = check<Request> { request ->
    assertThat(request.body()!!.contentType(), equalTo(MediaType.get("application/json; charset=utf-8")))

    val buffer = Buffer()
    request.body()!!.writeTo(buffer)
    val parsedBody = JsonTreeParser(buffer.readUtf8()).readFully() as JsonObject
    predicate(parsedBody)
}

private fun requestWithBody(expectedBody: RequestBody) = check<Request> { request ->
    assertThat(request.body(), equalTo(expectedBody))
}

class ProgressReceiver {
    val updatesReceived = mutableListOf<JsonObject>()

    fun onProgressUpdate(update: JsonObject) {
        updatesReceived.add(update)
    }
}

private fun receivedAllUpdatesFrom(response: String): Matcher<ProgressReceiver> = receivedAllUpdatesFrom(response.lines())

private fun receivedAllUpdatesFrom(lines: Iterable<String>): Matcher<ProgressReceiver> {
    val expectedUpdates = lines.map { JsonTreeParser(it).readFully().jsonObject }

    return has(ProgressReceiver::updatesReceived, equalTo(expectedUpdates))
}
