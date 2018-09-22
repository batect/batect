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
import batect.docker.build.DockerImageBuildContextFactory
import batect.docker.build.DockerfileParser
import batect.docker.pull.DockerImagePullProgress
import batect.docker.pull.DockerImagePullProgressReporter
import batect.docker.pull.DockerRegistryCredentials
import batect.docker.pull.DockerRegistryCredentialsProvider
import batect.os.ProcessRunner
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.withCause
import batect.testutils.withMessage
import batect.ui.ConsoleInfo
import batect.utils.Version
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTreeParser
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.mockito.invocation.InvocationOnMock
import java.io.IOException
import java.nio.file.Paths

object DockerClientSpec : Spek({
    describe("a Docker client") {
        val processRunner by createForEachTest { mock<ProcessRunner>() }
        val api by createForEachTest { mock<DockerAPI>() }
        val consoleInfo by createForEachTest { mock<ConsoleInfo>() }
        val credentialsProvider by createForEachTest { mock<DockerRegistryCredentialsProvider>() }
        val imageBuildContextFactory by createForEachTest { mock<DockerImageBuildContextFactory>() }
        val dockerfileParser by createForEachTest { mock<DockerfileParser>() }
        val logger by createLoggerForEachTest()
        val imagePullProgressReporter by createForEachTest { mock<DockerImagePullProgressReporter>() }
        val imagePullProgressReporterFactory = { imagePullProgressReporter }
        val client by createForEachTest { DockerClient(processRunner, api, consoleInfo, credentialsProvider, imageBuildContextFactory, dockerfileParser, logger, imagePullProgressReporterFactory) }

        describe("building an image") {
            val buildDirectory = "/path/to/build/dir"
            val buildArgs = mapOf(
                "some_name" to "some_value",
                "some_other_name" to "some_other_value"
            )

            val context = DockerImageBuildContext(emptySet())

            beforeEachTest {
                whenever(imageBuildContextFactory.createFromDirectory(Paths.get(buildDirectory))).doReturn(context)
                whenever(dockerfileParser.extractBaseImageName(Paths.get("/path/to/build/dir/Dockerfile"))).doReturn("nginx:1.13.0")
            }

            given("getting the credentials for the base image succeeds") {
                val credentials = mock<DockerRegistryCredentials>()

                beforeEachTest {
                    whenever(credentialsProvider.getCredentials("nginx:1.13.0")).doReturn(credentials)
                }

                on("a successful build") {
                    val output = """
                        |{"stream":"Step 1/5 : FROM nginx:1.13.0"}
                        |{"status":"pulling the image"}
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

                    val imagePullProgress = DockerImagePullProgress("Doing something", 10, 20)
                    stubProgressUpdate(imagePullProgressReporter, output.lines()[0], imagePullProgress)

                    whenever(api.buildImage(any(), any(), any(), any())).doAnswer(sendProgressAndReturnImage(output, DockerImage("some-image-id")))

                    val statusUpdates = mutableListOf<DockerImageBuildProgress>()

                    val onStatusUpdate = fun(p: DockerImageBuildProgress) {
                        statusUpdates.add(p)
                    }

                    val result = client.build(buildDirectory, buildArgs, onStatusUpdate)

                    it("builds the image") {
                        verify(api).buildImage(eq(context), eq(buildArgs), eq(credentials), any())
                    }

                    it("returns the ID of the created image") {
                        assertThat(result.id, equalTo("some-image-id"))
                    }

                    it("sends status updates as the build progresses") {
                        assertThat(statusUpdates, equalTo(listOf(
                            DockerImageBuildProgress(1, 5, "FROM nginx:1.13.0", null),
                            DockerImageBuildProgress(1, 5, "FROM nginx:1.13.0", imagePullProgress),
                            DockerImageBuildProgress(2, 5, "RUN apt update && apt install -y curl && rm -rf /var/lib/apt/lists/*", null),
                            DockerImageBuildProgress(3, 5, "COPY index.html /usr/share/nginx/html", null),
                            DockerImageBuildProgress(4, 5, "COPY health-check.sh /tools/", null),
                            DockerImageBuildProgress(5, 5, "HEALTHCHECK --interval=2s --retries=1 CMD /tools/health-check.sh", null)
                        )))
                    }
                }

                on("the daemon sending image pull information before sending any step progress information") {
                    val output = """
                        |{"status":"pulling the image"}
                        |{"stream":"Step 1/5 : FROM nginx:1.13.0"}
                        |{"status":"pulling the image"}
                        |{"stream":"\n"}
                        |{"stream":" ---\u003e 3448f27c273f\n"}
                        |{"stream":"Step 2/5 : RUN apt update \u0026\u0026 apt install -y curl \u0026\u0026 rm -rf /var/lib/apt/lists/*"}
                    """.trimMargin()

                    val imagePullProgress = DockerImagePullProgress("Doing something", 10, 20)
                    stubProgressUpdate(imagePullProgressReporter, output.lines()[0], imagePullProgress)

                    whenever(api.buildImage(any(), any(), any(), any())).doAnswer(sendProgressAndReturnImage(output, DockerImage("some-image-id")))

                    val statusUpdates = mutableListOf<DockerImageBuildProgress>()

                    val onStatusUpdate = fun(p: DockerImageBuildProgress) {
                        statusUpdates.add(p)
                    }

                    client.build(buildDirectory, buildArgs, onStatusUpdate)

                    it("sends status updates only once the first step is started") {
                        assertThat(statusUpdates, equalTo(listOf(
                            DockerImageBuildProgress(1, 5, "FROM nginx:1.13.0", null),
                            DockerImageBuildProgress(1, 5, "FROM nginx:1.13.0", imagePullProgress),
                            DockerImageBuildProgress(2, 5, "RUN apt update && apt install -y curl && rm -rf /var/lib/apt/lists/*", null)
                        )))
                    }
                }
            }

            given("getting credentials for the base image fails") {
                val exception = DockerRegistryCredentialsException("Could not load credentials: something went wrong.")

                beforeEachTest {
                    whenever(credentialsProvider.getCredentials("nginx:1.13.0")).thenThrow(exception)
                }

                on("building the image") {
                    it("throws an appropriate exception") {
                        assertThat({ client.build(buildDirectory, buildArgs, {}) }, throws<ImageBuildFailedException>(
                            withMessage("Could not build image: Could not load credentials: something went wrong.")
                                and withCause(exception)
                        ))
                    }
                }
            }
        }

        describe("creating a container") {
            given("a container configuration and a built image") {
                val image = DockerImage("the-image")
                val network = DockerNetwork("the-network")
                val command = listOf("doStuff")
                val request = DockerContainerCreationRequest(image, network, command, "some-host", "some-host", emptyMap(), "/some-dir", emptySet(), emptySet(), HealthCheckConfig(), null)

                on("creating the container") {
                    whenever(api.createContainer(request)).doReturn(DockerContainer("abc123"))

                    val result = client.create(request)

                    it("sends a request to the Docker daemon to create the container") {
                        verify(api).createContainer(request)
                    }

                    it("returns the ID of the created container") {
                        assertThat(result.id, equalTo("abc123"))
                    }
                }
            }
        }

        describe("running a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")

                given("and that the application is being run with a TTY connected to STDIN") {
                    on("running the container") {
                        whenever(consoleInfo.stdinIsTTY).thenReturn(true)
                        whenever(processRunner.run(any())).thenReturn(123)

                        val result = client.run(container)

                        it("starts the container in interactive mode") {
                            verify(processRunner).run(listOf("docker", "start", "--attach", "--interactive", container.id))
                        }

                        it("returns the exit code from the container") {
                            assertThat(result.exitCode, equalTo(123))
                        }
                    }
                }

                given("and that the application is being run without a TTY connected to STDIN") {
                    on("running the container") {
                        whenever(consoleInfo.stdinIsTTY).thenReturn(false)
                        whenever(processRunner.run(any())).thenReturn(123)

                        val result = client.run(container)

                        it("starts the container in non-interactive mode") {
                            verify(processRunner).run(listOf("docker", "start", "--attach", container.id))
                        }

                        it("returns the exit code from the container") {
                            assertThat(result.exitCode, equalTo(123))
                        }
                    }
                }
            }
        }

        describe("starting a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")

                on("starting that container") {
                    client.start(container)

                    it("sends a request to the Docker daemon to start the container") {
                        verify(api).startContainer(container)
                    }
                }
            }
        }

        describe("stopping a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")

                on("stopping that container") {
                    client.stop(container)

                    it("sends a request to the Docker daemon to stop the container") {
                        verify(api).stopContainer(container)
                    }
                }
            }
        }

        describe("waiting for a container to report its health status") {
            given("a Docker container with no health check") {
                val container = DockerContainer("the-container-id")

                beforeEachTest {
                    whenever(api.inspectContainer(container)).thenReturn(DockerContainerInfo(
                        DockerContainerState(),
                        DockerContainerConfiguration(
                            healthCheck = DockerContainerHealthCheckConfig(null)
                        )
                    ))
                }

                on("waiting for that container to become healthy") {
                    val result = client.waitForHealthStatus(container)

                    it("reports that the container does not have a health check") {
                        assertThat(result, equalTo(HealthStatus.NoHealthCheck))
                    }
                }
            }

            given("the Docker client returns an error when checking if the container has a health check") {
                val container = DockerContainer("the-container-id")

                beforeEachTest {
                    whenever(api.inspectContainer(container)).thenThrow(ContainerInspectionFailedException("Something went wrong"))
                }

                on("waiting for that container to become healthy") {
                    it("throws an appropriate exception") {
                        assertThat({ client.waitForHealthStatus(container) }, throws<ContainerHealthCheckException>(withMessage("Checking if container 'the-container-id' has a health check failed: Something went wrong")))
                    }
                }
            }

            given("a Docker container with a health check") {
                val container = DockerContainer("the-container-id")

                beforeEachTest {
                    whenever(api.inspectContainer(container)).thenReturn(DockerContainerInfo(
                        DockerContainerState(),
                        DockerContainerConfiguration(
                            healthCheck = DockerContainerHealthCheckConfig(listOf("some-command"))
                        )
                    ))
                }

                given("the health check passes") {
                    beforeEachTest {
                        whenever(api.waitForNextEventForContainer(container, setOf("die", "health_status")))
                            .thenReturn(DockerEvent("health_status: healthy"))
                    }

                    on("waiting for that container to become healthy") {
                        val result = client.waitForHealthStatus(container)

                        it("reports that the container became healthy") {
                            assertThat(result, equalTo(HealthStatus.BecameHealthy))
                        }
                    }
                }

                given("the health check fails") {
                    beforeEachTest {
                        whenever(api.waitForNextEventForContainer(container, setOf("die", "health_status")))
                            .thenReturn(DockerEvent("health_status: unhealthy"))
                    }

                    on("waiting for that container to become healthy") {
                        val result = client.waitForHealthStatus(container)

                        it("reports that the container became unhealthy") {
                            assertThat(result, equalTo(HealthStatus.BecameUnhealthy))
                        }
                    }
                }

                given("the container exits before the health check reports") {
                    beforeEachTest {
                        whenever(api.waitForNextEventForContainer(container, setOf("die", "health_status")))
                            .thenReturn(DockerEvent("die"))
                    }

                    on("waiting for that container to become healthy") {
                        val result = client.waitForHealthStatus(container)

                        it("reports that the container exited") {
                            assertThat(result, equalTo(HealthStatus.Exited))
                        }
                    }
                }

                given("getting the next event for the container fails") {
                    beforeEachTest {
                        whenever(api.waitForNextEventForContainer(container, setOf("die", "health_status")))
                            .thenThrow(DockerException("Something went wrong."))
                    }

                    on("waiting for that container to become healthy") {
                        it("throws an appropriate exception") {
                            assertThat({ client.waitForHealthStatus(container) }, throws<ContainerHealthCheckException>(withMessage("Waiting for health status of container 'the-container-id' failed: Something went wrong.")))
                        }
                    }
                }
            }
        }

        describe("getting the last health check result for a container") {
            val container = DockerContainer("some-container")

            on("the container only having one last health check result") {
                val info = DockerContainerInfo(
                    DockerContainerState(
                        DockerContainerHealthCheckState(listOf(
                            DockerHealthCheckResult(1, "something went wrong")
                        ))
                    ),
                    DockerContainerConfiguration(DockerContainerHealthCheckConfig())
                )

                whenever(api.inspectContainer(container)).doReturn(info)

                val details = client.getLastHealthCheckResult(container)

                it("returns the details of the last health check result") {
                    assertThat(details, equalTo(DockerHealthCheckResult(1, "something went wrong")))
                }
            }

            on("the container having a full set of previous health check results") {
                val info = DockerContainerInfo(
                    DockerContainerState(
                        DockerContainerHealthCheckState(listOf(
                            DockerHealthCheckResult(1, ""),
                            DockerHealthCheckResult(1, ""),
                            DockerHealthCheckResult(1, ""),
                            DockerHealthCheckResult(1, ""),
                            DockerHealthCheckResult(1, "this is the most recent health check")
                        ))
                    ),
                    DockerContainerConfiguration(DockerContainerHealthCheckConfig())
                )

                whenever(api.inspectContainer(container)).doReturn(info)

                val details = client.getLastHealthCheckResult(container)

                it("returns the details of the last health check result") {
                    assertThat(details, equalTo(DockerHealthCheckResult(1, "this is the most recent health check")))
                }
            }

            on("the container not having a health check") {
                val info = DockerContainerInfo(
                    DockerContainerState(health = null),
                    DockerContainerConfiguration(DockerContainerHealthCheckConfig())
                )

                whenever(api.inspectContainer(container)).doReturn(info)

                it("throws an appropriate exception") {
                    assertThat({ client.getLastHealthCheckResult(container) },
                        throws<ContainerHealthCheckException>(withMessage("Could not get the last health check result for container 'some-container'. The container does not have a health check.")))
                }
            }

            on("getting the container's details failing") {
                whenever(api.inspectContainer(container)).doThrow(ContainerInspectionFailedException("Something went wrong."))

                it("throws an appropriate exception") {
                    assertThat({ client.getLastHealthCheckResult(container) },
                        throws<ContainerHealthCheckException>(withMessage("Could not get the last health check result for container 'some-container': Something went wrong.")))
                }
            }
        }

        on("creating a new bridge network") {
            whenever(api.createNetwork()).doReturn(DockerNetwork("the-network-id"))

            val result = client.createNewBridgeNetwork()

            it("creates the network") {
                verify(api).createNetwork()
            }

            it("returns the ID of the created network") {
                assertThat(result.id, equalTo("the-network-id"))
            }
        }

        describe("deleting a network") {
            given("an existing network") {
                val network = DockerNetwork("abc123")

                on("deleting that network") {
                    client.deleteNetwork(network)

                    it("sends a request to the Docker daemon to delete the network") {
                        verify(api).deleteNetwork(network)
                    }
                }
            }
        }

        describe("removing a container") {
            given("an existing container") {
                val container = DockerContainer("the-container-id")

                on("removing that container") {
                    client.remove(container)

                    it("sends a request to the Docker daemon to remove the container") {
                        verify(api).removeContainer(container)
                    }
                }
            }
        }

        describe("getting Docker version information") {
            on("the Docker version command invocation succeeding") {
                val versionInfo = DockerVersionInfo(Version(17, 4, 0), "1.27", "1.12", "deadbee")
                whenever(api.getServerVersionInfo()).doReturn(versionInfo)

                it("returns the version information from Docker") {
                    assertThat(client.getDockerVersionInfo(), equalTo(DockerVersionInfoRetrievalResult.Succeeded(versionInfo)))
                }
            }

            on("running the Docker version command throwing an exception (for example, because Docker is not installed)") {
                whenever(api.getServerVersionInfo()).doThrow(RuntimeException("Something went wrong"))

                it("returns an appropriate message") {
                    assertThat(client.getDockerVersionInfo(), equalTo(DockerVersionInfoRetrievalResult.Failed("Could not get Docker version information because RuntimeException was thrown: Something went wrong")))
                }
            }
        }

        describe("pulling an image") {
            given("the image does not exist locally") {
                beforeEachTest {
                    whenever(api.hasImage("some-image")).thenReturn(false)
                }

                given("getting credentials for the image succeeds") {
                    val credentials = mock<DockerRegistryCredentials>()

                    beforeEachTest {
                        whenever(credentialsProvider.getCredentials("some-image")).thenReturn(credentials)
                    }

                    on("pulling the image") {
                        val firstProgressUpdate = JsonTreeParser("""{"thing": "value"}""").readFully().jsonObject
                        val secondProgressUpdate = JsonTreeParser("""{"thing": "other value"}""").readFully().jsonObject

                        whenever(imagePullProgressReporter.processProgressUpdate(firstProgressUpdate)).thenReturn(DockerImagePullProgress("Doing something", 10, 20))
                        whenever(imagePullProgressReporter.processProgressUpdate(secondProgressUpdate)).thenReturn(null)

                        whenever(api.pullImage(any(), any(), any())).then { invocation ->
                            @Suppress("UNCHECKED_CAST")
                            val onProgressUpdate = invocation.arguments[2] as (JsonObject) -> Unit
                            onProgressUpdate(firstProgressUpdate)
                            onProgressUpdate(secondProgressUpdate)

                            null
                        }

                        val progressUpdatesReceived = mutableListOf<DockerImagePullProgress>()
                        val image = client.pullImage("some-image") { progressUpdatesReceived.add(it) }

                        it("calls the Docker CLI to pull the image") {
                            verify(api).pullImage(eq("some-image"), eq(credentials), any())
                        }

                        it("sends notifications for all relevant progress updates") {
                            assertThat(progressUpdatesReceived, equalTo(listOf(DockerImagePullProgress("Doing something", 10, 20))))
                        }

                        it("returns the Docker image") {
                            assertThat(image, equalTo(DockerImage("some-image")))
                        }
                    }
                }

                given("getting credentials for the image fails") {
                    val exception = DockerRegistryCredentialsException("Could not load credentials: something went wrong.")

                    beforeEachTest {
                        whenever(credentialsProvider.getCredentials("some-image")).thenThrow(exception)
                    }

                    on("pulling the image") {
                        it("throws an appropriate exception") {
                            assertThat({ client.pullImage("some-image", {}) }, throws<ImagePullFailedException>(
                                withMessage("Could not pull image 'some-image': Could not load credentials: something went wrong.")
                                    and withCause(exception)
                            ))
                        }
                    }
                }
            }

            on("when the image already exists locally") {
                whenever(api.hasImage("some-image")).thenReturn(true)

                val image = client.pullImage("some-image", {})

                it("does not call the Docker CLI to pull the image again") {
                    verify(api, never()).pullImage(any(), any(), any())
                }

                it("returns the Docker image") {
                    assertThat(image, equalTo(DockerImage("some-image")))
                }
            }
        }

        describe("checking connectivity to the Docker daemon") {
            given("pinging the daemon succeeds") {
                it("returns success") {
                    assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Succeeded))
                }
            }

            given("pinging the daemon fails with a general Docker exception") {
                beforeEachTest {
                    whenever(api.ping()).doThrow(DockerException("Something went wrong."))
                }

                it("returns failure") {
                    assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Failed("Something went wrong.")))
                }
            }

            given("pinging the daemon fails due to an I/O issue") {
                beforeEachTest {
                    whenever(api.ping()).doAnswer { throw IOException("Something went wrong.") }
                }

                it("returns failure") {
                    assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Failed("Something went wrong.")))
                }
            }
        }
    }
})

private fun stubProgressUpdate(reporter: DockerImagePullProgressReporter, input: String, update: DockerImagePullProgress) {
    val json = JsonTreeParser(input).readFully().jsonObject
    whenever(reporter.processProgressUpdate(json)).thenReturn(update)
}

private fun sendProgressAndReturnImage(progressUpdates: String, image: DockerImage) = { invocation: InvocationOnMock ->
    @Suppress("UNCHECKED_CAST")
    val onProgressUpdate = invocation.arguments.last() as (JsonObject) -> Unit

    progressUpdates.lines().forEach { line ->
        onProgressUpdate(JsonTreeParser(line).readFully().jsonObject)
    }

    image
}
