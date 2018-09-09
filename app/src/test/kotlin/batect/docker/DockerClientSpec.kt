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
import batect.docker.pull.DockerImagePullProgress
import batect.docker.pull.DockerImagePullProgressReporter
import batect.docker.pull.DockerRegistryCredentials
import batect.docker.pull.DockerRegistryCredentialsProvider
import batect.os.ProcessOutput
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
import java.io.IOException

object DockerClientSpec : Spek({
    describe("a Docker client") {
        val processRunner by createForEachTest { mock<ProcessRunner>() }
        val api by createForEachTest { mock<DockerAPI>() }
        val consoleInfo by createForEachTest { mock<ConsoleInfo>() }
        val credentialsProvider by createForEachTest { mock<DockerRegistryCredentialsProvider>() }
        val logger by createLoggerForEachTest()
        val imagePullProgressReporter by createForEachTest { mock<DockerImagePullProgressReporter>() }
        val imagePullProgressReporterFactory = { imagePullProgressReporter }
        val client by createForEachTest { DockerClient(processRunner, api, consoleInfo, credentialsProvider, logger, imagePullProgressReporterFactory) }

        describe("building an image") {
            given("a container configuration") {
                val buildDirectory = "/path/to/build/dir"
                val buildArgs = mapOf(
                    "some_name" to "some_value",
                    "some_other_name" to "some_other_value"
                )

                on("a successful build") {
                    val output = """
                        |Sending build context to Docker daemon  3.072kB
                        |Step 1/3 : FROM nginx:1.13.0
                        | ---> 3448f27c273f
                        |Step 2/3 : COPY health-check.sh /tools/
                        | ---> Using cache
                        | ---> 071856168043
                        |Step 3/3 : HEALTHCHECK --interval=2s --retries=1 CMD /tools/health-check.sh
                        | ---> Using cache
                        | ---> 11d3e1df9526
                        |Successfully built 11d3e1df9526
                        |""".trimMargin()

                    whenever(processRunner.runAndStreamOutput(any(), any()))
                        .then { invocation ->
                            @Suppress("UNCHECKED_CAST")
                            val outputProcessor: (String) -> Unit = invocation.arguments[1] as (String) -> Unit
                            output.lines().forEach { outputProcessor(it) }

                            ProcessOutput(0, output)
                        }

                    val statusUpdates = mutableListOf<DockerImageBuildProgress>()

                    val onStatusUpdate = fun(p: DockerImageBuildProgress) {
                        statusUpdates.add(p)
                    }

                    val result = client.build(buildDirectory, buildArgs, onStatusUpdate)

                    it("builds the image") {
                        verify(processRunner).runAndStreamOutput(eq(listOf(
                            "docker", "build",
                            "--build-arg", "some_name=some_value",
                            "--build-arg", "some_other_name=some_other_value",
                            buildDirectory
                        )), any())
                    }

                    it("returns the ID of the created image") {
                        assertThat(result.id, equalTo("11d3e1df9526"))
                    }

                    it("sends status updates as the build progresses") {
                        assertThat(statusUpdates, equalTo(listOf(
                            DockerImageBuildProgress(1, 3, "FROM nginx:1.13.0"),
                            DockerImageBuildProgress(2, 3, "COPY health-check.sh /tools/"),
                            DockerImageBuildProgress(3, 3, "HEALTHCHECK --interval=2s --retries=1 CMD /tools/health-check.sh")
                        )))
                    }
                }

                on("a successful build with multiple messages that appear to contain the image ID") {
                    val output = """
                        |Sending build context to Docker daemon  2.048kB
                        |Step 1/3 : FROM ucalgary/python-librdkafka
                        |---> 18f2baa09b5a
                        |Step 2/3 : RUN apk add --no-cache gcc linux-headers libc-dev
                        |---> Using cache
                        |---> aba46ffd34d1
                        |Step 3/3 : RUN pip install confluent-kafka
                        |---> Running in 881227951a4a
                        |Collecting confluent-kafka
                        |  Downloading confluent-kafka-0.11.0.tar.gz (42kB)
                        |Building wheels for collected packages: confluent-kafka
                        |  Running setup.py bdist_wheel for confluent-kafka: started
                        |  Running setup.py bdist_wheel for confluent-kafka: finished with status 'done'
                        |  Stored in directory: /root/.cache/pip/wheels/16/01/47/3c47cdadbcfb415df612631e5168db2123594c3903523716df
                        |Successfully built confluent-kafka
                        |Installing collected packages: confluent-kafka
                        |Successfully installed confluent-kafka-0.11.0
                        |Removing intermediate container 881227951a4a
                        |---> 95bc4e66a4f9
                        |Successfully built 95bc4e66a4f9
                        |""".trimMargin()

                    whenever(processRunner.runAndStreamOutput(any(), any()))
                        .then { invocation ->
                            @Suppress("UNCHECKED_CAST")
                            val outputProcessor: (String) -> Unit = invocation.arguments[1] as (String) -> Unit
                            output.lines().forEach { outputProcessor(it) }

                            ProcessOutput(0, output)
                        }

                    val result = client.build(buildDirectory, buildArgs) {}

                    it("returns the ID of the created image") {
                        assertThat(result.id, equalTo("95bc4e66a4f9"))
                    }
                }

                on("a failed build") {
                    val onStatusUpdate = { _: DockerImageBuildProgress -> }

                    whenever(processRunner.runAndStreamOutput(any(), any())).thenReturn(ProcessOutput(1, "Some output from Docker"))

                    it("raises an appropriate exception") {
                        assertThat({ client.build(buildDirectory, emptyMap(), onStatusUpdate) }, throws<ImageBuildFailedException>(withMessage("Image build failed. Output from Docker was: Some output from Docker")))
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
