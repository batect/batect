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

package batect.ui.simple

import batect.config.Container
import batect.docker.DockerContainer
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.model.steps.BuildImageStep
import batect.model.steps.CleanUpContainerStep
import batect.model.steps.CreateContainerStep
import batect.model.steps.CreateTaskNetworkStep
import batect.model.steps.DisplayTaskFailureStep
import batect.model.steps.PullImageStep
import batect.model.steps.RemoveContainerStep
import batect.model.steps.RunContainerStep
import batect.model.steps.StartContainerStep
import batect.testutils.createForEachTest
import batect.testutils.imageSourceDoesNotMatter
import batect.ui.Console
import batect.ui.ConsoleColor
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object SimpleEventLoggerSpec : Spek({
    describe("a simple event logger") {
        val whiteConsole by createForEachTest { mock<Console>() }
        val console by createForEachTest {
            mock<Console> {
                on { withColor(eq(ConsoleColor.White), any()) } doAnswer {
                    val printStatements = it.getArgument<Console.() -> Unit>(1)
                    printStatements(whiteConsole)
                }
            }
        }

        val redErrorConsole by createForEachTest { mock<Console>() }
        val errorConsole by createForEachTest {
            mock<Console> {
                on { withColor(eq(ConsoleColor.Red), any()) } doAnswer {
                    val printStatements = it.getArgument<Console.() -> Unit>(1)
                    printStatements(redErrorConsole)
                }
            }
        }

        val logger by createForEachTest { SimpleEventLogger(console, errorConsole) }
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
                        val createContainerStep = CreateContainerStep(container, null, DockerImage("some-image"), DockerNetwork("some-network"))
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
                        val createContainerStep = CreateContainerStep(container, "do-stuff.sh", DockerImage("some-image"), DockerNetwork("some-network"))
                        val runContainerStep = RunContainerStep(container, DockerContainer("not-important"))

                        logger.onStartingTaskStep(createContainerStep)
                        logger.onStartingTaskStep(runContainerStep)

                        it("prints a message to the output including the command") {
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

            on("when a 'display task failure' step is starting") {
                val step = DisplayTaskFailureStep("Something went wrong.")
                logger.onStartingTaskStep(step)

                it("prints the message to the output") {
                    inOrder(redErrorConsole) {
                        verify(redErrorConsole).println()
                        verify(redErrorConsole).println(step.message)
                    }
                }
            }

            mapOf(
                    "remove container" to RemoveContainerStep(container, DockerContainer("some-id")),
                    "clean up container" to CleanUpContainerStep(container, DockerContainer("some-id"))
            ).forEach { description, step ->
                describe("when a '$description' step is starting") {
                    on("and no 'remove container' or 'clean up container' steps have run before") {
                        logger.onStartingTaskStep(step)

                        it("prints a message to the output") {
                            verify(whiteConsole).println("Cleaning up...")
                        }
                    }

                    on("and a 'remove container' step has already been run") {
                        val previousStep = RemoveContainerStep(Container("other-container", imageSourceDoesNotMatter()), DockerContainer("some-other-id"))
                        logger.onStartingTaskStep(previousStep)

                        logger.onStartingTaskStep(step)

                        it("only prints one message to the output") {
                            verify(whiteConsole, times(1)).println("Cleaning up...")
                        }
                    }

                    on("and a 'clean up container' step has already been run") {
                        val previousStep = CleanUpContainerStep(Container("other-container", imageSourceDoesNotMatter()), DockerContainer("some-other-id"))
                        logger.onStartingTaskStep(previousStep)

                        logger.onStartingTaskStep(step)

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
    }
})
