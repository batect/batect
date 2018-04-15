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

package batect.ui.simple

import batect.config.Container
import batect.docker.DockerContainer
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.execution.RunOptions
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.steps.BuildImageStep
import batect.execution.model.steps.CleanupStep
import batect.execution.model.steps.CreateContainerStep
import batect.execution.model.steps.CreateTaskNetworkStep
import batect.execution.model.steps.PullImageStep
import batect.execution.model.steps.RunContainerStep
import batect.execution.model.steps.StartContainerStep
import batect.os.Command
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
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object SimpleEventLoggerSpec : Spek({
    describe("a simple event logger") {
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

        val logger by createForEachTest { SimpleEventLogger(failureErrorMessageFormatter, runOptions, console, errorConsole) }
        val container = Container("the-cool-container", imageSourceDoesNotMatter())

        describe("handling when steps start") {
            on("when a 'build image' step is starting") {
                val step = BuildImageStep("doesnt-matter", container)
                logger.onStartingTaskStep(step)

                it("prints a message to the output") {
                    inOrder(whiteConsole) {
                        verify(whiteConsole).print("Building ")
                        verify(whiteConsole).printBold("the-cool-container")
                        verify(whiteConsole).println("...")
                    }
                }
            }

            on("when a 'pull image' step is starting") {
                val step = PullImageStep("some-image:1.2.3")
                logger.onStartingTaskStep(step)

                it("prints a message to the output") {
                    inOrder(whiteConsole) {
                        verify(whiteConsole).print("Pulling ")
                        verify(whiteConsole).printBold("some-image:1.2.3")
                        verify(whiteConsole).println("...")
                    }
                }
            }

            on("when a 'start container' step is starting") {
                val step = StartContainerStep(container, DockerContainer("not-important"))
                logger.onStartingTaskStep(step)

                it("prints a message to the output") {
                    inOrder(whiteConsole) {
                        verify(whiteConsole).print("Starting dependency ")
                        verify(whiteConsole).printBold("the-cool-container")
                        verify(whiteConsole).println("...")
                    }
                }
            }

            describe("when a 'run container' step is starting") {
                on("and no 'create container' step has been seen") {
                    val step = RunContainerStep(container, DockerContainer("not-important"))
                    logger.onStartingTaskStep(step)

                    it("prints a message to the output without mentioning a command") {
                        inOrder(whiteConsole) {
                            verify(whiteConsole).print("Running ")
                            verify(whiteConsole).printBold("the-cool-container")
                            verify(whiteConsole).println("...")
                        }
                    }
                }

                describe("and a 'create container' step has been seen") {
                    on("and that step did not contain a command") {
                        val createContainerStep = CreateContainerStep(container, null, emptyMap(), emptySet(), DockerImage("some-image"), DockerNetwork("some-network"))
                        val runContainerStep = RunContainerStep(container, DockerContainer("not-important"))

                        logger.onStartingTaskStep(createContainerStep)
                        logger.onStartingTaskStep(runContainerStep)

                        it("prints a message to the output without mentioning a command") {
                            inOrder(whiteConsole) {
                                verify(whiteConsole).print("Running ")
                                verify(whiteConsole).printBold("the-cool-container")
                                verify(whiteConsole).println("...")
                            }
                        }
                    }

                    on("and that step contained a command") {
                        val createContainerStep = CreateContainerStep(container, Command.parse("do-stuff.sh"), emptyMap(), emptySet(), DockerImage("some-image"), DockerNetwork("some-network"))
                        val runContainerStep = RunContainerStep(container, DockerContainer("not-important"))

                        logger.onStartingTaskStep(createContainerStep)
                        logger.onStartingTaskStep(runContainerStep)

                        it("prints a message to the output including the original command") {
                            inOrder(whiteConsole) {
                                verify(whiteConsole).print("Running ")
                                verify(whiteConsole).printBold("do-stuff.sh")
                                verify(whiteConsole).print(" in ")
                                verify(whiteConsole).printBold("the-cool-container")
                                verify(whiteConsole).println("...")
                            }
                        }
                    }
                }
            }

            describe("when a cleanup step is starting") {
                val cleanupStep = mock<CleanupStep>()

                given("no cleanup steps have run before") {
                    on("that step starting") {
                        logger.onStartingTaskStep(cleanupStep)

                        it("prints a blank line before then printing that clean up has started") {
                            inOrder(whiteConsole) {
                                verify(whiteConsole).println()
                                verify(whiteConsole).println("Cleaning up...")
                            }
                        }
                    }
                }

                given("and a cleanup step has already been run") {
                    beforeEachTest {
                        val previousStep = mock<CleanupStep>()
                        logger.onStartingTaskStep(previousStep)
                    }

                    on("that step starting") {
                        logger.onStartingTaskStep(cleanupStep)

                        it("only prints one message to the output") {
                            verify(whiteConsole, times(1)).println("Cleaning up...")
                        }
                    }
                }
            }

            on("when another kind of step is starting") {
                val step = CreateTaskNetworkStep
                logger.onStartingTaskStep(step)

                it("does not print anything to the output") {
                    verifyZeroInteractions(console)
                }
            }
        }

        describe("handling when events are posted") {
            on("when a 'task failed' event is posted") {
                val event = mock<TaskFailedEvent>()
                whenever(failureErrorMessageFormatter.formatErrorMessage(event, runOptions)).doReturn("Something went wrong.")

                logger.postEvent(event)

                it("prints the message to the console") {
                    inOrder(redErrorConsole) {
                        verify(redErrorConsole).println()
                        verify(redErrorConsole).println("Something went wrong.")
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
