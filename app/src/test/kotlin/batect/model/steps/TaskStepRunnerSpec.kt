/*
   Copyright 2017 Charles Korn.

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

package batect.model.steps

import batect.config.BuildImage
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import batect.config.Container
import batect.docker.ContainerCreationFailedException
import batect.docker.ContainerDoesNotExistException
import batect.docker.ContainerHealthCheckException
import batect.docker.ContainerRemovalFailedException
import batect.docker.ContainerStartFailedException
import batect.docker.ContainerStopFailedException
import batect.docker.DockerClient
import batect.docker.DockerContainer
import batect.docker.DockerContainerRunResult
import batect.docker.DockerImage
import batect.docker.DockerImageBuildProgress
import batect.docker.DockerNetwork
import batect.docker.HealthStatus
import batect.docker.ImageBuildFailedException
import batect.docker.ImagePullFailedException
import batect.docker.NetworkCreationFailedException
import batect.docker.NetworkDeletionFailedException
import batect.model.events.ContainerBecameHealthyEvent
import batect.model.events.ContainerCreatedEvent
import batect.model.events.ContainerCreationFailedEvent
import batect.model.events.ContainerDidNotBecomeHealthyEvent
import batect.model.events.ContainerRemovalFailedEvent
import batect.model.events.ContainerRemovedEvent
import batect.model.events.ContainerStartFailedEvent
import batect.model.events.ContainerStartedEvent
import batect.model.events.ContainerStopFailedEvent
import batect.model.events.ContainerStoppedEvent
import batect.model.events.ImageBuildFailedEvent
import batect.model.events.ImageBuildProgressEvent
import batect.model.events.ImageBuiltEvent
import batect.model.events.ImagePullFailedEvent
import batect.model.events.ImagePulledEvent
import batect.model.events.RunningContainerExitedEvent
import batect.model.events.TaskEventSink
import batect.model.events.TaskNetworkCreatedEvent
import batect.model.events.TaskNetworkCreationFailedEvent
import batect.model.events.TaskNetworkDeletedEvent
import batect.model.events.TaskNetworkDeletionFailedEvent
import batect.model.events.TaskStartedEvent
import batect.testutils.imageSourceDoesNotMatter
import com.nhaarman.mockito_kotlin.eq
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskStepRunnerSpec : Spek({
    describe("a task step runner") {
        val eventSink = mock<TaskEventSink>()
        val dockerClient = mock<DockerClient>()
        val runner = TaskStepRunner(dockerClient)

        beforeEachTest {
            reset(eventSink)
            reset(dockerClient)
        }

        describe("running steps") {
            on("running a 'begin task' step") {
                val step = BeginTaskStep
                runner.run(step, eventSink)

                it("emits a 'task started' event") {
                    verify(eventSink).postEvent(TaskStartedEvent)
                }
            }

            describe("running a 'build image' step") {
                val container = Container("some-container", BuildImage("/some-build-dir"))
                val step = BuildImageStep("some-project-name", container)

                on("when building the image succeeds") {
                    val image = DockerImage("some-image")
                    val update1 = DockerImageBuildProgress(1, 2, "First step")
                    val update2 = DockerImageBuildProgress(2, 2, "Second step")

                    whenever(dockerClient.build(eq("some-project-name"), eq(container), any()))
                        .then { invocation ->
                            @Suppress("UNCHECKED_CAST")
                            val onStatusUpdate: (DockerImageBuildProgress) -> Unit = invocation.arguments[2] as (DockerImageBuildProgress) -> Unit

                            onStatusUpdate(update1)
                            onStatusUpdate(update2)

                            image
                        }

                    runner.run(step, eventSink)

                    it("emits a 'image build progress' event for each update received from Docker") {
                        verify(eventSink).postEvent(ImageBuildProgressEvent(container, update1))
                        verify(eventSink).postEvent(ImageBuildProgressEvent(container, update2))
                    }

                    it("emits a 'image built' event") {
                        verify(eventSink).postEvent(ImageBuiltEvent(container, image))
                    }
                }

                on("when building the image fails") {
                    whenever(dockerClient.build(eq("some-project-name"), eq(container), any())).thenThrow(ImageBuildFailedException("Something went wrong."))

                    runner.run(step, eventSink)

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

                    runner.run(step, eventSink)

                    it("emits a 'image pulled' event") {
                        verify(eventSink).postEvent(ImagePulledEvent(image))
                    }
                }

                on("when building the image fails") {
                    whenever(dockerClient.pullImage("some-image")).thenThrow(ImagePullFailedException("Pulling image 'some-image' failed. Output from Docker was: Something went wrong."))

                    runner.run(step, eventSink)

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

                    runner.run(step, eventSink)

                    it("emits a 'network created' event") {
                        verify(eventSink).postEvent(TaskNetworkCreatedEvent(network))
                    }
                }

                on("when creating the network fails") {
                    whenever(dockerClient.createNewBridgeNetwork()).doThrow(NetworkCreationFailedException("Something went wrong."))

                    runner.run(step, eventSink)

                    it("emits a 'network creation failed' event") {
                        verify(eventSink).postEvent(TaskNetworkCreationFailedEvent("Something went wrong."))
                    }
                }
            }

            describe("running a 'create container' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val command = "do-stuff"
                val image = DockerImage("some-image")
                val network = DockerNetwork("some-network")
                val step = CreateContainerStep(container, command, image, network)

                on("when creating the container succeeds") {
                    val dockerContainer = DockerContainer("some-id")
                    whenever(dockerClient.create(container, command, image, network)).doReturn(dockerContainer)

                    runner.run(step, eventSink)

                    it("emits a 'container created' event") {
                        verify(eventSink).postEvent(ContainerCreatedEvent(container, dockerContainer))
                    }
                }

                on("when creating the container fails") {
                    whenever(dockerClient.create(container, command, image, network)).doThrow(ContainerCreationFailedException("Something went wrong."))

                    runner.run(step, eventSink)

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

                runner.run(step, eventSink)

                it("emits a 'running container exited' event") {
                    verify(eventSink).postEvent(RunningContainerExitedEvent(container, 200))
                }
            }

            describe("running a 'start container' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val dockerContainer = DockerContainer("some-id")
                val step = StartContainerStep(container, dockerContainer)

                on("when starting the container succeeds") {
                    runner.run(step, eventSink)

                    it("starts the container") {
                        verify(dockerClient).start(dockerContainer)
                    }

                    it("emits a 'container started' event") {
                        verify(eventSink).postEvent(ContainerStartedEvent(container))
                    }
                }

                on("when starting the container fails") {
                    whenever(dockerClient.start(dockerContainer)).thenThrow(ContainerStartFailedException("some-id", "Something went wrong"))

                    runner.run(step, eventSink)

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
                    runner.run(step, eventSink)

                    it("stops the container") {
                        verify(dockerClient).stop(dockerContainer)
                    }

                    it("emits a 'container stopped' event") {
                        verify(eventSink).postEvent(ContainerStoppedEvent(container))
                    }
                }

                on("when stopping the container fails") {
                    whenever(dockerClient.stop(dockerContainer)).thenThrow(ContainerStopFailedException("some-id", "Something went wrong"))

                    runner.run(step, eventSink)

                    it("emits a 'container stop failed' event") {
                        verify(eventSink).postEvent(ContainerStopFailedEvent(container, "Something went wrong"))
                    }
                }
            }

            describe("running a 'clean up container' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val dockerContainer = DockerContainer("some-id")
                val step = CleanUpContainerStep(container, dockerContainer)

                on("when cleaning up the container succeeds") {
                    runner.run(step, eventSink)

                    it("removes the container") {
                        verify(dockerClient).forciblyRemove(dockerContainer)
                    }

                    it("emits a 'container removed' event") {
                        verify(eventSink).postEvent(ContainerRemovedEvent(container))
                    }
                }

                on("when cleaning up the container fails") {
                    whenever(dockerClient.forciblyRemove(dockerContainer)).thenThrow(ContainerRemovalFailedException("some-id", "Something went wrong"))

                    runner.run(step, eventSink)

                    it("emits a 'container removal failed' event") {
                        verify(eventSink).postEvent(ContainerRemovalFailedEvent(container, "Something went wrong"))
                    }
                }

                on("when the container does not exist") {
                    whenever(dockerClient.forciblyRemove(dockerContainer)).thenThrow(ContainerDoesNotExistException("Some message"))

                    runner.run(step, eventSink)

                    it("emits a 'container removed' event") {
                        verify(eventSink).postEvent(ContainerRemovedEvent(container))
                    }
                }
            }

            describe("running a 'remove container' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val dockerContainer = DockerContainer("some-id")
                val step = RemoveContainerStep(container, dockerContainer)

                on("when removing the container succeeds") {
                    runner.run(step, eventSink)

                    it("removes the container") {
                        verify(dockerClient).remove(dockerContainer)
                    }

                    it("emits a 'container removed' event") {
                        verify(eventSink).postEvent(ContainerRemovedEvent(container))
                    }
                }

                on("when removing the container fails") {
                    whenever(dockerClient.remove(dockerContainer)).thenThrow(ContainerRemovalFailedException("some-id", "Something went wrong"))

                    runner.run(step, eventSink)

                    it("emits a 'container removal failed' event") {
                        verify(eventSink).postEvent(ContainerRemovalFailedEvent(container, "Something went wrong"))
                    }
                }

                on("when the container does not exist") {
                    whenever(dockerClient.remove(dockerContainer)).thenThrow(ContainerDoesNotExistException("Some message"))

                    runner.run(step, eventSink)

                    it("emits a 'container removed' event") {
                        verify(eventSink).postEvent(ContainerRemovedEvent(container))
                    }
                }
            }

            describe("running a 'wait for container to become healthy' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val dockerContainer = DockerContainer("some-id")
                val step = WaitForContainerToBecomeHealthyStep(container, dockerContainer)

                on("when the container has no health check") {
                    whenever(dockerClient.waitForHealthStatus(dockerContainer)).doReturn(HealthStatus.NoHealthCheck)

                    runner.run(step, eventSink)

                    it("emits a 'container became healthy' event") {
                        verify(eventSink).postEvent(ContainerBecameHealthyEvent(container))
                    }
                }

                on("when the container becomes healthy") {
                    whenever(dockerClient.waitForHealthStatus(dockerContainer)).doReturn(HealthStatus.BecameHealthy)

                    runner.run(step, eventSink)

                    it("emits a 'container became healthy' event") {
                        verify(eventSink).postEvent(ContainerBecameHealthyEvent(container))
                    }
                }

                on("when the container becomes unhealthy") {
                    whenever(dockerClient.waitForHealthStatus(dockerContainer)).doReturn(HealthStatus.BecameUnhealthy)

                    runner.run(step, eventSink)

                    it("emits a 'container did not become healthy' event") {
                        verify(eventSink).postEvent(ContainerDidNotBecomeHealthyEvent(container, "The configured health check did not report the container as healthy within the timeout period."))
                    }
                }

                on("when the container exits before reporting a health status") {
                    whenever(dockerClient.waitForHealthStatus(dockerContainer)).doReturn(HealthStatus.Exited)

                    runner.run(step, eventSink)

                    it("emits a 'container did not become healthy' event") {
                        verify(eventSink).postEvent(ContainerDidNotBecomeHealthyEvent(container, "The container exited before becoming healthy."))
                    }
                }

                on("when waiting for the container's health status fails") {
                    whenever(dockerClient.waitForHealthStatus(dockerContainer)).doThrow(ContainerHealthCheckException("Something went wrong"))

                    runner.run(step, eventSink)

                    it("emits a 'container did not become healthy' event") {
                        verify(eventSink).postEvent(ContainerDidNotBecomeHealthyEvent(container, "Waiting for the container's health status failed: Something went wrong"))
                    }
                }
            }

            describe("running a 'delete task network' step") {
                val network = DockerNetwork("some-network")
                val step = DeleteTaskNetworkStep(network)

                on("when deleting the network succeeds") {
                    runner.run(step, eventSink)

                    it("deletes the network") {
                        verify(dockerClient).deleteNetwork(network)
                    }

                    it("emits a 'network deleted' event") {
                        verify(eventSink).postEvent(TaskNetworkDeletedEvent)
                    }
                }

                on("when removing the container fails") {
                    whenever(dockerClient.deleteNetwork(network)).thenThrow(NetworkDeletionFailedException("some-network", "Something went wrong"))

                    runner.run(step, eventSink)

                    it("emits a 'network deletion failed' event") {
                        verify(eventSink).postEvent(TaskNetworkDeletionFailedEvent("Something went wrong"))
                    }
                }
            }

            on("running a 'display task failure' step") {
                val step = DisplayTaskFailureStep("Something went wrong.")
                runner.run(step, eventSink)

                it("does not emit any events") {
                    verify(eventSink, never()).postEvent(any())
                }
            }

            on("running a 'finish task' step") {
                val step = FinishTaskStep(123)
                runner.run(step, eventSink)

                it("does not emit any events") {
                    verify(eventSink, never()).postEvent(any())
                }
            }
        }
    }
})
