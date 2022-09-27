/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.ui.interleaved

import batect.config.BuildImage
import batect.config.Container
import batect.config.LiteralValue
import batect.config.PullImage
import batect.config.SetupCommand
import batect.docker.DockerContainer
import batect.dockerclient.ContainerReference
import batect.dockerclient.ImageReference
import batect.execution.PostTaskManualCleanup
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerCreationFailedEvent
import batect.execution.model.events.ContainerDidNotBecomeHealthyEvent
import batect.execution.model.events.ContainerRemovalFailedEvent
import batect.execution.model.events.ContainerRunFailedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.ContainerStopFailedEvent
import batect.execution.model.events.ContainerStoppedEvent
import batect.execution.model.events.ExecutionFailedEvent
import batect.execution.model.events.ImageBuildFailedEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.ImagePullFailedEvent
import batect.execution.model.events.ImagePulledEvent
import batect.execution.model.events.RunningSetupCommandEvent
import batect.execution.model.events.SetupCommandsCompletedEvent
import batect.execution.model.events.StepStartingEvent
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.events.TaskNetworkCreationFailedEvent
import batect.execution.model.events.TaskNetworkDeletionFailedEvent
import batect.execution.model.events.UserInterruptedExecutionEvent
import batect.execution.model.steps.BuildImageStep
import batect.execution.model.steps.CleanupStep
import batect.execution.model.steps.PullImageStep
import batect.execution.model.steps.RunContainerStep
import batect.execution.model.steps.TaskStep
import batect.os.Command
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.on
import batect.testutils.pathResolutionContextDoesNotMatter
import batect.ui.FailureErrorMessageFormatter
import batect.ui.text.Text
import batect.ui.text.TextRun
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration

object InterleavedEventLoggerSpec : Spek({
    describe("an interleaved event logger") {
        val container1ImageSource = BuildImage(LiteralValue("/some-image-dir"), pathResolutionContextDoesNotMatter())
        val container2And3ImageSource = PullImage("another-image")
        val taskContainerImageSource = PullImage("some-image")
        val taskContainer = Container("task-container", taskContainerImageSource)
        val setupCommands = listOf("a", "b", "c", "d").map { SetupCommand(Command.parse(it)) }
        val container1 = Container("container-1", container1ImageSource, setupCommands = setupCommands)
        val container2 = Container("container-4", container2And3ImageSource)
        val container3 = Container("container-5", container2And3ImageSource)
        val containers = setOf(taskContainer, container1, container2, container3)

        val output by createForEachTest { mock<InterleavedOutput>() }
        val failureErrorMessageFormatter by createForEachTest { mock<FailureErrorMessageFormatter>() }
        val logger by createForEachTest { InterleavedEventLogger(taskContainer, containers, output, failureErrorMessageFormatter) }

        describe("handling when events are posted") {
            on("when an 'image built' event is posted") {
                beforeEachTest {
                    val event = ImageBuiltEvent(container1, ImageReference("abc-123"))
                    logger.postEvent(event)
                }

                it("prints a message to the output") {
                    verify(output).printForContainer(container1, Text.white("Batect | ") + Text("Image built."))
                }
            }

            on("when an 'image pulled' event is posted") {
                beforeEachTest {
                    val event = ImagePulledEvent(PullImage("another-image"), ImageReference("the-cool-image-id"))
                    logger.postEvent(event)
                }

                it("prints a message to the output for each container that uses that pulled image") {
                    verify(output).printForContainer(container2, Text.white("Batect | ") + Text("Pulled ") + Text.bold("another-image") + Text("."))
                    verify(output).printForContainer(container3, Text.white("Batect | ") + Text("Pulled ") + Text.bold("another-image") + Text("."))
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
                        verify(output).printForContainer(container1, Text.white("Batect | ") + Text("Container started."))
                    }
                }
            }

            on("when a 'container became healthy' event is posted") {
                beforeEachTest {
                    val event = ContainerBecameHealthyEvent(container1)
                    logger.postEvent(event)
                }

                it("prints a message to the output") {
                    verify(output).printForContainer(container1, Text.white("Batect | ") + Text("Container became healthy."))
                }
            }

            describe("when a 'container stopped' event is posted") {
                beforeEachTest {
                    val event = ContainerStoppedEvent(container1)
                    logger.postEvent(event)
                }

                it("prints a message to the output") {
                    verify(output).printForContainer(container1, Text.white("Batect | ") + Text("Container stopped."))
                }
            }

            on("when a 'running setup command' event is posted") {
                beforeEachTest {
                    val event = RunningSetupCommandEvent(container1, SetupCommand(Command.parse("do-the-thing")), 2)
                    logger.postEvent(event)
                }

                it("prints a message to the output") {
                    verify(output).printForContainer(container1, Text.white("Batect | ") + Text("Running setup command ") + Text.bold("do-the-thing") + Text(" (3 of 4)..."))
                }
            }

            on("when a 'setup commands complete' event is posted") {
                beforeEachTest {
                    val event = SetupCommandsCompletedEvent(container1)
                    logger.postEvent(event)
                }

                it("prints a message to the output") {
                    verify(output).printForContainer(container1, Text.white("Batect | ") + Text("Container has completed all setup commands."))
                }
            }

            describe("when a 'step starting' event is posted") {
                on("when a 'build image' step is starting") {
                    beforeEachTest {
                        val step = BuildImageStep(container1)
                        logger.postEvent(StepStartingEvent(step))
                    }

                    it("prints a message to the output") {
                        verify(output).printForContainer(container1, Text.white("Batect | ") + Text("Building image..."))
                    }
                }

                on("when a 'pull image' step is starting") {
                    beforeEachTest {
                        val step = PullImageStep(container2And3ImageSource)
                        logger.postEvent(StepStartingEvent(step))
                    }

                    it("prints a message to the output for each container that used that pulled image") {
                        verify(output).printForContainer(container2, Text.white("Batect | ") + Text("Pulling ") + Text.bold("another-image") + Text("..."))
                        verify(output).printForContainer(container3, Text.white("Batect | ") + Text("Pulling ") + Text.bold("another-image") + Text("..."))
                    }
                }

                describe("when a 'run container' step is starting") {
                    on("and no 'create container' step has been seen") {
                        beforeEachTest {
                            val step = RunContainerStep(taskContainer, DockerContainer(ContainerReference("not-important"), "some-name"))
                            logger.postEvent(StepStartingEvent(step))
                        }

                        it("prints a message to the output without mentioning a command") {
                            verify(output).printForContainer(taskContainer, Text.white("Batect | ") + Text("Running..."))
                        }
                    }

                    describe("and a 'create container' step has been seen") {
                        on("and that step did not contain a command") {
                            val containerWithoutCommand = taskContainer.copy(command = null)

                            beforeEachTest {
                                logger.postEvent(StepStartingEvent(RunContainerStep(containerWithoutCommand, DockerContainer(ContainerReference("not-important"), "some-name"))))
                            }

                            it("prints a message to the output without mentioning a command") {
                                verify(output).printForContainer(containerWithoutCommand, Text.white("Batect | ") + Text("Running..."))
                            }
                        }

                        on("and that step contained a command") {
                            val containerWithCommand = taskContainer.copy(command = Command.parse("do-stuff.sh"))

                            beforeEachTest {
                                logger.postEvent(StepStartingEvent(RunContainerStep(containerWithCommand, DockerContainer(ContainerReference("not-important"), "some-name"))))
                            }

                            it("prints a message to the output including the original command") {
                                verify(output).printForContainer(containerWithCommand, Text.white("Batect | ") + Text("Running ") + Text.bold("do-stuff.sh") + Text("..."))
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
                                verify(output).printForTask(Text.white("Batect | ") + Text("Cleaning up..."))
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
                                verify(output, times(1)).printForTask(Text.white("Batect | ") + Text("Cleaning up..."))
                            }
                        }
                    }
                }

                describe("when a 'task failed' event is posted") {
                    describe("when the event can be associated with a particular container") {
                        data class Scenario(val description: String, val event: TaskFailedEvent, val container: Container)

                        listOf(
                            Scenario("image pull failed", ImageBuildFailedEvent(container1, "Couldn't pull the image."), container1),
                            Scenario("image build failed", ImagePullFailedEvent(taskContainerImageSource, "Couldn't build the image."), taskContainer),
                            Scenario("container creation failed", ContainerCreationFailedEvent(container1, "Couldn't create the container."), container1),
                            Scenario("container did not become healthy", ContainerDidNotBecomeHealthyEvent(container1, "Container did not become healthy."), container1),
                            Scenario("container run failed", ContainerRunFailedEvent(container1, "Couldn't run container."), container1),
                            Scenario("container stop failed", ContainerStopFailedEvent(container1, "Couldn't stop container."), container1),
                            Scenario("container removal failed", ContainerRemovalFailedEvent(container1, "Couldn't remove container."), container1)
                        ).forEach { (description, event, container) ->
                            on("when a '$description' event is posted") {
                                beforeEachTest {
                                    whenever(failureErrorMessageFormatter.formatErrorMessage(event)).doReturn(TextRun("Something went wrong.\nAnother line."))

                                    logger.postEvent(event)
                                }

                                it("prints the message to the output for that container") {
                                    verify(output).printErrorForContainer(
                                        container,
                                        Text.white("Batect | ") + TextRun("Something went wrong.") + Text("\n") +
                                            Text.white("Batect | ") + TextRun("Another line.")
                                    )
                                }
                            }
                        }

                        on("when a 'image pull failed' event is posted for an image shared by multiple containers") {
                            val event = ImagePullFailedEvent(container2And3ImageSource, "Couldn't pull the image.")

                            beforeEachTest {
                                whenever(failureErrorMessageFormatter.formatErrorMessage(event)).doReturn(TextRun("Something went wrong.\nAnother line."))

                                logger.postEvent(event)
                            }

                            it("prints the message to the output for the task") {
                                verify(output).printErrorForTask(
                                    Text.white("Batect | ") + TextRun("Something went wrong.") + Text("\n") +
                                        Text.white("Batect | ") + TextRun("Another line.")
                                )
                            }
                        }
                    }

                    describe("when the event cannot be associated with a particular container") {
                        mapOf(
                            "execution failed" to ExecutionFailedEvent("Couldn't do the thing."),
                            "network creation failed" to TaskNetworkCreationFailedEvent("Couldn't create the network."),
                            "network deletion failed" to TaskNetworkDeletionFailedEvent("Couldn't delete the network."),
                            "user interrupted execution" to UserInterruptedExecutionEvent
                        ).forEach { (description, event) ->
                            on("when a '$description' event is posted") {
                                beforeEachTest {
                                    whenever(failureErrorMessageFormatter.formatErrorMessage(event)).doReturn(TextRun("Something went wrong.\nAnother line."))

                                    logger.postEvent(event)
                                }

                                it("prints the message to the output") {
                                    verify(output).printErrorForTask(
                                        Text.white("Batect | ") + TextRun("Something went wrong.") + Text("\n") +
                                            Text.white("Batect | ") + TextRun("Another line.")
                                    )
                                }
                            }
                        }
                    }
                }

                on("when another kind of step is starting") {
                    beforeEachTest {
                        val step = mock<TaskStep>()
                        logger.postEvent(StepStartingEvent(step))
                    }

                    it("does not print anything to the output") {
                        verifyNoInteractions(output)
                    }
                }
            }
        }

        on("when the task starts") {
            beforeEachTest { logger.onTaskStarting("some-task") }

            it("prints a message to the output") {
                verify(output).printForTask(Text.white("Batect | ") + Text("Running ") + Text.bold("some-task") + Text("..."))
            }
        }

        on("when the task finishes") {
            given("the task finished with a non-zero exit code") {
                beforeEachTest { logger.onTaskFinished("some-task", 234, Duration.ofMillis(2500)) }

                it("prints a message to the output with the exit code in red") {
                    verify(output).printForTask(Text.white("Batect | ") + Text.bold("some-task") + Text(" finished with exit code ") + Text.red(Text.bold("234")) + Text(" in 2.5s."))
                }
            }

            given("the task finished with a zero exit code") {
                beforeEachTest { logger.onTaskFinished("some-task", 0, Duration.ofMillis(2500)) }

                it("prints a message to the output with the exit code in green") {
                    verify(output).printForTask(Text.white("Batect | ") + Text.bold("some-task") + Text(" finished with exit code ") + Text.green(Text.bold("0")) + Text(" in 2.5s."))
                }
            }
        }

        on("when the task finishes with cleanup disabled") {
            given("there are no cleanup instructions") {
                beforeEachTest {
                    val postTaskCleanup = PostTaskManualCleanup.Required.DueToTaskSuccessWithCleanupDisabled(listOf("some thing to clean up"))
                    whenever(failureErrorMessageFormatter.formatManualCleanupMessage(postTaskCleanup, emptySet())).doReturn(null)

                    logger.onTaskFinishedWithCleanupDisabled(postTaskCleanup, emptySet())
                }

                it("prints nothing") {
                    verifyNoInteractions(output)
                }
            }

            given("there are cleanup instructions") {
                beforeEachTest {
                    val postTaskCleanup = PostTaskManualCleanup.Required.DueToTaskSuccessWithCleanupDisabled(listOf("some thing to clean up"))
                    whenever(failureErrorMessageFormatter.formatManualCleanupMessage(postTaskCleanup, emptySet())).doReturn(TextRun("Some instructions\nAnother line"))

                    logger.onTaskFinishedWithCleanupDisabled(postTaskCleanup, emptySet())
                }

                it("prints the cleanup instructions, prefixing each line of the instructions") {
                    inOrder(output) {
                        verify(output).printErrorForTask(
                            Text.white("Batect | ") + Text("Some instructions") + Text("\n") +
                                Text.white("Batect | ") + Text("Another line")
                        )
                    }
                }
            }
        }

        describe("when the task fails") {
            val postTaskCleanup = PostTaskManualCleanup.Required.DueToCleanupFailure(listOf("some thing to clean up"))

            given("there are no cleanup instructions") {
                beforeEachTest {
                    whenever(failureErrorMessageFormatter.formatManualCleanupMessage(postTaskCleanup, emptySet())).doReturn(null)
                }

                on("when logging that the task has failed") {
                    beforeEachTest { logger.onTaskFailed("some-task", postTaskCleanup, emptySet()) }

                    it("prints a message to the output") {
                        inOrder(output) {
                            verify(output).printErrorForTask(Text.white("Batect | ") + Text.red(Text("The task ") + Text.bold("some-task") + Text(" failed. See above for details.")))
                        }
                    }
                }
            }

            given("there are some cleanup instructions") {
                beforeEachTest {
                    whenever(failureErrorMessageFormatter.formatManualCleanupMessage(postTaskCleanup, emptySet())).doReturn(TextRun("Do this to clean up.\nAnother line"))
                }

                on("when logging that the task has failed") {
                    beforeEachTest { logger.onTaskFailed("some-task", postTaskCleanup, emptySet()) }

                    it("prints a message to the output, including the instructions, prefixing each line of the instructions") {
                        inOrder(output) {
                            verify(output).printErrorForTask(
                                Text.white("Batect | ") + TextRun("Do this to clean up.") + Text("\n") +
                                    Text.white("Batect | ") + TextRun("Another line") + Text("\n") +
                                    Text.white("Batect | ") + Text("\n") +
                                    Text.white("Batect | ") + Text.red(Text("The task ") + Text.bold("some-task") + Text(" failed. See above for details."))
                            )
                        }
                    }
                }
            }
        }
    }
})
