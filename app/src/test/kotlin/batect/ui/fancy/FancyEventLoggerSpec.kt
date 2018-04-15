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

package batect.ui.fancy

import batect.config.Container
import batect.docker.DockerContainer
import batect.docker.DockerNetwork
import batect.model.RunOptions
import batect.model.events.ContainerBecameHealthyEvent
import batect.model.events.ContainerRemovedEvent
import batect.model.events.RunningContainerExitedEvent
import batect.model.events.TaskFailedEvent
import batect.model.steps.CleanUpContainerStep
import batect.model.steps.CreateTaskNetworkStep
import batect.model.steps.DeleteTaskNetworkStep
import batect.model.steps.RemoveContainerStep
import batect.model.steps.RunContainerStep
import batect.testutils.createForEachTest
import batect.testutils.imageSourceDoesNotMatter
import batect.ui.Console
import batect.ui.ConsoleColor
import batect.ui.ConsolePrintStatements
import batect.ui.FailureErrorMessageFormatter
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object FancyEventLoggerSpec : Spek({
    describe("a fancy event logger") {
        val failureErrorMessageFormatter by createForEachTest { mock<FailureErrorMessageFormatter>() }
        val runOptions by createForEachTest { mock<RunOptions>() }

        val whiteConsole by createForEachTest { mock<Console>() }
        val console by createForEachTest {
            mock<Console> {
                on { withColor(eq(ConsoleColor.White), any()) } doAnswer {
                    val printStatements = it.getArgument<ConsolePrintStatements>(1)
                    printStatements(whiteConsole)
                }
            }
        }

        val redErrorConsole by createForEachTest { mock<Console>() }
        val errorConsole by createForEachTest {
            mock<Console> {
                on { withColor(eq(ConsoleColor.Red), any()) } doAnswer {
                    val printStatements = it.getArgument<ConsolePrintStatements>(1)
                    printStatements(redErrorConsole)
                }
            }
        }

        val startupProgressDisplay by createForEachTest { mock<StartupProgressDisplay>() }
        val cleanupProgressDisplay by createForEachTest { mock<CleanupProgressDisplay>() }

        val logger by createForEachTest {
            FancyEventLogger(failureErrorMessageFormatter, runOptions, console, errorConsole, startupProgressDisplay, cleanupProgressDisplay)
        }

        describe("when logging that a step is starting") {
            val container = Container("task-container", imageSourceDoesNotMatter())
            val dockerContainer = DockerContainer("some-id")

            on("while the task is starting up") {
                val step = CreateTaskNetworkStep
                logger.onStartingTaskStep(step)

                it("notifies the startup progress display of the step and then reprints it") {
                    inOrder(startupProgressDisplay) {
                        verify(startupProgressDisplay).onStepStarting(step)
                        verify(startupProgressDisplay).print(console)
                    }
                }

                it("does not print the cleanup progress display") {
                    verify(cleanupProgressDisplay, never()).print(console)
                }
            }

            on("and that step is to run the task container") {
                val step = RunContainerStep(container, dockerContainer)
                logger.onStartingTaskStep(step)

                it("notifies the startup progress display of the step, reprints it and then prints a blank line") {
                    inOrder(startupProgressDisplay, console) {
                        verify(startupProgressDisplay).onStepStarting(step)
                        verify(startupProgressDisplay).print(console)
                        verify(console).println()
                    }
                }

                it("does not print the cleanup progress display") {
                    verify(cleanupProgressDisplay, never()).print(console)
                }
            }

            mapOf(
                "remove container" to RemoveContainerStep(Container("some-container", imageSourceDoesNotMatter()), DockerContainer("some-id")),
                "clean up container" to CleanUpContainerStep(Container("some-container", imageSourceDoesNotMatter()), DockerContainer("some-id")),
                "remove network" to DeleteTaskNetworkStep(DockerNetwork("some-network-id"))
            ).forEach { (description, step) ->
                describe("and that step is a '$description' step") {
                    on("and clean up has not started yet") {
                        logger.onStartingTaskStep(step)

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
                            verify(startupProgressDisplay, never()).onStepStarting(step)
                        }

                        it("does not reprint the startup progress display") {
                            verify(startupProgressDisplay, never()).print(console)
                        }
                    }

                    on("and clean up has already started") {
                        logger.onStartingTaskStep(DeleteTaskNetworkStep(DockerNetwork("some-network-id")))
                        reset(cleanupProgressDisplay)

                        logger.onStartingTaskStep(step)

                        it("clears the existing cleanup progress before reprinting the cleanup progress") {
                            inOrder(console, redErrorConsole, cleanupProgressDisplay) {
                                verify(cleanupProgressDisplay).clear(console)
                                verify(cleanupProgressDisplay).print(console)
                            }
                        }

                        it("does not notify the startup progress display") {
                            verify(startupProgressDisplay, never()).onStepStarting(step)
                        }

                        it("does not reprint the startup progress display") {
                            verify(startupProgressDisplay, never()).print(console)
                        }
                    }
                }
            }
        }

        describe("when logging an event") {
            on("while the task is starting up") {
                val event = ContainerBecameHealthyEvent(Container("some-container", imageSourceDoesNotMatter()))
                logger.postEvent(event)

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

            on("when the task finishes") {
                val container = Container("task-container", imageSourceDoesNotMatter())
                logger.onStartingTaskStep(RunContainerStep(container, DockerContainer("some-id")))
                reset(startupProgressDisplay)
                reset(cleanupProgressDisplay)

                val event = RunningContainerExitedEvent(container, 123)
                logger.postEvent(event)

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

                it("does attempt to clear the previous cleanup progress") {
                    verify(cleanupProgressDisplay, never()).clear(console)
                }
            }

            on("after the task has finished") {
                val container = Container("task-container", imageSourceDoesNotMatter())
                logger.onStartingTaskStep(RunContainerStep(container, DockerContainer("some-id")))
                logger.postEvent(RunningContainerExitedEvent(container, 123))
                reset(startupProgressDisplay)
                reset(cleanupProgressDisplay)

                val event = ContainerRemovedEvent(Container("some-container", imageSourceDoesNotMatter()))
                logger.postEvent(event)

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

            mapOf(
                "remove container" to RemoveContainerStep(Container("some-container", imageSourceDoesNotMatter()), DockerContainer("some-id")),
                "clean up container" to CleanUpContainerStep(Container("some-container", imageSourceDoesNotMatter()), DockerContainer("some-id")),
                "remove network" to DeleteTaskNetworkStep(DockerNetwork("some-network-id"))
            ).forEach { (description, step) ->
                on("after a '$description' step has been run") {
                    logger.onStartingTaskStep(step)
                    reset(startupProgressDisplay)
                    reset(cleanupProgressDisplay)

                    val event = ContainerBecameHealthyEvent(Container("some-container", imageSourceDoesNotMatter()))
                    logger.postEvent(event)

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
                    beforeEachTest { whenever(failureErrorMessageFormatter.formatErrorMessage(event, runOptions)).doReturn("Something went wrong.") }

                    on("posting the event") {
                        logger.postEvent(event)

                        it("prints the message to the output") {
                            inOrder(console, redErrorConsole) {
                                verify(console).println()
                                verify(redErrorConsole).println("Something went wrong.")
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
                        logger.onStartingTaskStep(DeleteTaskNetworkStep(DockerNetwork("some-network-id")))
                        reset(redErrorConsole)
                        reset(console)
                        reset(cleanupProgressDisplay)

                        whenever(failureErrorMessageFormatter.formatErrorMessage(event, runOptions)).doReturn("Something went wrong for a second time.")
                    }

                    on("posting the event") {
                        logger.postEvent(event)

                        it("prints the message to the output") {
                            verify(redErrorConsole).println("Something went wrong for a second time.")
                        }

                        it("prints the cleanup progress to the console") {
                            verify(cleanupProgressDisplay).print(console)
                        }

                        it("clears the existing cleanup progress before printing the error message and reprinting the cleanup progress") {
                            inOrder(console, redErrorConsole, cleanupProgressDisplay) {
                                verify(cleanupProgressDisplay).clear(console)
                                verify(redErrorConsole).println("Something went wrong for a second time.")
                                verify(console).println()
                                verify(cleanupProgressDisplay).print(console)
                            }
                        }
                    }
                }
            }
        }

        on("when the task starts") {
            logger.onTaskStarting("some-task")

            it("prints a message to the output") {
                inOrder(whiteConsole) {
                    verify(whiteConsole).print("Running ")
                    verify(whiteConsole).printBold("some-task")
                    verify(whiteConsole).println("...")
                }
            }
        }

        describe("when the task fails") {
            given("there are no cleanup instructions") {
                val cleanupInstructions = ""

                on("when logging that the task has failed") {
                    logger.onTaskFailed("some-task", cleanupInstructions)

                    it("prints a message to the output") {
                        inOrder(redErrorConsole) {
                            verify(redErrorConsole).println()
                            verify(redErrorConsole).print("The task ")
                            verify(redErrorConsole).printBold("some-task")
                            verify(redErrorConsole).println(" failed. See above for details.")
                        }
                    }
                }
            }

            given("there are some cleanup instructions") {
                val cleanupInstructions = "Do this to clean up."

                on("when logging that the task has failed") {
                    logger.onTaskFailed("some-task", cleanupInstructions)

                    it("prints a message to the output, including the instructions") {
                        inOrder(redErrorConsole) {
                            verify(redErrorConsole).println()
                            verify(redErrorConsole).println(cleanupInstructions)
                            verify(redErrorConsole).println()
                            verify(redErrorConsole).print("The task ")
                            verify(redErrorConsole).printBold("some-task")
                            verify(redErrorConsole).println(" failed. See above for details.")
                        }
                    }
                }
            }
        }
    }
})
