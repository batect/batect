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

package batect.execution.model.steps

import batect.config.BuildImage
import batect.config.Container
import batect.config.HealthCheckConfig
import batect.config.PortMapping
import batect.config.VolumeMount
import batect.docker.ContainerCreationFailedException
import batect.docker.ContainerDoesNotExistException
import batect.docker.ContainerHealthCheckException
import batect.docker.ContainerRemovalFailedException
import batect.docker.ContainerStartFailedException
import batect.docker.ContainerStopFailedException
import batect.docker.DockerClient
import batect.docker.DockerContainer
import batect.docker.DockerContainerCreationRequest
import batect.docker.DockerContainerCreationRequestFactory
import batect.docker.DockerContainerRunResult
import batect.docker.DockerHealthCheckResult
import batect.docker.DockerImage
import batect.docker.DockerImageBuildProgress
import batect.docker.DockerNetwork
import batect.docker.HealthStatus
import batect.docker.ImageBuildFailedException
import batect.docker.ImagePullFailedException
import batect.docker.NetworkCreationFailedException
import batect.docker.NetworkDeletionFailedException
import batect.docker.UserAndGroup
import batect.logging.Logger
import batect.logging.Severity
import batect.execution.BehaviourAfterFailure
import batect.execution.RunAsCurrentUserConfiguration
import batect.execution.RunAsCurrentUserConfigurationProvider
import batect.execution.RunOptions
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerCreationFailedEvent
import batect.execution.model.events.ContainerDidNotBecomeHealthyEvent
import batect.execution.model.events.ContainerRemovalFailedEvent
import batect.execution.model.events.ContainerRemovedEvent
import batect.execution.model.events.ContainerStartFailedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.ContainerStopFailedEvent
import batect.execution.model.events.ContainerStoppedEvent
import batect.execution.model.events.ImageBuildFailedEvent
import batect.execution.model.events.ImageBuildProgressEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.ImagePullFailedEvent
import batect.execution.model.events.ImagePulledEvent
import batect.execution.model.events.RunningContainerExitedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.events.TaskNetworkCreationFailedEvent
import batect.execution.model.events.TaskNetworkDeletedEvent
import batect.execution.model.events.TaskNetworkDeletionFailedEvent
import batect.execution.model.events.TemporaryFileDeletedEvent
import batect.execution.model.events.TemporaryFileDeletionFailedEvent
import batect.os.Command
import batect.os.ProxyEnvironmentVariablesProvider
import batect.testutils.InMemoryLogSink
import batect.testutils.createForEachTest
import batect.testutils.hasMessage
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.withAdditionalData
import batect.testutils.withLogMessage
import batect.testutils.withSeverity
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.nio.file.Files

object TaskStepRunnerSpec : Spek({
    describe("a task step runner") {
        val eventSink = mock<TaskEventSink>()
        val dockerClient = mock<DockerClient>()
        val creationRequestFactory = mock<DockerContainerCreationRequestFactory>()

        val proxyVariables = mapOf("SOME_PROXY_CONFIG" to "some_proxy")
        val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider> {
            on { getProxyEnvironmentVariables(emptySet()) } doReturn proxyVariables
        }

        val runAsCurrentUserConfiguration = RunAsCurrentUserConfiguration(
            setOf(VolumeMount("/tmp/local-path", "/tmp/remote-path", "rw")),
            UserAndGroup(456, 789)
        )

        val runAsCurrentUserConfigurationProvider = mock<RunAsCurrentUserConfigurationProvider> {
            on { generateConfiguration(any(), any()) } doReturn runAsCurrentUserConfiguration
        }

        val logSink = InMemoryLogSink()
        val runOptions = RunOptions("some-task", emptyList(), 123, BehaviourAfterFailure.Cleanup, true)

        val logger = Logger("some.source", logSink)
        val runner = TaskStepRunner(dockerClient, proxyEnvironmentVariablesProvider, creationRequestFactory, runAsCurrentUserConfigurationProvider, logger)

        beforeEachTest {
            reset(eventSink)
            reset(dockerClient)
            reset(creationRequestFactory)
        }

        describe("running steps") {
            on("running any step") {
                val step = mock<TaskStep> {
                    on { toString() } doReturn "The step description"
                }

                runner.run(step, eventSink, runOptions)

                it("logs that the step is starting") {
                    assertThat(logSink, hasMessage(
                        withSeverity(Severity.Info) and
                            withLogMessage("Running step.") and
                            withAdditionalData("step", "The step description")))
                }

                it("logs that the step has finished") {
                    assertThat(logSink, hasMessage(
                        withSeverity(Severity.Info) and
                            withLogMessage("Step completed.") and
                            withAdditionalData("step", "The step description")))
                }
            }

            describe("running a 'build image' step") {
                val container = Container("some-container", BuildImage("/some-build-dir"))
                val step = BuildImageStep("some-project-name", container)

                describe("when building the image succeeds") {
                    on("and propagating proxy-related environment variables is enabled") {
                        val image = DockerImage("some-image")
                        val update1 = DockerImageBuildProgress(1, 2, "First step")
                        val update2 = DockerImageBuildProgress(2, 2, "Second step")

                        whenever(dockerClient.build(eq("some-project-name"), eq(container), any(), any()))
                            .then { invocation ->
                                @Suppress("UNCHECKED_CAST")
                                val onStatusUpdate: (DockerImageBuildProgress) -> Unit = invocation.arguments[3] as (DockerImageBuildProgress) -> Unit

                                onStatusUpdate(update1)
                                onStatusUpdate(update2)

                                image
                            }

                        runner.run(step, eventSink, runOptions)

                        it("passes the proxy-related environment variables as image build arguments") {
                            verify(dockerClient).build(any(), any(), eq(proxyVariables), any())
                        }

                        it("emits a 'image build progress' event for each update received from Docker") {
                            verify(eventSink).postEvent(ImageBuildProgressEvent(container, update1))
                            verify(eventSink).postEvent(ImageBuildProgressEvent(container, update2))
                        }

                        it("emits a 'image built' event") {
                            verify(eventSink).postEvent(ImageBuiltEvent(container, image))
                        }
                    }

                    on("and propagating proxy-related environment variables is disabled") {
                        val image = DockerImage("some-image")
                        whenever(dockerClient.build(eq("some-project-name"), eq(container), any(), any())).thenReturn(image)

                        val runOptionsWithProxyEnvironmentVariablePropagationDisabled = RunOptions("some-task", emptyList(), 123, BehaviourAfterFailure.Cleanup, false)
                        runner.run(step, eventSink, runOptionsWithProxyEnvironmentVariablePropagationDisabled)

                        it("does not pass the proxy-related environment variables as image build arguments") {
                            verify(dockerClient).build(any(), any(), eq(emptyMap()), any())
                        }

                        it("emits a 'image built' event") {
                            verify(eventSink).postEvent(ImageBuiltEvent(container, image))
                        }
                    }
                }

                on("when building the image fails") {
                    whenever(dockerClient.build(eq("some-project-name"), eq(container), any(), any())).thenThrow(ImageBuildFailedException("Something went wrong."))

                    runner.run(step, eventSink, runOptions)

                    it("emits a 'image build failed' event") {
                        verify(eventSink).postEvent(ImageBuildFailedEvent(container, "Image build failed. Output from Docker was: Something went wrong."))
                    }
                }
            }

            describe("running a 'pull image' step") {
                val step = PullImageStep("some-image")

                on("when pulling the image succeeds") {
                    val image = DockerImage("some-image")
                    whenever(dockerClient.pullImage("some-image")).thenReturn(image)

                    runner.run(step, eventSink, runOptions)

                    it("emits a 'image pulled' event") {
                        verify(eventSink).postEvent(ImagePulledEvent(image))
                    }
                }

                on("when building the image fails") {
                    whenever(dockerClient.pullImage("some-image")).thenThrow(ImagePullFailedException("Pulling image 'some-image' failed. Output from Docker was: Something went wrong."))

                    runner.run(step, eventSink, runOptions)

                    it("emits a 'image pull failed' event") {
                        verify(eventSink).postEvent(ImagePullFailedEvent("some-image", "Pulling image 'some-image' failed. Output from Docker was: Something went wrong."))
                    }
                }
            }

            describe("running a 'create task network' step") {
                val step = CreateTaskNetworkStep

                on("when creating the network succeeds") {
                    val network = DockerNetwork("some-network")
                    whenever(dockerClient.createNewBridgeNetwork()).doReturn(network)

                    runner.run(step, eventSink, runOptions)

                    it("emits a 'network created' event") {
                        verify(eventSink).postEvent(TaskNetworkCreatedEvent(network))
                    }
                }

                on("when creating the network fails") {
                    whenever(dockerClient.createNewBridgeNetwork()).doThrow(NetworkCreationFailedException("Something went wrong."))

                    runner.run(step, eventSink, runOptions)

                    it("emits a 'network creation failed' event") {
                        verify(eventSink).postEvent(TaskNetworkCreationFailedEvent("Something went wrong."))
                    }
                }
            }

            describe("running a 'create container' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val otherContainer = Container("some-other-container", imageSourceDoesNotMatter())
                val command = Command.parse("do-stuff")
                val additionalEnvironmentVariables = mapOf("SOME_VAR" to "some value")
                val additionalPortMappings = setOf(PortMapping(123, 456))
                val image = DockerImage("some-image")
                val network = DockerNetwork("some-network")

                val step = CreateContainerStep(container, command, additionalEnvironmentVariables, additionalPortMappings, setOf(container, otherContainer), image, network)
                val request = DockerContainerCreationRequest(image, network, command!!.parsedCommand, "some-container", "some-container", emptyMap(), "/work-dir", emptySet(), emptySet(), HealthCheckConfig(), null)

                beforeEachTest {
                    whenever(creationRequestFactory.create(
                        container,
                        image,
                        network,
                        command,
                        additionalEnvironmentVariables,
                        runAsCurrentUserConfiguration.volumeMounts,
                        additionalPortMappings,
                        runOptions.propagateProxyEnvironmentVariables,
                        runAsCurrentUserConfiguration.userAndGroup,
                        step.allContainersInNetwork
                    )).doReturn(request)
                }

                on("when creating the container succeeds") {
                    val dockerContainer = DockerContainer("some-id")
                    whenever(dockerClient.create(request)).doReturn(dockerContainer)

                    runner.run(step, eventSink, runOptions)

                    it("creates the container with the provided configuration") {
                        verify(dockerClient).create(request)
                    }

                    it("emits a 'container created' event") {
                        verify(eventSink).postEvent(ContainerCreatedEvent(container, dockerContainer))
                    }
                }

                on("when creating the container fails") {
                    whenever(dockerClient.create(request)).doThrow(ContainerCreationFailedException("Something went wrong."))

                    runner.run(step, eventSink, runOptions)

                    it("emits a 'container creation failed' event") {
                        verify(eventSink).postEvent(ContainerCreationFailedEvent(container, "Something went wrong."))
                    }
                }
            }

            on("running a 'run container' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val dockerContainer = DockerContainer("some-id")
                val step = RunContainerStep(container, dockerContainer)

                whenever(dockerClient.run(dockerContainer)).doReturn(DockerContainerRunResult(200))

                runner.run(step, eventSink, runOptions)

                it("emits a 'running container exited' event") {
                    verify(eventSink).postEvent(RunningContainerExitedEvent(container, 200))
                }
            }

            describe("running a 'start container' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val dockerContainer = DockerContainer("some-id")
                val step = StartContainerStep(container, dockerContainer)

                on("when starting the container succeeds") {
                    runner.run(step, eventSink, runOptions)

                    it("starts the container") {
                        verify(dockerClient).start(dockerContainer)
                    }

                    it("emits a 'container started' event") {
                        verify(eventSink).postEvent(ContainerStartedEvent(container))
                    }
                }

                on("when starting the container fails") {
                    whenever(dockerClient.start(dockerContainer)).thenThrow(ContainerStartFailedException("some-id", "Something went wrong"))

                    runner.run(step, eventSink, runOptions)

                    it("emits a 'container start failed' event") {
                        verify(eventSink).postEvent(ContainerStartFailedEvent(container, "Something went wrong"))
                    }
                }
            }

            describe("running a 'stop container' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val dockerContainer = DockerContainer("some-id")
                val step = StopContainerStep(container, dockerContainer)

                on("when stopping the container succeeds") {
                    runner.run(step, eventSink, runOptions)

                    it("stops the container") {
                        verify(dockerClient).stop(dockerContainer)
                    }

                    it("emits a 'container stopped' event") {
                        verify(eventSink).postEvent(ContainerStoppedEvent(container))
                    }
                }

                on("when stopping the container fails") {
                    whenever(dockerClient.stop(dockerContainer)).thenThrow(ContainerStopFailedException("some-id", "Something went wrong"))

                    runner.run(step, eventSink, runOptions)

                    it("emits a 'container stop failed' event") {
                        verify(eventSink).postEvent(ContainerStopFailedEvent(container, "Something went wrong"))
                    }
                }
            }

            describe("running a 'remove container' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val dockerContainer = DockerContainer("some-id")
                val step = RemoveContainerStep(container, dockerContainer)

                on("when removing the container succeeds") {
                    runner.run(step, eventSink, runOptions)

                    it("removes the container") {
                        verify(dockerClient).remove(dockerContainer)
                    }

                    it("emits a 'container removed' event") {
                        verify(eventSink).postEvent(ContainerRemovedEvent(container))
                    }
                }

                on("when removing the container fails") {
                    whenever(dockerClient.remove(dockerContainer)).thenThrow(ContainerRemovalFailedException("some-id", "Something went wrong"))

                    runner.run(step, eventSink, runOptions)

                    it("emits a 'container removal failed' event") {
                        verify(eventSink).postEvent(ContainerRemovalFailedEvent(container, "Something went wrong"))
                    }
                }

                on("when the container does not exist") {
                    whenever(dockerClient.remove(dockerContainer)).thenThrow(ContainerDoesNotExistException("Some message"))

                    runner.run(step, eventSink, runOptions)

                    it("emits a 'container removed' event") {
                        verify(eventSink).postEvent(ContainerRemovedEvent(container))
                    }
                }
            }

            describe("running a 'delete temporary file' step") {
                val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
                val filePath by createForEachTest { fileSystem.getPath("/temp-file") }

                val step by createForEachTest { DeleteTemporaryFileStep(filePath) }

                on("when deleting the file succeeds") {
                    Files.write(filePath, listOf("test file contents"))

                    runner.run(step, eventSink, runOptions)

                    it("emits a 'temporary file deleted' event") {
                        verify(eventSink).postEvent(TemporaryFileDeletedEvent(filePath))
                    }
                }

                on("when deleting the file fails") {
                    runner.run(step, eventSink, runOptions)

                    it("emits a 'temporary file deletion failed' event") {
                        verify(eventSink).postEvent(TemporaryFileDeletionFailedEvent(filePath, "java.nio.file.NoSuchFileException: /temp-file"))
                    }
                }
            }

            describe("running a 'wait for container to become healthy' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val dockerContainer = DockerContainer("some-id")
                val step = WaitForContainerToBecomeHealthyStep(container, dockerContainer)

                on("when the container has no health check") {
                    whenever(dockerClient.waitForHealthStatus(dockerContainer)).doReturn(HealthStatus.NoHealthCheck)

                    runner.run(step, eventSink, runOptions)

                    it("emits a 'container became healthy' event") {
                        verify(eventSink).postEvent(ContainerBecameHealthyEvent(container))
                    }
                }

                on("when the container becomes healthy") {
                    whenever(dockerClient.waitForHealthStatus(dockerContainer)).doReturn(HealthStatus.BecameHealthy)

                    runner.run(step, eventSink, runOptions)

                    it("emits a 'container became healthy' event") {
                        verify(eventSink).postEvent(ContainerBecameHealthyEvent(container))
                    }
                }

                describe("when the container becomes unhealthy") {
                    beforeEachTest {
                        whenever(dockerClient.waitForHealthStatus(dockerContainer)).doReturn(HealthStatus.BecameUnhealthy)
                    }

                    describe("when the last health check returned a non-zero exit code") {
                        on("and produced no output") {
                            whenever(dockerClient.getLastHealthCheckResult(dockerContainer)).doReturn(DockerHealthCheckResult(2, ""))

                            runner.run(step, eventSink, runOptions)

                            it("emits a 'container did not become healthy' event with details of the last health check") {
                                verify(eventSink).postEvent(ContainerDidNotBecomeHealthyEvent(container,
                                    "The configured health check did not indicate that the container was healthy within the timeout period. The last health check exited with code 2 but did not produce any output."))
                            }
                        }

                        on("and produced some output") {
                            whenever(dockerClient.getLastHealthCheckResult(dockerContainer)).doReturn(DockerHealthCheckResult(2, "Something's not ready yet.\n"))

                            runner.run(step, eventSink, runOptions)

                            it("emits a 'container did not become healthy' event with details of the last health check") {
                                verify(eventSink).postEvent(ContainerDidNotBecomeHealthyEvent(container,
                                    "The configured health check did not indicate that the container was healthy within the timeout period. The last health check exited with code 2 and output: Something's not ready yet."))
                            }
                        }
                    }

                    on("when the last health check returned a zero exit code") {
                        whenever(dockerClient.getLastHealthCheckResult(dockerContainer)).doReturn(DockerHealthCheckResult(0, ""))

                        runner.run(step, eventSink, runOptions)

                        it("emits a 'container did not become healthy' event with an explanation of the race condition") {
                            verify(eventSink).postEvent(ContainerDidNotBecomeHealthyEvent(container,
                                "The configured health check did not indicate that the container was healthy within the timeout period. The most recent health check exited with code 0, which usually indicates that the container became healthy just after the timeout period expired."))
                        }
                    }

                    on("when getting the last health check result throws an exception") {
                        whenever(dockerClient.getLastHealthCheckResult(dockerContainer)).doThrow(ContainerHealthCheckException("Something went wrong."))

                        runner.run(step, eventSink, runOptions)

                        it("emits a 'container did not become healthy' event with details of the last health check") {
                            verify(eventSink).postEvent(ContainerDidNotBecomeHealthyEvent(container,
                                "Waiting for the container's health status failed: Something went wrong."))
                        }
                    }
                }

                on("when the container exits before reporting a health status") {
                    whenever(dockerClient.waitForHealthStatus(dockerContainer)).doReturn(HealthStatus.Exited)

                    runner.run(step, eventSink, runOptions)

                    it("emits a 'container did not become healthy' event") {
                        verify(eventSink).postEvent(ContainerDidNotBecomeHealthyEvent(container, "The container exited before becoming healthy."))
                    }
                }

                on("when waiting for the container's health status fails") {
                    whenever(dockerClient.waitForHealthStatus(dockerContainer)).doThrow(ContainerHealthCheckException("Something went wrong"))

                    runner.run(step, eventSink, runOptions)

                    it("emits a 'container did not become healthy' event") {
                        verify(eventSink).postEvent(ContainerDidNotBecomeHealthyEvent(container, "Waiting for the container's health status failed: Something went wrong"))
                    }
                }
            }

            describe("running a 'delete task network' step") {
                val network = DockerNetwork("some-network")
                val step = DeleteTaskNetworkStep(network)

                on("when deleting the network succeeds") {
                    runner.run(step, eventSink, runOptions)

                    it("deletes the network") {
                        verify(dockerClient).deleteNetwork(network)
                    }

                    it("emits a 'network deleted' event") {
                        verify(eventSink).postEvent(TaskNetworkDeletedEvent)
                    }
                }

                on("when removing the container fails") {
                    whenever(dockerClient.deleteNetwork(network)).thenThrow(NetworkDeletionFailedException("some-network", "Something went wrong"))

                    runner.run(step, eventSink, runOptions)

                    it("emits a 'network deletion failed' event") {
                        verify(eventSink).postEvent(TaskNetworkDeletionFailedEvent("Something went wrong"))
                    }
                }
            }
        }
    }
})
