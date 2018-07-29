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
import batect.os.ExecutableDoesNotExistException
import batect.os.Exited
import batect.os.KillProcess
import batect.os.KilledDuringProcessing
import batect.os.OutputProcessing
import batect.os.ProcessOutput
import batect.os.ProcessRunner
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.mockPost
import batect.testutils.withMessage
import batect.ui.ConsoleInfo
import batect.utils.Version
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.util.UUID

object DockerClientSpec : Spek({
    describe("a Docker client") {
        val processRunner by createForEachTest { mock<ProcessRunner>() }
        val httpClient by createForEachTest { mock<OkHttpClient>() }
        val dockerBaseUrl = "http://the-docker-daemon"
        val httpConfig by createForEachTest {
            mock<DockerHttpConfig> {
                on { client } doReturn httpClient
                on { baseUrl } doReturn HttpUrl.get(dockerBaseUrl)
            }
        }

        val creationCommandGenerator by createForEachTest { mock<DockerContainerCreationCommandGenerator>() }
        val consoleInfo by createForEachTest { mock<ConsoleInfo>() }
        val logger by createLoggerForEachTest()

        val client by createForEachTest { DockerClient(processRunner, httpConfig, creationCommandGenerator, consoleInfo, logger) }

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
                            val outputProcessor: (String) -> OutputProcessing<Unit> = invocation.arguments[1] as (String) -> OutputProcessing<Unit>
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
                            val outputProcessor: (String) -> OutputProcessing<Unit> = invocation.arguments[1] as (String) -> OutputProcessing<Unit>
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
                val commandLine = listOf("docker", "doStuff", "please")
                val request = DockerContainerCreationRequest(image, network, command, "some-host", "some-host", emptyMap(), "/some-dir", emptySet(), emptySet(), HealthCheckConfig(), null)

                beforeEachTest {
                    whenever(creationCommandGenerator.createCommandLine(request)).thenReturn(commandLine)
                }

                on("a successful creation") {
                    val containerId = "abc123"
                    whenever(processRunner.runAndCaptureOutput(any())).thenReturn(ProcessOutput(0, containerId + "\n"))

                    val result = client.create(request)

                    it("creates the container") {
                        verify(processRunner).runAndCaptureOutput(commandLine)
                    }

                    it("returns the ID of the created container") {
                        assertThat(result.id, equalTo(containerId))
                    }
                }

                on("a failed creation") {
                    whenever(processRunner.runAndCaptureOutput(any())).thenReturn(ProcessOutput(1, "Something went wrong."))

                    it("raises an appropriate exception") {
                        assertThat({ client.create(request) }, throws<ContainerCreationFailedException>(withMessage("Output from Docker was: Something went wrong.")))
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
                    val call = httpClient.mockPost("$dockerBaseUrl/v1.12/containers/the-container-id/start", "", 204)

                    client.start(container)

                    it("sends a request to the Docker daemon to start the container") {
                        verify(call).execute()
                    }
                }

                on("an unsuccessful start attempt") {
                    httpClient.mockPost("$dockerBaseUrl/v1.12/containers/the-container-id/start", """{"message": "Something went wrong."}""", 418)

                    it("raises an appropriate exception") {
                        assertThat({ client.start(container) }, throws<ContainerStartFailedException>(withMessage("Starting container 'the-container-id' failed: Something went wrong.")))
                    }
                }
            }
        }

        describe("stopping a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")

                on("stopping that container") {
                    whenever(processRunner.runAndCaptureOutput(any())).thenReturn(ProcessOutput(0, ""))

                    client.stop(container)

                    it("launches the Docker CLI to stop the container") {
                        verify(processRunner).runAndCaptureOutput(listOf("docker", "stop", container.id))
                    }
                }

                on("an unsuccessful stop attempt") {
                    whenever(processRunner.runAndCaptureOutput(any())).thenReturn(ProcessOutput(1, "Something went wrong."))

                    it("raises an appropriate exception") {
                        assertThat({ client.stop(container) }, throws<ContainerStopFailedException>(withMessage("Stopping container 'the-container-id' failed: Something went wrong.")))
                    }
                }
            }
        }

        describe("waiting for a container to report its health status") {
            given("a Docker container with no healthcheck") {
                val container = DockerContainer("the-container-id")

                on("waiting for that container to become healthy") {
                    val expectedCommand = listOf("docker", "inspect", "--format", "{{json .Config.Healthcheck}}", container.id)
                    whenever(processRunner.runAndCaptureOutput(expectedCommand)).thenReturn(ProcessOutput(0, "null\n"))

                    val result = client.waitForHealthStatus(container)

                    it("reports that the container does not have a healthcheck") {
                        assertThat(result, equalTo(HealthStatus.NoHealthCheck))
                    }
                }
            }

            given("the Docker client returns an error when checking if the container has a healthcheck") {
                val container = DockerContainer("the-container-id")

                on("waiting for that container to become healthy") {
                    val expectedCommand = listOf("docker", "inspect", "--format", "{{json .Config.Healthcheck}}", container.id)
                    whenever(processRunner.runAndCaptureOutput(expectedCommand)).thenReturn(ProcessOutput(1, "Something went wrong\n"))

                    it("throws an appropriate exception") {
                        assertThat({ client.waitForHealthStatus(container) }, throws<ContainerHealthCheckException>(withMessage("Checking if container 'the-container-id' has a healthcheck failed: Something went wrong")))
                    }
                }
            }

            given("a Docker container with a healthcheck that passes") {
                val container = DockerContainer("the-container-id")

                on("waiting for that container to become healthy") {
                    val hasHealthcheckCommand = listOf("docker", "inspect", "--format", "{{json .Config.Healthcheck}}", container.id)
                    whenever(processRunner.runAndCaptureOutput(hasHealthcheckCommand)).thenReturn(ProcessOutput(0, "{some healthcheck config}\n"))

                    processRunner.whenGettingEventsForContainerRespondWith(container.id, "health_status: healthy")

                    val result = client.waitForHealthStatus(container)

                    it("reports that the container became healthy") {
                        assertThat(result, equalTo(HealthStatus.BecameHealthy))
                    }
                }
            }

            given("a Docker container with a healthcheck that fails") {
                val container = DockerContainer("the-container-id")

                on("waiting for that container to become healthy") {
                    val hasHealthcheckCommand = listOf("docker", "inspect", "--format", "{{json .Config.Healthcheck}}", container.id)
                    whenever(processRunner.runAndCaptureOutput(hasHealthcheckCommand)).thenReturn(ProcessOutput(0, "{some healthcheck config}\n"))

                    processRunner.whenGettingEventsForContainerRespondWith(container.id, "health_status: unhealthy")

                    val result = client.waitForHealthStatus(container)

                    it("reports that the container became unhealthy") {
                        assertThat(result, equalTo(HealthStatus.BecameUnhealthy))
                    }
                }
            }

            given("a Docker container with a healthcheck that exits before the healthcheck reports") {
                val container = DockerContainer("the-container-id")

                on("waiting for that container to become healthy") {
                    val hasHealthcheckCommand = listOf("docker", "inspect", "--format", "{{json .Config.Healthcheck}}", container.id)
                    whenever(processRunner.runAndCaptureOutput(hasHealthcheckCommand)).thenReturn(ProcessOutput(0, "{some healthcheck config}\n"))

                    processRunner.whenGettingEventsForContainerRespondWith(container.id, "die")

                    val result = client.waitForHealthStatus(container)

                    it("reports that the container exited") {
                        assertThat(result, equalTo(HealthStatus.Exited))
                    }
                }
            }

            given("a Docker container with a healthcheck that causes the 'docker events' command to terminate early") {
                val container = DockerContainer("the-container-id")

                on("waiting for that container to become healthy") {
                    val hasHealthcheckCommand = listOf("docker", "inspect", "--format", "{{json .Config.Healthcheck}}", container.id)
                    whenever(processRunner.runAndCaptureOutput(hasHealthcheckCommand)).thenReturn(ProcessOutput(0, "{some healthcheck config}\n"))

                    val command = eventsCommandForContainer(container.id)
                    whenever(processRunner.runAndProcessOutput<HealthStatus>(eq(command), any())).thenReturn(Exited(123))

                    it("throws an appropriate exception") {
                        assertThat({ client.waitForHealthStatus(container) }, throws<ContainerHealthCheckException>(withMessage("Event stream for container 'the-container-id' exited early with exit code 123.")))
                    }
                }
            }
        }

        describe("getting the last health check result for a container") {
            val container = DockerContainer("some-container")
            val expectedCommand = listOf("docker", "inspect", container.id, "--format={{json .State.Health}}")

            on("the container only having one last health check result") {
                val response = """{
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
                                }""".trimIndent()

                whenever(processRunner.runAndCaptureOutput(expectedCommand)).thenReturn(ProcessOutput(0, response))

                val details = client.getLastHealthCheckResult(container)

                it("returns the details of the last health check result") {
                    assertThat(details, equalTo(DockerHealthCheckResult(1, "something went wrong")))
                }
            }

            on("the container having a full set of previous health check results") {
                val response = """{
                                  "Status": "unhealthy",
                                  "FailingStreak": 130,
                                  "Log": [
                                    {
                                      "Start": "2017-10-04T00:54:15.389708057Z",
                                      "End": "2017-10-04T00:54:15.426118682Z",
                                      "ExitCode": 1,
                                      "Output": ""
                                    },
                                    {
                                      "Start": "2017-10-04T00:54:17.435801514Z",
                                      "End": "2017-10-04T00:54:17.473788486Z",
                                      "ExitCode": 1,
                                      "Output": ""
                                    },
                                    {
                                      "Start": "2017-10-04T00:54:19.483518154Z",
                                      "End": "2017-10-04T00:54:19.534368638Z",
                                      "ExitCode": 1,
                                      "Output": ""
                                    },
                                    {
                                      "Start": "2017-10-04T00:54:21.546935143Z",
                                      "End": "2017-10-04T00:54:21.592975551Z",
                                      "ExitCode": 1,
                                      "Output": ""
                                    },
                                    {
                                      "Start": "2017-10-04T00:54:23.608075352Z",
                                      "End": "2017-10-04T00:54:23.646606606Z",
                                      "ExitCode": 1,
                                      "Output": "this is the most recent health check"
                                    }
                                  ]
                                }""".trimIndent()

                whenever(processRunner.runAndCaptureOutput(expectedCommand)).thenReturn(ProcessOutput(0, response))

                val details = client.getLastHealthCheckResult(container)

                it("returns the details of the last health check result") {
                    assertThat(details, equalTo(DockerHealthCheckResult(1, "this is the most recent health check")))
                }
            }

            on("the container not having a health check") {
                whenever(processRunner.runAndCaptureOutput(expectedCommand)).thenReturn(ProcessOutput(0, "null\n"))

                it("throws an appropriate exception") {
                    assertThat({ client.getLastHealthCheckResult(container) },
                        throws<ContainerHealthCheckException>(withMessage("Could not get the last health check result for container 'some-container'. The container does not have a health check.")))
                }
            }

            on("getting the container's details failing") {
                whenever(processRunner.runAndCaptureOutput(expectedCommand)).thenReturn(ProcessOutput(1, "Something went wrong.\n"))

                it("throws an appropriate exception") {
                    assertThat({ client.getLastHealthCheckResult(container) },
                        throws<ContainerHealthCheckException>(withMessage("Could not get the last health check result for container 'some-container': Something went wrong.")))
                }
            }
        }

        describe("creating a new bridge network") {
            on("a successful creation") {
                whenever(processRunner.runAndCaptureOutput(any())).thenReturn(ProcessOutput(0, "the-network-ID\n"))

                val result = client.createNewBridgeNetwork()

                it("creates the network") {
                    verify(processRunner).runAndCaptureOutput(check { command ->
                        val expectedCommandTemplate = listOf("docker", "network", "create", "--driver", "bridge")
                        assertThat(command.count(), equalTo(expectedCommandTemplate.size + 1))
                        assertThat(command.take(expectedCommandTemplate.size), equalTo(expectedCommandTemplate))
                        assertThat(command.last(), isUUID)
                    })
                }

                it("returns the ID of the created network") {
                    assertThat(result.id, equalTo("the-network-ID"))
                }
            }

            on("an unsuccessful creation") {
                whenever(processRunner.runAndCaptureOutput(any())).thenReturn(ProcessOutput(1, "Something went wrong.\n"))

                it("throws an appropriate exception") {
                    assertThat({ client.createNewBridgeNetwork() }, throws<NetworkCreationFailedException>(withMessage("Creation of network failed: Something went wrong.")))
                }
            }
        }

        describe("deleting a network") {
            val network = DockerNetwork("abc123")
            val expectedCommand = listOf("docker", "network", "rm", network.id)

            on("a successful deletion") {
                whenever(processRunner.runAndCaptureOutput(expectedCommand)).thenReturn(ProcessOutput(0, "Done!"))

                client.deleteNetwork(network)

                it("calls the Docker CLI to delete the network") {
                    verify(processRunner).runAndCaptureOutput(expectedCommand)
                }
            }

            on("an unsuccessful deletion") {
                whenever(processRunner.runAndCaptureOutput(expectedCommand)).thenReturn(ProcessOutput(1, "Something went wrong.\n"))

                it("throws an appropriate exception") {
                    assertThat({ client.deleteNetwork(network) }, throws<NetworkDeletionFailedException>(withMessage("Deletion of network 'abc123' failed: Something went wrong.")))
                }
            }
        }

        describe("removing a container") {
            val container = DockerContainer("some-id")
            val expectedCommand = listOf("docker", "rm", "--volumes", "some-id")

            on("a successful removal") {
                whenever(processRunner.runAndCaptureOutput(expectedCommand)).thenReturn(ProcessOutput(0, "Done!"))

                client.remove(container)

                it("calls the Docker CLI to remove the container") {
                    verify(processRunner).runAndCaptureOutput(expectedCommand)
                }
            }

            on("an unsuccessful deletion") {
                whenever(processRunner.runAndCaptureOutput(expectedCommand)).thenReturn(ProcessOutput(1, "Something went wrong.\n"))

                it("throws an appropriate exception") {
                    assertThat({ client.remove(container) }, throws<ContainerRemovalFailedException>(withMessage("Removal of container 'some-id' failed: Something went wrong.")))
                }
            }

            on("that container not existing") {
                whenever(processRunner.runAndCaptureOutput(expectedCommand)).thenReturn(ProcessOutput(1, "Error response from daemon: No such container: ${container.id}"))

                it("throws an appropriate exception") {
                    assertThat({ client.remove(container) }, throws<ContainerDoesNotExistException>(withMessage("Removing container 'some-id' failed because it does not exist.")))
                }
            }
        }

        describe("getting Docker version information") {
            val expectedCommand = listOf("docker", "version", "--format", "{{println .Client.Version}}{{println .Client.APIVersion}}{{println .Client.GitCommit}}{{println .Server.Version}}{{println .Server.APIVersion}}{{println .Server.MinAPIVersion}}{{println .Server.GitCommit}}")

            on("the Docker version command invocation succeeding") {
                whenever(processRunner.runAndCaptureOutput(expectedCommand)).thenReturn(ProcessOutput(0, "0.1\n2.3\nabc123\n4.5\n6.7\n8.9\ndef456\n"))

                it("returns the version information from Docker") {
                    assertThat(client.getDockerVersionInfo(), equalTo(DockerVersionInfoRetrievalResult.Succeeded(DockerVersionInfo(
                        DockerClientVersionInfo(Version(0, 1, 0), "2.3", "abc123"),
                        DockerServerVersionInfo(Version(4, 5, 0), "6.7", "8.9", "def456")
                    ))))
                }
            }

            on("the Docker version command invocation failing") {
                whenever(processRunner.runAndCaptureOutput(expectedCommand)).thenReturn(ProcessOutput(1, "Something went wrong\n"))

                it("returns an appropriate message") {
                    assertThat(client.getDockerVersionInfo(), equalTo(DockerVersionInfoRetrievalResult.Failed("Could not get Docker version information because the command failed: Something went wrong")))
                }
            }

            on("running the Docker version command throwing an exception (for example, because Docker is not installed)") {
                val exception = RuntimeException("Something went wrong")
                whenever(processRunner.runAndCaptureOutput(expectedCommand)).thenThrow(exception)

                it("returns an appropriate message") {
                    assertThat(client.getDockerVersionInfo(), equalTo(DockerVersionInfoRetrievalResult.Failed("Could not get Docker version information because RuntimeException was thrown: Something went wrong")))
                }
            }
        }

        describe("pulling an image") {
            val pullCommand = listOf("docker", "pull", "some-image")

            describe("when the image does not exist locally") {
                beforeEachTest {
                    whenever(processRunner.runAndCaptureOutput(listOf("docker", "images", "-q", "some-image"))).thenReturn(ProcessOutput(0, ""))
                }

                on("and pulling the image succeeds") {
                    whenever(processRunner.runAndCaptureOutput(pullCommand)).thenReturn(ProcessOutput(0, "Image pulled!"))

                    val image = client.pullImage("some-image")

                    it("calls the Docker CLI to pull the image") {
                        verify(processRunner).runAndCaptureOutput(pullCommand)
                    }

                    it("returns the Docker image") {
                        assertThat(image, equalTo(DockerImage("some-image")))
                    }
                }

                on("and pulling the image fails") {
                    whenever(processRunner.runAndCaptureOutput(pullCommand)).thenReturn(ProcessOutput(1, "Something went wrong.\n"))

                    it("throws an appropriate exception") {
                        assertThat({ client.pullImage("some-image") }, throws<ImagePullFailedException>(withMessage("Pulling image 'some-image' failed: Something went wrong.")))
                    }
                }
            }

            on("when the image already exists locally") {
                whenever(processRunner.runAndCaptureOutput(listOf("docker", "images", "-q", "some-image"))).thenReturn(ProcessOutput(0, "abc123"))

                val image = client.pullImage("some-image")

                it("does not call the Docker CLI to pull the image again") {
                    verify(processRunner, never()).runAndCaptureOutput(pullCommand)
                }

                it("returns the Docker image") {
                    assertThat(image, equalTo(DockerImage("some-image")))
                }
            }

            on("when checking if the image has already been pulled fails") {
                whenever(processRunner.runAndCaptureOutput(listOf("docker", "images", "-q", "some-image"))).thenReturn(ProcessOutput(1, "Something went wrong.\n"))

                it("throws an appropriate exception") {
                    assertThat({ client.pullImage("some-image") }, throws<ImagePullFailedException>(withMessage("Checking if image 'some-image' has already been pulled failed: Something went wrong.")))
                }
            }
        }

        describe("checking if Docker is available") {
            given("running 'docker --version' succeeds") {
                beforeEachTest {
                    whenever(processRunner.runAndCaptureOutput(listOf("docker", "--version"))).doReturn(ProcessOutput(0, "Some output"))
                }

                it("returns true") {
                    assertThat(client.checkIfDockerIsAvailable(), equalTo(true))
                }
            }

            given("running 'docker --version' fails") {
                beforeEachTest {
                    whenever(processRunner.runAndCaptureOutput(listOf("docker", "--version"))).doReturn(ProcessOutput(1, "Some output"))
                }

                it("returns false") {
                    assertThat(client.checkIfDockerIsAvailable(), equalTo(false))
                }
            }

            given("the Docker executable cannot be found") {
                beforeEachTest {
                    whenever(processRunner.runAndCaptureOutput(listOf("docker", "--version"))).doThrow(ExecutableDoesNotExistException("docker", null))
                }

                it("returns false") {
                    assertThat(client.checkIfDockerIsAvailable(), equalTo(false))
                }
            }
        }
    }
})

private fun eventsCommandForContainer(containerId: String) = listOf("docker", "events", "--since=0",
    "--format", "{{.Status}}",
    "--filter", "container=$containerId",
    "--filter", "event=die",
    "--filter", "event=health_status")

private fun ProcessRunner.whenGettingEventsForContainerRespondWith(containerId: String, event: String) {
    val eventsCommand = eventsCommandForContainer(containerId)

    whenever(this.runAndProcessOutput<HealthStatus>(eq(eventsCommand), any())).then { invocation ->
        val processor: (String) -> OutputProcessing<HealthStatus> = invocation.getArgument(1)
        val processingResponse = processor.invoke(event)

        assertThat(processingResponse, isA<KillProcess<HealthStatus>>())

        KilledDuringProcessing((processingResponse as KillProcess<HealthStatus>).result)
    }
}

private val isUUID = Matcher(::validUUID)
private fun validUUID(value: String): Boolean {
    try {
        UUID.fromString(value)
        return true
    } catch (_: IllegalArgumentException) {
        return false
    }
}
