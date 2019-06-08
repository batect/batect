/*
   Copyright 2017-2019 Charles Korn.

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
import batect.docker.run.ContainerIOStreamer
import batect.docker.run.ContainerInputStream
import batect.docker.run.ContainerKiller
import batect.docker.run.ContainerOutputStream
import batect.docker.run.ContainerTTYManager
import batect.docker.run.ContainerWaiter
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withCause
import batect.testutils.withMessage
import batect.ui.ConsoleInfo
import batect.utils.Json
import batect.utils.Version
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.serialization.json.JsonObject
import org.mockito.invocation.InvocationOnMock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.IOException
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.CompletableFuture

object DockerClientSpec : Spek({
    describe("a Docker client") {
        val api by createForEachTest { mock<DockerAPI>() }
        val consoleInfo by createForEachTest { mock<ConsoleInfo>() }
        val credentialsProvider by createForEachTest { mock<DockerRegistryCredentialsProvider>() }
        val imageBuildContextFactory by createForEachTest { mock<DockerImageBuildContextFactory>() }
        val dockerfileParser by createForEachTest { mock<DockerfileParser>() }
        val waiter by createForEachTest { mock<ContainerWaiter>() }
        val ioStreamer by createForEachTest { mock<ContainerIOStreamer>() }
        val killer by createForEachTest { mock<ContainerKiller>() }
        val ttyManager by createForEachTest { mock<ContainerTTYManager>() }
        val logger by createLoggerForEachTest()
        val imagePullProgressReporter by createForEachTest { mock<DockerImagePullProgressReporter>() }
        val imagePullProgressReporterFactory = { imagePullProgressReporter }
        val client by createForEachTest { DockerClient(api, consoleInfo, credentialsProvider, imageBuildContextFactory, dockerfileParser, waiter, ioStreamer, killer, ttyManager, logger, imagePullProgressReporterFactory) }

        describe("building an image") {
            val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
            val buildDirectory by createForEachTest { fileSystem.getPath("/path/to/build/dir") }
            val buildArgs = mapOf(
                "some_name" to "some_value",
                "some_other_name" to "some_other_value"
            )

            val dockerfilePath = "some-Dockerfile-path"
            val imageTags = setOf("some_image_tag", "some_other_image_tag")
            val context = DockerImageBuildContext(emptySet())

            given("the Dockerfile exists") {
                val resolvedDockerfilePath by createForEachTest { buildDirectory.resolve(dockerfilePath) }

                beforeEachTest {
                    Files.createDirectories(buildDirectory)
                    Files.createFile(resolvedDockerfilePath)

                    whenever(imageBuildContextFactory.createFromDirectory(buildDirectory)).doReturn(context)
                    whenever(dockerfileParser.extractBaseImageName(resolvedDockerfilePath)).doReturn("nginx:1.13.0")
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

                        beforeEachTest {
                            stubProgressUpdate(imagePullProgressReporter, output.lines()[0], imagePullProgress)
                            whenever(api.buildImage(any(), any(), any(), any(), any(), any())).doAnswer(sendProgressAndReturnImage(output, DockerImage("some-image-id")))
                        }

                        val statusUpdates by createForEachTest { mutableListOf<DockerImageBuildProgress>() }

                        val onStatusUpdate = fun(p: DockerImageBuildProgress) {
                            statusUpdates.add(p)
                        }

                        val result by runForEachTest { client.build(buildDirectory, buildArgs, dockerfilePath, imageTags, onStatusUpdate) }

                        it("builds the image") {
                            verify(api).buildImage(eq(context), eq(buildArgs), eq(dockerfilePath), eq(imageTags), eq(credentials), any())
                        }

                        it("returns the ID of the created image") {
                            assertThat(result.id, equalTo("some-image-id"))
                        }

                        it("sends status updates as the build progresses") {
                            assertThat(
                                statusUpdates, equalTo(
                                    listOf(
                                        DockerImageBuildProgress(1, 5, "FROM nginx:1.13.0", null),
                                        DockerImageBuildProgress(1, 5, "FROM nginx:1.13.0", imagePullProgress),
                                        DockerImageBuildProgress(2, 5, "RUN apt update && apt install -y curl && rm -rf /var/lib/apt/lists/*", null),
                                        DockerImageBuildProgress(3, 5, "COPY index.html /usr/share/nginx/html", null),
                                        DockerImageBuildProgress(4, 5, "COPY health-check.sh /tools/", null),
                                        DockerImageBuildProgress(5, 5, "HEALTHCHECK --interval=2s --retries=1 CMD /tools/health-check.sh", null)
                                    )
                                )
                            )
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
                        val statusUpdates by createForEachTest { mutableListOf<DockerImageBuildProgress>() }

                        beforeEachTest {
                            stubProgressUpdate(imagePullProgressReporter, output.lines()[0], imagePullProgress)
                            whenever(api.buildImage(any(), any(), any(), any(), any(), any())).doAnswer(sendProgressAndReturnImage(output, DockerImage("some-image-id")))

                            val onStatusUpdate = fun(p: DockerImageBuildProgress) {
                                statusUpdates.add(p)
                            }

                            client.build(buildDirectory, buildArgs, dockerfilePath, imageTags, onStatusUpdate)
                        }

                        it("sends status updates only once the first step is started") {
                            assertThat(
                                statusUpdates, equalTo(
                                    listOf(
                                        DockerImageBuildProgress(1, 5, "FROM nginx:1.13.0", null),
                                        DockerImageBuildProgress(1, 5, "FROM nginx:1.13.0", imagePullProgress),
                                        DockerImageBuildProgress(2, 5, "RUN apt update && apt install -y curl && rm -rf /var/lib/apt/lists/*", null)
                                    )
                                )
                            )
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
                            assertThat(
                                { client.build(buildDirectory, buildArgs, dockerfilePath, imageTags, {}) }, throws<ImageBuildFailedException>(
                                    withMessage("Could not build image: Could not load credentials: something went wrong.")
                                        and withCause(exception)
                                )
                            )
                        }
                    }
                }
            }

            given("the Dockerfile does not exist") {
                on("building the image") {
                    it("throws an appropriate exception") {
                        assertThat(
                            { client.build(buildDirectory, buildArgs, dockerfilePath, imageTags, {}) },
                            throws<ImageBuildFailedException>(withMessage("Could not build image: the Dockerfile 'some-Dockerfile-path' does not exist in '/path/to/build/dir'"))
                        )
                    }
                }
            }

            given("the Dockerfile exists but is not a child of the build directory") {
                val dockerfilePathOutsideBuildDir = "../some-Dockerfile"
                val resolvedDockerfilePath by createForEachTest { buildDirectory.resolve(dockerfilePathOutsideBuildDir) }

                beforeEachTest {
                    Files.createDirectories(buildDirectory)
                    Files.createFile(resolvedDockerfilePath)
                }

                on("building the image") {
                    it("throws an appropriate exception") {
                        assertThat(
                            { client.build(buildDirectory, buildArgs, dockerfilePathOutsideBuildDir, imageTags, {}) },
                            throws<ImageBuildFailedException>(withMessage("Could not build image: the Dockerfile '../some-Dockerfile' is not a child of '/path/to/build/dir'"))
                        )
                    }
                }
            }
        }

        describe("creating a container") {
            given("a container configuration and a built image") {
                val image = DockerImage("the-image")
                val network = DockerNetwork("the-network")
                val command = listOf("doStuff")
                val request = DockerContainerCreationRequest(image, network, command, "some-host", "some-host", emptyMap(), "/some-dir", emptySet(), emptySet(), HealthCheckConfig(), null, false, false, emptySet(), emptySet())

                on("creating the container") {
                    beforeEachTest { whenever(api.createContainer(request)).doReturn(DockerContainer("abc123")) }

                    val result by runForEachTest { client.create(request) }

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
                val outputStream by createForEachTest { mock<ContainerOutputStream>() }
                val inputStream by createForEachTest { mock<ContainerInputStream>() }
                val terminalRestorer by createForEachTest { mock<AutoCloseable>() }
                val signalRestorer by createForEachTest { mock<AutoCloseable>() }
                val resizingRestorer by createForEachTest { mock<AutoCloseable>() }

                beforeEachTest {
                    whenever(waiter.startWaitingForContainerToExit(container)).doReturn(CompletableFuture.completedFuture(123))
                    whenever(api.attachToContainerOutput(container)).doReturn(outputStream)
                    whenever(api.attachToContainerInput(container)).doReturn(inputStream)
                    whenever(consoleInfo.enterRawMode()).doReturn(terminalRestorer)
                    whenever(killer.killContainerOnSigint(container)).doReturn(signalRestorer)
                    whenever(ttyManager.monitorForSizeChanges(container)).doReturn(resizingRestorer)
                }

                on("running the container") {
                    val result by runForEachTest { client.run(container) }

                    it("returns the exit code from the container") {
                        assertThat(result.exitCode, equalTo(123))
                    }

                    it("starts waiting for the container to exit before starting the container") {
                        inOrder(api, waiter) {
                            verify(waiter).startWaitingForContainerToExit(container)
                            verify(api).startContainer(container)
                        }
                    }

                    it("starts streaming I/O after starting the container") {
                        inOrder(api, ioStreamer) {
                            verify(api).startContainer(container)
                            verify(ioStreamer).stream(outputStream, inputStream)
                        }
                    }

                    it("starts streaming I/O after putting the terminal into raw mode") {
                        inOrder(consoleInfo, ioStreamer) {
                            verify(consoleInfo).enterRawMode()
                            verify(ioStreamer).stream(outputStream, inputStream)
                        }
                    }

                    it("starts monitoring for terminal size changes after starting the container but before streaming I/O") {
                        inOrder(api, ttyManager, ioStreamer) {
                            verify(api).startContainer(container)
                            verify(ttyManager).monitorForSizeChanges(container)
                            verify(ioStreamer).stream(outputStream, inputStream)
                        }
                    }

                    it("stops monitoring for terminal size changes after the streaming completes") {
                        inOrder(ioStreamer, resizingRestorer) {
                            verify(ioStreamer).stream(outputStream, inputStream)
                            verify(resizingRestorer).close()
                        }
                    }

                    it("configures killing the container when a SIGINT is received after starting the container but before entering raw mode") {
                        inOrder(api, killer, consoleInfo) {
                            verify(api).startContainer(container)
                            verify(killer).killContainerOnSigint(container)
                            verify(consoleInfo).enterRawMode()
                        }
                    }

                    it("restores the terminal and signal handling state after streaming completes") {
                        inOrder(ioStreamer, terminalRestorer, signalRestorer) {
                            verify(ioStreamer).stream(outputStream, inputStream)
                            verify(terminalRestorer).close()
                            verify(signalRestorer).close()
                        }
                    }

                    it("attaches to the container output before starting the container") {
                        inOrder(api) {
                            verify(api).attachToContainerOutput(container)
                            verify(api).startContainer(container)
                        }
                    }

                    it("attaches to the container input before starting the container") {
                        inOrder(api) {
                            verify(api).attachToContainerInput(container)
                            verify(api).startContainer(container)
                        }
                    }

                    it("closes the output stream after streaming the output completes") {
                        inOrder(ioStreamer, outputStream) {
                            verify(ioStreamer).stream(outputStream, inputStream)
                            verify(outputStream).close()
                        }
                    }

                    it("closes the input stream after streaming the output completes") {
                        inOrder(ioStreamer, inputStream) {
                            verify(ioStreamer).stream(outputStream, inputStream)
                            verify(inputStream).close()
                        }
                    }
                }
            }
        }

        describe("starting a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")

                on("starting that container") {
                    beforeEachTest { client.start(container) }

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
                    beforeEachTest { client.stop(container) }

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
                            healthCheck = DockerContainerHealthCheckConfig()
                        )
                    ))
                }

                on("waiting for that container to become healthy") {
                    val result by runForEachTest { client.waitForHealthStatus(container) }

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
                            healthCheck = DockerContainerHealthCheckConfig(
                                test = listOf("some-command"),
                                interval = Duration.ofSeconds(2),
                                timeout = Duration.ofSeconds(1),
                                startPeriod = Duration.ofSeconds(10),
                                retries = 4
                            )
                        )
                    ))
                }

                given("the health check passes") {
                    beforeEachTest {
                        whenever(api.waitForNextEventForContainer(eq(container), eq(setOf("die", "health_status")), any()))
                            .thenReturn(DockerEvent("health_status: healthy"))
                    }

                    on("waiting for that container to become healthy") {
                        val result by runForEachTest { client.waitForHealthStatus(container) }

                        it("waits with a timeout that allows the container time to start and become healthy") {
                            verify(api).waitForNextEventForContainer(any(), any(), eq(Duration.ofSeconds(10 + (3 * 4) + 1)))
                        }

                        it("reports that the container became healthy") {
                            assertThat(result, equalTo(HealthStatus.BecameHealthy))
                        }
                    }
                }

                given("the health check fails") {
                    beforeEachTest {
                        whenever(api.waitForNextEventForContainer(eq(container), eq(setOf("die", "health_status")), any()))
                            .thenReturn(DockerEvent("health_status: unhealthy"))
                    }

                    on("waiting for that container to become healthy") {
                        val result by runForEachTest { client.waitForHealthStatus(container) }

                        it("reports that the container became unhealthy") {
                            assertThat(result, equalTo(HealthStatus.BecameUnhealthy))
                        }
                    }
                }

                given("the container exits before the health check reports") {
                    beforeEachTest {
                        whenever(api.waitForNextEventForContainer(eq(container), eq(setOf("die", "health_status")), any()))
                            .thenReturn(DockerEvent("die"))
                    }

                    on("waiting for that container to become healthy") {
                        val result by runForEachTest { client.waitForHealthStatus(container) }

                        it("reports that the container exited") {
                            assertThat(result, equalTo(HealthStatus.Exited))
                        }
                    }
                }

                given("getting the next event for the container fails") {
                    beforeEachTest {
                        whenever(api.waitForNextEventForContainer(eq(container), eq(setOf("die", "health_status")), any()))
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

                beforeEachTest { whenever(api.inspectContainer(container)).doReturn(info) }

                val details by runForEachTest { client.getLastHealthCheckResult(container) }

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

                beforeEachTest { whenever(api.inspectContainer(container)).doReturn(info) }

                val details by runForEachTest { client.getLastHealthCheckResult(container) }

                it("returns the details of the last health check result") {
                    assertThat(details, equalTo(DockerHealthCheckResult(1, "this is the most recent health check")))
                }
            }

            on("the container not having a health check") {
                val info = DockerContainerInfo(
                    DockerContainerState(health = null),
                    DockerContainerConfiguration(DockerContainerHealthCheckConfig())
                )

                beforeEachTest { whenever(api.inspectContainer(container)).doReturn(info) }

                it("throws an appropriate exception") {
                    assertThat({ client.getLastHealthCheckResult(container) },
                        throws<ContainerHealthCheckException>(withMessage("Could not get the last health check result for container 'some-container'. The container does not have a health check.")))
                }
            }

            on("getting the container's details failing") {
                beforeEachTest { whenever(api.inspectContainer(container)).doThrow(ContainerInspectionFailedException("Something went wrong.")) }

                it("throws an appropriate exception") {
                    assertThat({ client.getLastHealthCheckResult(container) },
                        throws<ContainerHealthCheckException>(withMessage("Could not get the last health check result for container 'some-container': Something went wrong.")))
                }
            }
        }

        on("creating a new bridge network") {
            beforeEachTest { whenever(api.createNetwork()).doReturn(DockerNetwork("the-network-id")) }

            val result by runForEachTest { client.createNewBridgeNetwork() }

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
                    beforeEachTest { client.deleteNetwork(network) }

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
                    beforeEachTest { client.remove(container) }

                    it("sends a request to the Docker daemon to remove the container") {
                        verify(api).removeContainer(container)
                    }
                }
            }
        }

        describe("getting Docker version information") {
            on("the Docker version command invocation succeeding") {
                val versionInfo = DockerVersionInfo(Version(17, 4, 0), "1.27", "1.12", "deadbee")

                beforeEachTest { whenever(api.getServerVersionInfo()).doReturn(versionInfo) }

                it("returns the version information from Docker") {
                    assertThat(client.getDockerVersionInfo(), equalTo(DockerVersionInfoRetrievalResult.Succeeded(versionInfo)))
                }
            }

            on("running the Docker version command throwing an exception (for example, because Docker is not installed)") {
                beforeEachTest { whenever(api.getServerVersionInfo()).doThrow(RuntimeException("Something went wrong")) }

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
                        val firstProgressUpdate = Json.parser.parseJson("""{"thing": "value"}""").jsonObject
                        val secondProgressUpdate = Json.parser.parseJson("""{"thing": "other value"}""").jsonObject

                        beforeEachTest {
                            whenever(imagePullProgressReporter.processProgressUpdate(firstProgressUpdate)).thenReturn(DockerImagePullProgress("Doing something", 10, 20))
                            whenever(imagePullProgressReporter.processProgressUpdate(secondProgressUpdate)).thenReturn(null)

                            whenever(api.pullImage(any(), any(), any())).then { invocation ->
                                @Suppress("UNCHECKED_CAST")
                                val onProgressUpdate = invocation.arguments[2] as (JsonObject) -> Unit
                                onProgressUpdate(firstProgressUpdate)
                                onProgressUpdate(secondProgressUpdate)

                                null
                            }
                        }

                        val progressUpdatesReceived by createForEachTest { mutableListOf<DockerImagePullProgress>() }
                        val image by runForEachTest { client.pullImage("some-image") { progressUpdatesReceived.add(it) } }

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
                beforeEachTest { whenever(api.hasImage("some-image")).thenReturn(true) }

                val image by runForEachTest { client.pullImage("some-image", {}) }

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
                given("getting daemon version info succeeds") {
                    given("the daemon reports an API version that is greater than required") {
                        beforeEachTest {
                            whenever(api.getServerVersionInfo()).thenReturn(DockerVersionInfo(Version(1, 2, 3), "1.31", "xxx", "xxx"))
                        }

                        it("returns success") {
                            assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Succeeded))
                        }
                    }

                    given("the daemon reports an API version that is exactly the required version") {
                        beforeEachTest {
                            whenever(api.getServerVersionInfo()).thenReturn(DockerVersionInfo(Version(1, 2, 3), "1.30", "xxx", "xxx"))
                        }

                        it("returns success") {
                            assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Succeeded))
                        }
                    }

                    given("the daemon reports an API version that is lower than required") {
                        beforeEachTest {
                            whenever(api.getServerVersionInfo()).thenReturn(DockerVersionInfo(Version(1, 2, 3), "1.29", "xxx", "xxx"))
                        }

                        it("returns failure") {
                            assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Failed("batect requires Docker 17.06 or later, but version 1.2.3 is installed.")))
                        }
                    }
                }

                given("getting daemon version info fails") {
                    beforeEachTest {
                        whenever(api.getServerVersionInfo()).doThrow(DockerException("Something went wrong."))
                    }

                    it("returns failure") {
                        assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Failed("Something went wrong.")))
                    }
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
    val json = Json.parser.parseJson(input).jsonObject
    whenever(reporter.processProgressUpdate(eq(json))).thenReturn(update)
}

private fun sendProgressAndReturnImage(progressUpdates: String, image: DockerImage) = { invocation: InvocationOnMock ->
    @Suppress("UNCHECKED_CAST")
    val onProgressUpdate = invocation.arguments.last() as (JsonObject) -> Unit

    progressUpdates.lines().forEach { line ->
        onProgressUpdate(Json.parser.parseJson(line).jsonObject)
    }

    image
}
