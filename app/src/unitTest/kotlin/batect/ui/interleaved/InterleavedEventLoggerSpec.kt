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

package batect.ui.interleaved

import batect.config.BuildImage
import batect.config.Container
import batect.config.PullImage
import batect.docker.DockerContainer
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.execution.RunOptions
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerCreationFailedEvent
import batect.execution.model.events.ContainerDidNotBecomeHealthyEvent
import batect.execution.model.events.ContainerRemovalFailedEvent
import batect.execution.model.events.ContainerRunFailedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.ContainerStopFailedEvent
import batect.execution.model.events.ExecutionFailedEvent
import batect.execution.model.events.ImageBuildFailedEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.ImagePullFailedEvent
import batect.execution.model.events.ImagePulledEvent
import batect.execution.model.events.StepStartingEvent
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.events.TaskNetworkCreationFailedEvent
import batect.execution.model.events.TaskNetworkDeletionFailedEvent
import batect.execution.model.events.TemporaryDirectoryDeletionFailedEvent
import batect.execution.model.events.TemporaryFileDeletionFailedEvent
import batect.execution.model.events.UserInterruptedExecutionEvent
import batect.execution.model.steps.BuildImageStep
import batect.execution.model.steps.CleanupStep
import batect.execution.model.steps.CreateContainerStep
import batect.execution.model.steps.CreateTaskNetworkStep
import batect.execution.model.steps.PullImageStep
import batect.execution.model.steps.RunContainerStep
import batect.os.Command
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.on
import batect.ui.FailureErrorMessageFormatter
import batect.ui.text.Text
import batect.ui.text.TextRun
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths
import java.time.Duration

object InterleavedEventLoggerSpec : Spek({
    describe("an interleaved event logger") {
        val container1And2ImageSource = BuildImage(Paths.get("/some-image-dir"))
        val container3ImageSource = BuildImage(Paths.get("/some-other-image-dir"))
        val container4And5ImageSource = PullImage("another-image")
        val taskContainerImageSource = PullImage("some-image")
        val taskContainer = Container("task-container", taskContainerImageSource)
        val container1 = Container("container-1", container1And2ImageSource)
        val container2 = Container("container-2", container1And2ImageSource)
        val container3 = Container("container-3", container3ImageSource)
        val container4 = Container("container-4", container4And5ImageSource)
        val container5 = Container("container-5", container4And5ImageSource)
        val containers = setOf(taskContainer, container1, container2, container3, container4, container5)

        val output by createForEachTest { mock<InterleavedOutput>() }
        val failureErrorMessageFormatter by createForEachTest { mock<FailureErrorMessageFormatter>() }
        val runOptions by createForEachTest { mock<RunOptions>() }
        val logger by createForEachTest { InterleavedEventLogger(taskContainer, containers, output, failureErrorMessageFormatter, runOptions) }

        describe("handling when events are posted") {
            on("when an 'image built' event is posted") {
                beforeEachTest {
                    val event = ImageBuiltEvent(container1And2ImageSource, DockerImage("abc-123"))
                    logger.postEvent(event)
                }

                it("prints a message to the output for each container that uses that built image") {
                    verify(output).printForContainer(container1, TextRun(Text.white("Image built.")))
                    verify(output).printForContainer(container2, TextRun(Text.white("Image built.")))
                }
            }

            on("when an 'image pulled' event is posted") {
                beforeEachTest {
                    val event = ImagePulledEvent(PullImage("another-image"), DockerImage("the-cool-image-id"))
                    logger.postEvent(event)
                }

                it("prints a message to the output for each container that uses that pulled image") {
                    verify(output).printForContainer(container4, Text.white(Text("Pulled ") + Text.bold("another-image") + Text(".")))
                    verify(output).printForContainer(container5, Text.white(Text("Pulled ") + Text.bold("another-image") + Text(".")))
                }
            }

            describe("when a 'container started' event is posted") {
                on("when the task container has started") {
                    beforeEachTest {
                        val event = ContainerStartedEvent(taskContainer)
                        logger.postEvent(event)
                    }

                    it("does not print a message to the output") {
                        verify(output, never()).printForContainer(any(), any())
                    }
                }

                on("when a dependency container has started") {
                    beforeEachTest {
                        val event = ContainerStartedEvent(container1)
                        logger.postEvent(event)
                    }

                    it("prints a message to the output") {
                        verify(output).printForContainer(container1, TextRun(Text.white("Container started.")))
                    }
                }
            }

            on("when a 'container became healthy' event is posted") {
                beforeEachTest {
                    val event = ContainerBecameHealthyEvent(container1)
                    logger.postEvent(event)
                }

                it("prints a message to the output") {
                    verify(output).printForContainer(container1, TextRun(Text.white("Container became healthy.")))
                }
            }

            describe("when a 'step starting' event is posted") {
                on("when a 'build image' step is starting") {
                    beforeEachTest {
                        val step = BuildImageStep(container1And2ImageSource, emptySet())
                        logger.postEvent(StepStartingEvent(step))
                    }

                    it("prints a message to the output for each container that uses that built image") {
                        verify(output).printForContainer(container1, TextRun(Text.white("Building image...")))
                        verify(output).printForContainer(container2, TextRun(Text.white("Building image...")))
                    }
                }

                on("when a 'pull image' step is starting") {
                    beforeEachTest {
                        val step = PullImageStep(container4And5ImageSource)
                        logger.postEvent(StepStartingEvent(step))
                    }

                    it("prints a message to the output for each container that used that pulled image") {
                        verify(output).printForContainer(container4, Text.white(Text("Pulling ") + Text.bold("another-image") + Text("...")))
                        verify(output).printForContainer(container5, Text.white(Text("Pulling ") + Text.bold("another-image") + Text("...")))
                    }
                }

                describe("when a 'run container' step is starting") {
                    on("and no 'create container' step has been seen") {
                        beforeEachTest {
                            val step = RunContainerStep(taskContainer, DockerContainer("not-important"))
                            logger.postEvent(StepStartingEvent(step))
                        }

                        it("prints a message to the output without mentioning a command") {
                            verify(output).printForContainer(taskContainer, TextRun(Text.white("Running...")))
                        }
                    }

                    describe("and a 'create container' step has been seen") {
                        on("and that step did not contain a command") {
                            beforeEachTest {
                                val createContainerStep = CreateContainerStep(taskContainer, null, null, emptyMap(), emptySet(), emptySet(), DockerImage("some-image"), DockerNetwork("some-network"))
                                val runContainerStep = RunContainerStep(taskContainer, DockerContainer("not-important"))

                                logger.postEvent(StepStartingEvent(createContainerStep))
                                logger.postEvent(StepStartingEvent(runContainerStep))
                            }

                            it("prints a message to the output without mentioning a command") {
                                verify(output).printForContainer(taskContainer, TextRun(Text.white("Running...")))
                            }
                        }

                        on("and that step contained a command") {
                            beforeEachTest {
                                val createContainerStep = CreateContainerStep(taskContainer, Command.parse("do-stuff.sh"), null, emptyMap(), emptySet(), emptySet(), DockerImage("some-image"), DockerNetwork("some-network"))
                                val runContainerStep = RunContainerStep(taskContainer, DockerContainer("not-important"))

                                logger.postEvent(StepStartingEvent(createContainerStep))
                                logger.postEvent(StepStartingEvent(runContainerStep))
                            }

                            it("prints a message to the output including the original command") {
                                verify(output).printForContainer(taskContainer, Text.white(Text("Running ") + Text.bold("do-stuff.sh") + Text("...")))
                            }
                        }
                    }
                }

                describe("when a cleanup step is starting") {
                    val cleanupStep = mock<CleanupStep>()

                    given("no cleanup steps have run before") {
                        on("that step starting") {
                            beforeEachTest { logger.postEvent(StepStartingEvent(cleanupStep)) }

                            it("prints that clean up has started") {
                                verify(output).printForTask(TextRun(Text.white("Cleaning up...")))
                            }
                        }
                    }

                    given("and a cleanup step has already been run") {
                        beforeEachTest {
                            val previousStep = mock<CleanupStep>()
                            logger.postEvent(StepStartingEvent(previousStep))
                        }

                        on("that step starting") {
                            beforeEachTest { logger.postEvent(StepStartingEvent(cleanupStep)) }

                            it("only prints one message to the output") {
                                verify(output, times(1)).printForTask(TextRun(Text.white("Cleaning up...")))
                            }
                        }
                    }
                }

                describe("when a 'task failed' event is posted") {
                    describe("when the event can be associated with a particular container") {
                        data class Scenario(val description: String, val event: TaskFailedEvent, val container: Container)

                        listOf(
                            Scenario("image pull failed", ImageBuildFailedEvent(container3ImageSource, "Couldn't pull the image."), container3),
                            Scenario("image build failed", ImagePullFailedEvent(taskContainerImageSource, "Couldn't build the image."), taskContainer),
                            Scenario("container creation failed", ContainerCreationFailedEvent(container1, "Couldn't create the container."), container1),
                            Scenario("container did not become healthy", ContainerDidNotBecomeHealthyEvent(container1, "Container did not become healthy."), container1),
                            Scenario("container run failed", ContainerRunFailedEvent(container1, "Couldn't run container."), container1),
                            Scenario("container stop failed", ContainerStopFailedEvent(container1, "Couldn't stop container."), container1),
                            Scenario("container removal failed", ContainerRemovalFailedEvent(container1, "Couldn't remove container."), container1)
                        ).forEach { (description, event, container) ->
                            on("when a '$description' event is posted") {
                                beforeEachTest {
                                    whenever(failureErrorMessageFormatter.formatErrorMessage(event, runOptions)).doReturn(TextRun("Something went wrong."))

                                    logger.postEvent(event)
                                }

                                it("prints the message to the output for that container") {
                                    verify(output).printForContainer(container, TextRun("Something went wrong."))
                                }
                            }
                        }

                        on("when a 'image pull failed' event is posted for an image shared by multiple containers") {
                            val event = ImagePullFailedEvent(container4And5ImageSource, "Couldn't pull the image.")

                            beforeEachTest {
                                whenever(failureErrorMessageFormatter.formatErrorMessage(event, runOptions)).doReturn(TextRun("Something went wrong."))

                                logger.postEvent(event)
                            }

                            it("prints the message to the output for the task") {
                                verify(output).printForTask(TextRun("Something went wrong."))
                            }
                        }

                        on("when a 'image build failed' event is posted for an image shared by multiple containers") {
                            val event = ImageBuildFailedEvent(container1And2ImageSource, "Couldn't pull the image.")

                            beforeEachTest {
                                whenever(failureErrorMessageFormatter.formatErrorMessage(event, runOptions)).doReturn(TextRun("Something went wrong."))

                                logger.postEvent(event)
                            }

                            it("prints the message to the output for the task") {
                                verify(output).printForTask(TextRun("Something went wrong."))
                            }
                        }
                    }

                    describe("when the event cannot be associated with a particular container") {
                        mapOf(
                            "execution failed" to ExecutionFailedEvent("Couldn't do the thing."),
                            "network creation failed" to TaskNetworkCreationFailedEvent("Couldn't create the network."),
                            "network deletion failed" to TaskNetworkDeletionFailedEvent("Couldn't delete the network."),
                            "temporary file deletion failed" to TemporaryFileDeletionFailedEvent(Paths.get("some-file"), "Couldn't delete the file."),
                            "temporary directory deletion failed" to TemporaryDirectoryDeletionFailedEvent(Paths.get("some-dir"), "Couldn't delete the directory."),
                            "user interrupted execution" to UserInterruptedExecutionEvent
                        ).forEach { (description, event) ->
                            on("when a '$description' event is posted") {
                                beforeEachTest {
                                    whenever(failureErrorMessageFormatter.formatErrorMessage(event, runOptions)).doReturn(TextRun("Something went wrong."))

                                    logger.postEvent(event)
                                }

                                it("prints the message to the output") {
                                    verify(output).printForTask(TextRun("Something went wrong."))
                                }
                            }
                        }
                    }
                }

                on("when another kind of step is starting") {
                    beforeEachTest {
                        val step = CreateTaskNetworkStep
                        logger.postEvent(StepStartingEvent(step))
                    }

                    it("does not print anything to the output") {
                        verifyZeroInteractions(output)
                    }
                }
            }
        }

        on("when the task starts") {
            beforeEachTest { logger.onTaskStarting("some-task") }

            it("prints a message to the output") {
                verify(output).printForTask(Text.white(Text("Running ") + Text.bold("some-task") + Text("...")))
            }
        }

        on("when the task finishes") {
            beforeEachTest { logger.onTaskFinished("some-task", 234, Duration.ofMillis(2500)) }

            it("prints a message to the output") {
                verify(output).printForTask(Text.white(Text.bold("some-task") + Text(" finished with exit code 234 in 2.5s.")))
            }
        }

        on("when the task finishes with cleanup disabled") {
            val cleanupInstructions = TextRun("Some instructions")
            beforeEachTest { logger.onTaskFinishedWithCleanupDisabled(cleanupInstructions) }

            it("prints the cleanup instructions") {
                inOrder(output) {
                    verify(output).printForTask(cleanupInstructions)
                }
            }
        }

        describe("when the task fails") {
            given("there are no cleanup instructions") {
                on("when logging that the task has failed") {
                    beforeEachTest { logger.onTaskFailed("some-task", TextRun()) }

                    it("prints a message to the output") {
                        inOrder(output) {
                            verify(output).printForTask(Text.red(Text("The task ") + Text.bold("some-task") + Text(" failed. See above for details.")))
                        }
                    }
                }
            }

            given("there are some cleanup instructions") {
                on("when logging that the task has failed") {
                    beforeEachTest { logger.onTaskFailed("some-task", TextRun("Do this to clean up.")) }

                    it("prints a message to the output, including the instructions") {
                        inOrder(output) {
                            verify(output).printForTask(TextRun("Do this to clean up.") + Text("\n\n") + Text.red(Text("The task ") + Text.bold("some-task") + Text(" failed. See above for details.")))
                        }
                    }
                }
            }
        }
    }
})
