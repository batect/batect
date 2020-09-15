/*
   Copyright 2017-2020 Charles Korn.

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

package batect.ui.fancy

import batect.config.Container
import batect.docker.DockerContainer
import batect.docker.DockerNetwork
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerRemovedEvent
import batect.execution.model.events.RunningContainerExitedEvent
import batect.execution.model.events.StepStartingEvent
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.steps.CleanupStep
import batect.execution.model.steps.DeleteTaskNetworkStep
import batect.execution.model.steps.RunContainerStep
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.ui.Console
import batect.ui.FailureErrorMessageFormatter
import batect.ui.text.Text
import batect.ui.text.TextRun
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration

object FancyEventLoggerSpec : Spek({
    describe("a fancy event logger") {
        val failureErrorMessageFormatter by createForEachTest { mock<FailureErrorMessageFormatter>() }
        val console by createForEachTest { mock<Console>() }
        val errorConsole by createForEachTest { mock<Console>() }
        val startupProgressDisplay by createForEachTest { mock<StartupProgressDisplay>() }
        val cleanupProgressDisplay by createForEachTest { mock<CleanupProgressDisplay>() }
        val taskContainer by createForEachTest { Container("task-container", imageSourceDoesNotMatter()) }

        val logger by createForEachTest {
            FancyEventLogger(failureErrorMessageFormatter, console, errorConsole, startupProgressDisplay, cleanupProgressDisplay, taskContainer, mock())
        }

        describe("when logging an event") {
            on("while the task is starting up") {
                val event = ContainerBecameHealthyEvent(Container("some-container", imageSourceDoesNotMatter()))
                beforeEachTest { logger.postEvent(event) }

                it("notifies the startup progress display of the event and then reprints it") {
                    inOrder(startupProgressDisplay) {
                        verify(startupProgressDisplay).onEventPosted(event)
                        verify(startupProgressDisplay).print(console)
                    }
                }

                it("notifies the cleanup progress display of the event") {
                    verify(cleanupProgressDisplay).onEventPosted(event)
                }

                it("does not print the cleanup progress display") {
                    verify(cleanupProgressDisplay, never()).print(console)
                }
            }

            describe("when event is a notification that a container is starting") {
                on("the event being a notification that the task container is starting") {
                    val dockerContainer = DockerContainer("some-id")
                    val event by createForEachTest { StepStartingEvent(RunContainerStep(taskContainer, dockerContainer)) }
                    beforeEachTest { logger.postEvent(event) }

                    it("notifies the startup progress display of the event, reprints it and then prints a blank line") {
                        inOrder(startupProgressDisplay, console) {
                            verify(startupProgressDisplay).onEventPosted(event)
                            verify(startupProgressDisplay).print(console)
                            verify(console).println()
                        }
                    }

                    it("does not print the cleanup progress display") {
                        verify(cleanupProgressDisplay, never()).print(console)
                    }
                }

                on("the event being a notification that another container is starting") {
                    val container = Container("some-container", imageSourceDoesNotMatter())
                    val dockerContainer = DockerContainer("some-id")
                    val event by createForEachTest { StepStartingEvent(RunContainerStep(container, dockerContainer)) }
                    beforeEachTest { logger.postEvent(event) }

                    it("notifies the startup progress display of the event, reprints it and then prints a blank line") {
                        inOrder(startupProgressDisplay) {
                            verify(startupProgressDisplay).onEventPosted(event)
                            verify(startupProgressDisplay).print(console)
                        }
                    }

                    it("does not print a blank line") {
                        verify(console, never()).println()
                    }

                    it("does not print the cleanup progress display") {
                        verify(cleanupProgressDisplay, never()).print(console)
                    }
                }
            }

            describe("the event that a container has exited") {
                given("the container is the task container") {
                    val event by createForEachTest { RunningContainerExitedEvent(taskContainer, 123) }

                    beforeEachTest {
                        logger.postEvent(StepStartingEvent(RunContainerStep(taskContainer, DockerContainer("some-id"))))
                        reset(startupProgressDisplay)
                        reset(cleanupProgressDisplay)

                        logger.postEvent(event)
                    }

                    it("does not reprint the startup progress display") {
                        verify(startupProgressDisplay, never()).print(any())
                    }

                    it("does not notify the startup progress display of the event") {
                        verify(startupProgressDisplay, never()).onEventPosted(event)
                    }

                    it("notifies the cleanup progress display of the event before printing it") {
                        inOrder(cleanupProgressDisplay, console) {
                            verify(cleanupProgressDisplay).onEventPosted(event)
                            verify(console).println()
                            verify(cleanupProgressDisplay).print(console)
                        }
                    }

                    it("does not attempt to clear the previous cleanup progress") {
                        verify(cleanupProgressDisplay, never()).clear(console)
                    }
                }

                given("the container is not the task container") {
                    val container by createForEachTest { Container("other-container", imageSourceDoesNotMatter()) }
                    val event by createForEachTest { RunningContainerExitedEvent(container, 123) }

                    beforeEachTest {
                        logger.postEvent(StepStartingEvent(RunContainerStep(container, DockerContainer("some-id"))))
                        reset(startupProgressDisplay)
                        reset(cleanupProgressDisplay)

                        logger.postEvent(event)
                    }

                    it("does not print the cleanup progress") {
                        verify(cleanupProgressDisplay, never()).print(console)
                    }
                }
            }

            on("after the task has finished") {
                val event = ContainerRemovedEvent(Container("some-container", imageSourceDoesNotMatter()))

                beforeEachTest {
                    logger.postEvent(StepStartingEvent(RunContainerStep(taskContainer, DockerContainer("some-id"))))
                    logger.postEvent(RunningContainerExitedEvent(taskContainer, 123))
                    reset(startupProgressDisplay)
                    reset(cleanupProgressDisplay)

                    logger.postEvent(event)
                }

                it("does not reprint the startup progress display") {
                    verify(startupProgressDisplay, never()).print(any())
                }

                it("does not notify the startup progress display of the event") {
                    verify(startupProgressDisplay, never()).onEventPosted(event)
                }

                it("notifies the cleanup progress display of the event before reprinting it") {
                    inOrder(cleanupProgressDisplay) {
                        verify(cleanupProgressDisplay).onEventPosted(event)
                        verify(cleanupProgressDisplay).print(console)
                    }
                }

                it("clears the previously displayed cleanup progress before reprinting it") {
                    inOrder(cleanupProgressDisplay) {
                        verify(cleanupProgressDisplay).clear(console)
                        verify(cleanupProgressDisplay).print(console)
                    }
                }
            }

            given("the event is a notification that a cleanup step is starting") {
                val cleanupStep = mock<CleanupStep>()
                val cleanupStepEvent = StepStartingEvent(cleanupStep)

                on("and clean up has not started yet") {
                    beforeEachTest { logger.postEvent(cleanupStepEvent) }

                    it("prints a blank line before printing the cleanup progress") {
                        inOrder(console, cleanupProgressDisplay) {
                            verify(console).println()
                            verify(cleanupProgressDisplay).print(console)
                        }
                    }

                    it("does not attempt to clear the existing cleanup progress") {
                        verify(cleanupProgressDisplay, never()).clear(console)
                    }

                    it("does not notify the startup progress display") {
                        verify(startupProgressDisplay, never()).onEventPosted(cleanupStepEvent)
                    }

                    it("does not reprint the startup progress display") {
                        verify(startupProgressDisplay, never()).print(console)
                    }
                }

                on("and clean up has already started") {
                    beforeEachTest {
                        val previousCleanupStep = mock<CleanupStep>()
                        logger.postEvent(StepStartingEvent(previousCleanupStep))
                        reset(cleanupProgressDisplay)

                        logger.postEvent(cleanupStepEvent)
                    }

                    it("clears the existing cleanup progress before reprinting the cleanup progress") {
                        inOrder(cleanupProgressDisplay) {
                            verify(cleanupProgressDisplay).clear(console)
                            verify(cleanupProgressDisplay).print(console)
                        }
                    }

                    it("does not notify the startup progress display") {
                        verify(startupProgressDisplay, never()).onEventPosted(cleanupStepEvent)
                    }

                    it("does not reprint the startup progress display") {
                        verify(startupProgressDisplay, never()).print(console)
                    }
                }
            }

            given("a cleanup step has started") {
                beforeEachTest {
                    val step = mock<CleanupStep>()
                    logger.postEvent(StepStartingEvent(step))
                    reset(startupProgressDisplay)
                    reset(cleanupProgressDisplay)
                }

                on("posting an event") {
                    val event = ContainerBecameHealthyEvent(Container("some-container", imageSourceDoesNotMatter()))
                    beforeEachTest { logger.postEvent(event) }

                    it("does not reprint the startup progress display") {
                        verify(startupProgressDisplay, never()).print(any())
                    }

                    it("does not notify the startup progress display of the event") {
                        verify(startupProgressDisplay, never()).onEventPosted(event)
                    }

                    it("notifies the cleanup progress display of the event before reprinting it") {
                        inOrder(cleanupProgressDisplay) {
                            verify(cleanupProgressDisplay).onEventPosted(event)
                            verify(cleanupProgressDisplay).print(console)
                        }
                    }

                    it("clears the previously displayed cleanup progress before reprinting it") {
                        inOrder(cleanupProgressDisplay) {
                            verify(cleanupProgressDisplay).clear(console)
                            verify(cleanupProgressDisplay).print(console)
                        }
                    }
                }
            }

            given("a 'task failed' event is posted") {
                val event by createForEachTest { mock<TaskFailedEvent>() }

                given("clean up has not started yet") {
                    beforeEachTest { whenever(failureErrorMessageFormatter.formatErrorMessage(event)).doReturn(TextRun("Something went wrong.")) }

                    on("posting the event") {
                        beforeEachTest { logger.postEvent(event) }

                        it("prints the message to the output") {
                            inOrder(console, errorConsole) {
                                verify(console).println()
                                verify(errorConsole).println(TextRun("Something went wrong."))
                            }
                        }

                        it("does not print the cleanup progress to the console") {
                            verify(cleanupProgressDisplay, never()).print(console)
                        }

                        it("does not notify the startup progress display") {
                            verify(startupProgressDisplay, never()).onEventPosted(event)
                        }

                        it("does not reprint the startup progress display") {
                            verify(startupProgressDisplay, never()).print(console)
                        }
                    }
                }

                given("clean up has already started") {
                    beforeEachTest {
                        val step = DeleteTaskNetworkStep(DockerNetwork("some-network-id"))
                        logger.postEvent(StepStartingEvent(step))
                        reset(errorConsole)
                        reset(console)
                        reset(cleanupProgressDisplay)

                        whenever(failureErrorMessageFormatter.formatErrorMessage(event)).doReturn(TextRun("Something went wrong for a second time."))
                    }

                    on("posting the event") {
                        beforeEachTest { logger.postEvent(event) }

                        it("prints the message to the output") {
                            verify(errorConsole).println(TextRun("Something went wrong for a second time."))
                        }

                        it("prints the cleanup progress to the console") {
                            verify(cleanupProgressDisplay).print(console)
                        }

                        it("clears the existing cleanup progress before printing the error message and reprinting the cleanup progress") {
                            inOrder(console, errorConsole, cleanupProgressDisplay) {
                                verify(cleanupProgressDisplay).clear(console)
                                verify(errorConsole).println(TextRun("Something went wrong for a second time."))
                                verify(console).println()
                                verify(cleanupProgressDisplay).print(console)
                            }
                        }
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
                inOrder(console, cleanupProgressDisplay) {
                    verify(cleanupProgressDisplay).clear(console)
                    verify(console).println(Text.white(Text.bold("some-task") + Text(" finished with exit code 234 in 2.5s.")))
                }
            }
        }

        on("when the task finishes with cleanup disabled") {
            val cleanupInstructions = TextRun("Some instructions")
            beforeEachTest { logger.onTaskFinishedWithCleanupDisabled(cleanupInstructions) }

            it("prints the cleanup instructions") {
                inOrder(errorConsole) {
                    verify(errorConsole).println()
                    verify(errorConsole).println(cleanupInstructions)
                }
            }
        }

        describe("when the task fails") {
            given("there are no cleanup instructions") {
                on("when logging that the task has failed") {
                    beforeEachTest { logger.onTaskFailed("some-task", TextRun()) }

                    it("prints a message to the output") {
                        inOrder(errorConsole) {
                            verify(errorConsole).println()
                            verify(errorConsole).println(Text.red(Text("The task ") + Text.bold("some-task") + Text(" failed. See above for details.")))
                        }
                    }
                }
            }

            given("there are some cleanup instructions") {
                on("when logging that the task has failed") {
                    beforeEachTest { logger.onTaskFailed("some-task", TextRun("Do this to clean up.")) }

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
