/*
    Copyright 2017-2021 Charles Korn.

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

package batect.ui.simple

import batect.config.BuildImage
import batect.config.Container
import batect.config.LiteralValue
import batect.config.PullImage
import batect.config.SetupCommand
import batect.docker.DockerContainer
import batect.docker.DockerImage
import batect.execution.PostTaskManualCleanup
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.ImagePulledEvent
import batect.execution.model.events.RunningSetupCommandEvent
import batect.execution.model.events.SetupCommandsCompletedEvent
import batect.execution.model.events.StepStartingEvent
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.steps.BuildImageStep
import batect.execution.model.steps.CleanupStep
import batect.execution.model.steps.PullImageStep
import batect.execution.model.steps.RunContainerStep
import batect.execution.model.steps.TaskStep
import batect.os.Command
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.testutils.pathResolutionContextDoesNotMatter
import batect.ui.Console
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

object SimpleEventLoggerSpec : Spek({
    describe("a simple event logger") {
        val taskContainer = Container("task-container", PullImage("some-image"))
        val otherContainer = Container("other-container", BuildImage(LiteralValue("/some-image-dir"), pathResolutionContextDoesNotMatter()))
        val containers = setOf(taskContainer, otherContainer)

        val failureErrorMessageFormatter by createForEachTest { mock<FailureErrorMessageFormatter>() }
        val console by createForEachTest { mock<Console>() }
        val errorConsole by createForEachTest { mock<Console>() }

        val logger by createForEachTest { SimpleEventLogger(containers, taskContainer, failureErrorMessageFormatter, console, errorConsole, mock()) }
        val setupCommands = listOf("a", "b", "c", "d").map { SetupCommand(Command.parse(it)) }
        val container = Container("the-cool-container", imageSourceDoesNotMatter(), setupCommands = setupCommands)

        describe("handling when events are posted") {
            on("when a 'task failed' event is posted") {
                beforeEachTest {
                    val event = mock<TaskFailedEvent>()
                    whenever(failureErrorMessageFormatter.formatErrorMessage(event)).doReturn(TextRun("Something went wrong."))

                    logger.postEvent(event)
                }

                it("prints the message to the console") {
                    verify(errorConsole).println()
                    verify(errorConsole).println(TextRun("Something went wrong."))
                }
            }

            on("when an 'image built' event is posted") {
                beforeEachTest {
                    val event = ImageBuiltEvent(otherContainer, DockerImage("abc-123"))
                    logger.postEvent(event)
                }

                it("prints a message to the output") {
                    verify(console).println(Text.white(Text("Built ") + Text.bold("other-container") + Text(".")))
                }
            }

            on("when an 'image pulled' event is posted") {
                beforeEachTest {
                    val event = ImagePulledEvent(PullImage("the-cool-image:1.2.3"), DockerImage("the-cool-image-id"))
                    logger.postEvent(event)
                }

                it("prints a message to the output") {
                    verify(console).println(Text.white(Text("Pulled ") + Text.bold("the-cool-image:1.2.3") + Text(".")))
                }
            }

            describe("when a 'container started' event is posted") {
                on("when the task container has started") {
                    beforeEachTest {
                        val event = ContainerStartedEvent(taskContainer)
                        logger.postEvent(event)
                    }

                    it("does not print a message to the output") {
                        verify(console, never()).println(any<TextRun>())
                    }
                }

                on("when a dependency container has started") {
                    beforeEachTest {
                        val event = ContainerStartedEvent(container)
                        logger.postEvent(event)
                    }

                    it("prints a message to the output") {
                        verify(console).println(Text.white(Text("Started ") + Text.bold("the-cool-container") + Text(".")))
                    }
                }
            }

            on("when a 'container became healthy' event is posted") {
                on("when the task container has become healthy") {
                    beforeEachTest {
                        val event = ContainerBecameHealthyEvent(taskContainer)
                        logger.postEvent(event)
                    }

                    it("does not print a message to the output") {
                        verify(console, never()).println(any<TextRun>())
                    }
                }

                on("when a dependency container has become healthy") {
                    beforeEachTest {
                        val event = ContainerBecameHealthyEvent(container)
                        logger.postEvent(event)
                    }

                    it("prints a message to the output") {
                        verify(console).println(Text.white(Text.bold("the-cool-container") + Text(" has become healthy.")))
                    }
                }
            }

            on("when a 'running setup command' event is posted") {
                on("when the task container is running a setup command") {
                    beforeEachTest {
                        val event = RunningSetupCommandEvent(taskContainer, SetupCommand(Command.parse("do-the-thing")), 2)
                        logger.postEvent(event)
                    }

                    it("does not print a message to the output") {
                        verify(console, never()).println(any<TextRun>())
                    }
                }

                on("when a dependency container is running a setup command") {
                    beforeEachTest {
                        val event = RunningSetupCommandEvent(container, SetupCommand(Command.parse("do-the-thing")), 2)
                        logger.postEvent(event)
                    }

                    it("prints a message to the output") {
                        verify(console).println(Text.white(Text("Running setup command ") + Text.bold("do-the-thing") + Text(" (3 of 4) in ") + Text.bold("the-cool-container") + Text("...")))
                    }
                }
            }

            on("when a 'setup commands complete' event is posted") {
                on("when the task container has completed all setup commands") {
                    beforeEachTest {
                        val event = SetupCommandsCompletedEvent(taskContainer)
                        logger.postEvent(event)
                    }

                    it("does not print a message to the output") {
                        verify(console, never()).println(any<TextRun>())
                    }
                }

                on("when a dependency container has completed all setup commands") {
                    beforeEachTest {
                        val event = SetupCommandsCompletedEvent(container)
                        logger.postEvent(event)
                    }

                    it("prints a message to the output") {
                        verify(console).println(Text.white(Text.bold("the-cool-container") + Text(" has completed all setup commands.")))
                    }
                }
            }

            describe("when a 'step starting' event is posted") {
                on("when a 'build image' step is starting") {
                    beforeEachTest {
                        val step = BuildImageStep(otherContainer)
                        logger.postEvent(StepStartingEvent(step))
                    }

                    it("prints a message to the output") {
                        verify(console).println(Text.white(Text("Building ") + Text.bold("other-container") + Text("...")))
                    }
                }

                on("when a 'pull image' step is starting") {
                    beforeEachTest {
                        val step = PullImageStep(PullImage("some-image:1.2.3"))
                        logger.postEvent(StepStartingEvent(step))
                    }

                    it("prints a message to the output") {
                        verify(console).println(Text.white(Text("Pulling ") + Text.bold("some-image:1.2.3") + Text("...")))
                    }
                }

                describe("when a 'run container' step is starting") {
                    describe("when the step will run the task container") {
                        on("and that container does not have an explicit command") {
                            val taskContainerWithoutCommand = taskContainer.copy(command = null)
                            val loggerForContainerWithoutCommand by createForEachTest { SimpleEventLogger(setOf(taskContainerWithoutCommand), taskContainerWithoutCommand, failureErrorMessageFormatter, console, errorConsole, mock()) }

                            beforeEachTest {
                                loggerForContainerWithoutCommand.postEvent(StepStartingEvent(RunContainerStep(taskContainerWithoutCommand, DockerContainer("not-important"))))
                            }

                            it("prints a message to the output without mentioning a command") {
                                verify(console).println(Text.white(Text("Running ") + Text.bold("task-container") + Text("...")))
                            }
                        }

                        on("and that step contained a command") {
                            val taskContainerWithCommand = taskContainer.copy(command = Command.parse("do-stuff.sh"))
                            val loggerForContainerWithCommand by createForEachTest { SimpleEventLogger(setOf(taskContainerWithCommand), taskContainerWithCommand, failureErrorMessageFormatter, console, errorConsole, mock()) }

                            beforeEachTest {
                                loggerForContainerWithCommand.postEvent(StepStartingEvent(RunContainerStep(taskContainerWithCommand, DockerContainer("not-important"))))
                            }

                            it("prints a message to the output including the original command") {
                                verify(console).println(Text.white(Text("Running ") + Text.bold("do-stuff.sh") + Text(" in ") + Text.bold("task-container") + Text("...")))
                            }
                        }
                    }

                    on("when the step will run a dependency container") {
                        beforeEachTest {
                            val step = RunContainerStep(container, DockerContainer("not-important"))
                            logger.postEvent(StepStartingEvent(step))
                        }

                        it("prints a message to the output") {
                            verify(console).println(Text.white(Text("Starting ") + Text.bold("the-cool-container") + Text("...")))
                        }
                    }
                }

                describe("when a cleanup step is starting") {
                    val cleanupStep = mock<CleanupStep>()

                    given("no cleanup steps have run before") {
                        on("that step starting") {
                            beforeEachTest { logger.postEvent(StepStartingEvent(cleanupStep)) }

                            it("prints a blank line before then printing that clean up has started") {
                                inOrder(console) {
                                    verify(console).println()
                                    verify(console).println(Text.white("Cleaning up..."))
                                }
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
                                verify(console, times(1)).println(Text.white("Cleaning up..."))
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
                        verifyNoInteractions(console)
                    }
                }
            }
        }

        on("when the task starts") {
            beforeEachTest { logger.onTaskStarting("some-task") }

            it("prints a message to the output") {
                verify(console).println(Text.white(Text("Running ") + Text.bold("some-task") + Text("...")))
            }
        }

        on("when the task finishes") {
            beforeEachTest { logger.onTaskFinished("some-task", 234, Duration.ofMillis(2500)) }

            it("prints a message to the output") {
                verify(console).println(Text.white(Text.bold("some-task") + Text(" finished with exit code 234 in 2.5s.")))
            }
        }

        on("when the task finishes with cleanup disabled") {
            val cleanupInstructions = TextRun("Some instructions")

            beforeEachTest {
                val postTaskCleanup = PostTaskManualCleanup.Required.DueToTaskSuccessWithCleanupDisabled(listOf("some thing to clean up"))
                whenever(failureErrorMessageFormatter.formatManualCleanupMessage(postTaskCleanup, emptySet())).doReturn(cleanupInstructions)

                logger.onTaskFinishedWithCleanupDisabled(postTaskCleanup, emptySet())
            }

            it("prints the cleanup instructions") {
                inOrder(errorConsole) {
                    verify(errorConsole).println()
                    verify(errorConsole).println(cleanupInstructions)
                }
            }
        }

        describe("when the task fails") {
            val postTaskCleanup = PostTaskManualCleanup.Required.DueToCleanupFailure(listOf("some thing to clean up"))

            given("there are no cleanup instructions") {
                beforeEachTest {
                    whenever(failureErrorMessageFormatter.formatManualCleanupMessage(postTaskCleanup, emptySet())).doReturn(TextRun())
                }

                on("when logging that the task has failed") {
                    beforeEachTest { logger.onTaskFailed("some-task", postTaskCleanup, emptySet()) }

                    it("prints a message to the output") {
                        inOrder(errorConsole) {
                            verify(errorConsole).println()
                            verify(errorConsole).println(Text.red(Text("The task ") + Text.bold("some-task") + Text(" failed. See above for details.")))
                        }
                    }
                }
            }

            given("there are some cleanup instructions") {
                beforeEachTest {
                    whenever(failureErrorMessageFormatter.formatManualCleanupMessage(postTaskCleanup, emptySet())).doReturn(TextRun("Do this to clean up."))
                }

                on("when logging that the task has failed") {
                    beforeEachTest { logger.onTaskFailed("some-task", postTaskCleanup, emptySet()) }

                    it("prints a message to the output, including the instructions") {
                        inOrder(errorConsole) {
                            verify(errorConsole).println()
                            verify(errorConsole).println(TextRun("Do this to clean up."))
                            verify(errorConsole).println()
                            verify(errorConsole).println(Text.red(Text("The task ") + Text.bold("some-task") + Text(" failed. See above for details.")))
                        }
                    }
                }
            }
        }
    }
})
