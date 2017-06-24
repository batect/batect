package decompose.docker

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.*
import decompose.config.Container
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.mockito.ArgumentMatchers.anyString
import java.util.*

object DockerClientSpec : Spek({
    describe("a Docker client") {
        val imageLabel = "the-image"
        val labeller = mock<DockerImageLabellingStrategy> {
            on { labelImage(anyString(), any()) } doReturn imageLabel
        }

        val processRunner = mock<ProcessRunner>()
        val creationCommandGenerator = mock<DockerContainerCreationCommandGenerator>()

        val client = DockerClient(labeller, processRunner, creationCommandGenerator)

        beforeEachTest {
            reset(processRunner)
            reset(creationCommandGenerator)
        }

        describe("building an image") {
            given("a container configuration") {
                val container = Container("the-container", "/path/to/build/dir")

                on("a successful build") {
                    val result = client.build("the-project", container)

                    it("builds the image") {
                        verify(processRunner).run(listOf("docker", "build", "--tag", imageLabel, container.buildDirectory))
                    }

                    it("returns the ID of the created image") {
                        assert.that(result.id, equalTo(imageLabel))
                    }
                }

                on("a failed build") {
                    whenever(processRunner.run(any())).thenReturn(1)

                    it("raises an appropriate exception") {
                        assert.that({ client.build("the-project", container) }, throws<ImageBuildFailedException>())
                    }
                }
            }
        }

        describe("creating a container") {
            given("a container configuration and a built image") {
                val container = Container("the-container", "/this/does/not/matter")
                val command = "doStuff"
                val image = DockerImage("the-image")
                val network = DockerNetwork("the-network")
                val commandLine = listOf("docker", "doStuff", "please")

                beforeEachTest {
                    whenever(creationCommandGenerator.createCommandLine(container, command, image, network)).thenReturn(commandLine)
                }

                on("a successful creation") {
                    val containerId = "abc123"
                    whenever(processRunner.runAndCaptureOutput(any())).thenReturn(ProcessOutput(0, containerId + "\n"))

                    val result = client.create(container, command, image, network)

                    it("creates the container") {
                        verify(processRunner).runAndCaptureOutput(commandLine)
                    }

                    it("returns the ID of the created container") {
                        assert.that(result.id, equalTo(containerId))
                    }
                }

                on("a failed creation") {
                    whenever(processRunner.runAndCaptureOutput(any())).thenReturn(ProcessOutput(1, "Something went wrong."))

                    it("raises an appropriate exception") {
                        assert.that({ client.create(container, command, image, network) }, throws<ContainerCreationFailedException>(withMessage("Creation of container 'the-container' failed. Output from Docker was: Something went wrong.")))
                    }
                }
            }
        }

        describe("running a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id", "the-container")

                on("running the container") {
                    whenever(processRunner.run(any())).thenReturn(123)

                    val result = client.run(container)

                    it("starts the container") {
                        verify(processRunner).run(listOf("docker", "start", "--attach", "--interactive", container.id))
                    }

                    it("returns the exit code from the container") {
                        assert.that(result.exitCode, equalTo(123))
                    }
                }
            }
        }

        describe("starting a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id", "the-container")

                on("starting that container") {
                    whenever(processRunner.runAndCaptureOutput(any())).thenReturn(ProcessOutput(0, ""))

                    client.start(container)

                    it("launches the Docker CLI to start the container") {
                        verify(processRunner).runAndCaptureOutput(listOf("docker", "start", container.id))
                    }
                }

                on("an unsuccessful start attempt") {
                    whenever(processRunner.runAndCaptureOutput(any())).thenReturn(ProcessOutput(1, "Something went wrong."))

                    it("raises an appropriate exception") {
                        assert.that({ client.start(container) }, throws<ContainerStartFailedException>(withMessage("Starting container 'the-container' failed. Output from Docker was: Something went wrong.")))
                    }
                }
            }
        }

        describe("stopping a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id", "the-container")

                on("stopping that container") {
                    whenever(processRunner.runAndCaptureOutput(any())).thenReturn(ProcessOutput(0, ""))

                    client.stop(container)

                    it("launches the Docker CLI to stop the container") {
                        verify(processRunner).runAndCaptureOutput(listOf("docker", "stop", container.id))
                    }
                }

                on("that container not existing") {
                    whenever(processRunner.runAndCaptureOutput(any())).thenReturn(ProcessOutput(1, "Error response from daemon: No such container: ${container.id}"))

                    it("raises an appropriate exception") {
                        assert.that({ client.stop(container) }, throws<ContainerDoesNotExistException>(withMessage("Stopping container 'the-container' failed because it does not exist. If it was started with '--rm', it may have already stopped and removed itself.")))
                    }
                }

                on("an unsuccessful stop attempt") {
                    whenever(processRunner.runAndCaptureOutput(any())).thenReturn(ProcessOutput(1, "Something went wrong."))

                    it("raises an appropriate exception") {
                        assert.that({ client.stop(container) }, throws<ContainerStopFailedException>(withMessage("Stopping container 'the-container' failed. Output from Docker was: Something went wrong.")))
                    }
                }
            }
        }

        describe("waiting for a container to report its health status") {
            given("a Docker container with no healthcheck") {
                val container = DockerContainer("the-container-id", "the-container")

                on("waiting for that container to become healthy") {
                    val expectedCommand = listOf("docker", "inspect", "--format", "{{json .Config.Healthcheck}}", container.id)
                    whenever(processRunner.runAndCaptureOutput(expectedCommand)).thenReturn(ProcessOutput(0, "null\n"))

                    val result = client.waitForHealthStatus(container)

                    it("reports that the container does not have a healthcheck") {
                        assert.that(result, equalTo(HealthStatus.NoHealthCheck))
                    }
                }
            }

            given("a Docker container with a healthcheck that passes") {
                val container = DockerContainer("the-container-id", "the-container")

                on("waiting for that container to become healthy") {
                    val hasHealthcheckCommand = listOf("docker", "inspect", "--format", "{{json .Config.Healthcheck}}", container.id)
                    whenever(processRunner.runAndCaptureOutput(hasHealthcheckCommand)).thenReturn(ProcessOutput(0, "{some healthcheck config}\n"))

                    processRunner.whenGettingEventsForContainerRespondWith(container.id, "health_status: healthy")

                    val result = client.waitForHealthStatus(container)

                    it("reports that the container became healthy") {
                        assert.that(result, equalTo(HealthStatus.BecameHealthy))
                    }
                }
            }

            given("a Docker container with a healthcheck that fails") {
                val container = DockerContainer("the-container-id", "the-container")

                on("waiting for that container to become healthy") {
                    val hasHealthcheckCommand = listOf("docker", "inspect", "--format", "{{json .Config.Healthcheck}}", container.id)
                    whenever(processRunner.runAndCaptureOutput(hasHealthcheckCommand)).thenReturn(ProcessOutput(0, "{some healthcheck config}\n"))

                    processRunner.whenGettingEventsForContainerRespondWith(container.id, "health_status: unhealthy")

                    val result = client.waitForHealthStatus(container)

                    it("reports that the container became unhealthy") {
                        assert.that(result, equalTo(HealthStatus.BecameUnhealthy))
                    }
                }
            }

            given("a Docker container with a healthcheck that exits before the healthcheck reports") {
                val container = DockerContainer("the-container-id", "the-container")

                on("waiting for that container to become healthy") {
                    val hasHealthcheckCommand = listOf("docker", "inspect", "--format", "{{json .Config.Healthcheck}}", container.id)
                    whenever(processRunner.runAndCaptureOutput(hasHealthcheckCommand)).thenReturn(ProcessOutput(0, "{some healthcheck config}\n"))

                    processRunner.whenGettingEventsForContainerRespondWith(container.id, "die")

                    val result = client.waitForHealthStatus(container)

                    it("reports that the container exited") {
                        assert.that(result, equalTo(HealthStatus.Exited))
                    }
                }
            }

            given("a Docker container with a healthcheck that causes the 'docker events' command to terminate early") {
                val container = DockerContainer("the-container-id", "the-container")

                on("waiting for that container to become healthy") {
                    val hasHealthcheckCommand = listOf("docker", "inspect", "--format", "{{json .Config.Healthcheck}}", container.id)
                    whenever(processRunner.runAndCaptureOutput(hasHealthcheckCommand)).thenReturn(ProcessOutput(0, "{some healthcheck config}\n"))

                    val command = eventsCommandForContainer(container.id)
                    whenever(processRunner.runAndProcessOutput<HealthStatus>(eq(command), any())).thenReturn(Exited(123))

                    it("throws an appropriate exception") {
                        assert.that({ client.waitForHealthStatus(container) }, throws<ContainerHealthCheckException>(withMessage("Event stream for container 'the-container' exited early with exit code 123.")))
                    }
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
                        assert.that(command.count(), equalTo(expectedCommandTemplate.size + 1))
                        assert.that(command.take(expectedCommandTemplate.size), equalTo(expectedCommandTemplate))
                        assert.that(command.last(), isUUID)
                    })
                }

                it("returns the ID of the created network") {
                    assert.that(result.id, equalTo("the-network-ID"))
                }
            }

            on("an unsuccessful creation") {
                whenever(processRunner.runAndCaptureOutput(any())).thenReturn(ProcessOutput(1, "Something went wrong.\n"))

                it("throws an appropriate exception") {
                    assert.that({ client.createNewBridgeNetwork() }, throws<NetworkCreationFailedException>(withMessage("Creation of network failed. Output from Docker was: Something went wrong.")))
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
                    assert.that({ client.deleteNetwork(network) }, throws<NetworkDeletionFailedException>(withMessage("Deletion of network 'abc123' failed. Output from Docker was: Something went wrong.")))
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

        assert.that(processingResponse, com.natpryce.hamkrest.isA<KillProcess<HealthStatus>>())

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
