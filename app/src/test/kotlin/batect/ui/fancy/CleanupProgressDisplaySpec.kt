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

package batect.ui.fancy

import batect.config.Container
import batect.docker.DockerContainer
import batect.docker.DockerNetwork
import batect.model.events.ContainerCreatedEvent
import batect.model.events.ContainerRemovedEvent
import batect.model.events.TaskNetworkCreatedEvent
import batect.model.events.TaskNetworkDeletedEvent
import batect.testutils.CreateForEachTest
import batect.testutils.imageSourceDoesNotMatter
import batect.ui.Console
import batect.ui.ConsoleColor
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object CleanupProgressDisplaySpec : Spek({
    describe("a cleanup progress display") {
        val whiteConsole by CreateForEachTest(this) { mock<Console>() }
        val console by CreateForEachTest(this) {
            mock<Console> {
                on { withColor(eq(ConsoleColor.White), any()) } doAnswer {
                    val printStatements = it.getArgument<Console.() -> Unit>(1)
                    printStatements(whiteConsole)
                }
            }
        }

        val cleanupDisplay by CreateForEachTest(this) { CleanupProgressDisplay() }

        describe("printing cleanup progress to the console") {
            on("when there is nothing to clean up") {
                cleanupDisplay.print(console)

                it("prints that clean up is complete") {
                    inOrder(whiteConsole) {
                        verify(whiteConsole).println("Clean up: done")
                    }
                }
            }

            describe("when there is only the network to clean up") {
                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                }

                on("and the network hasn't been removed yet") {
                    cleanupDisplay.print(console)

                    it("prints that the network still needs to be cleaned up") {
                        inOrder(whiteConsole) {
                            verify(whiteConsole).println("Cleaning up: removing task network...")
                        }
                    }
                }

                on("and the network has been removed") {
                    cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    cleanupDisplay.print(console)

                    it("prints that clean up is complete") {
                        inOrder(whiteConsole) {
                            verify(whiteConsole).println("Clean up: done")
                        }
                    }
                }
            }

            describe("when there is a container and the network to clean up") {
                val container = Container("some-container", imageSourceDoesNotMatter())

                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container, DockerContainer("some-container-id")))
                }

                on("and the container hasn't been removed yet") {
                    cleanupDisplay.print(console)

                    it("prints that the container still needs to be cleaned up") {
                        inOrder(whiteConsole) {
                            verify(whiteConsole).print("Cleaning up: 1 container (")
                            verify(whiteConsole).printBold("some-container")
                            verify(whiteConsole).println(") left to remove...")
                        }
                    }
                }

                on("and the container has been removed") {
                    cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                    cleanupDisplay.print(console)

                    it("prints that the network still needs to be cleaned up") {
                        inOrder(whiteConsole) {
                            verify(whiteConsole).println("Cleaning up: removing task network...")
                        }
                    }
                }

                on("and the network has been removed") {
                    cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                    cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    cleanupDisplay.print(console)

                    it("prints that clean up is complete") {
                        inOrder(whiteConsole) {
                            verify(whiteConsole).println("Clean up: done")
                        }
                    }
                }
            }

            describe("when there are two containers and the network to clean up") {
                val container1 = Container("container-1", imageSourceDoesNotMatter())
                val container2 = Container("container-2", imageSourceDoesNotMatter())

                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container1, DockerContainer("container-1-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container2, DockerContainer("container-2-id")))
                }

                on("and neither container has been removed yet") {
                    cleanupDisplay.print(console)

                    it("prints that both of the containers still need to be cleaned up") {
                        inOrder(whiteConsole) {
                            verify(whiteConsole).print("Cleaning up: 2 containers (")
                            verify(whiteConsole).printBold("container-1")
                            verify(whiteConsole).print(" and ")
                            verify(whiteConsole).printBold("container-2")
                            verify(whiteConsole).println(") left to remove...")
                        }
                    }
                }

                on("and one container has been removed") {
                    cleanupDisplay.onEventPosted(ContainerRemovedEvent(container1))
                    cleanupDisplay.print(console)

                    it("prints that the other container still needs to be cleaned up") {
                        inOrder(whiteConsole) {
                            verify(whiteConsole).print("Cleaning up: 1 container (")
                            verify(whiteConsole).printBold("container-2")
                            verify(whiteConsole).println(") left to remove...")
                        }
                    }
                }

                on("and the network has been removed") {
                    cleanupDisplay.onEventPosted(ContainerRemovedEvent(container1))
                    cleanupDisplay.onEventPosted(ContainerRemovedEvent(container2))
                    cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    cleanupDisplay.print(console)

                    it("prints that clean up is complete") {
                        inOrder(whiteConsole) {
                            verify(whiteConsole).println("Clean up: done")
                        }
                    }
                }
            }

            describe("when there are three containers and the network to clean up") {
                val container1 = Container("container-1", imageSourceDoesNotMatter())
                val container2 = Container("container-2", imageSourceDoesNotMatter())
                val container3 = Container("container-3", imageSourceDoesNotMatter())

                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container1, DockerContainer("container-1-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container2, DockerContainer("container-2-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container3, DockerContainer("container-3-id")))
                }

                on("and none of the containers have been removed yet") {
                    cleanupDisplay.print(console)

                    it("prints that all of the containers still need to be cleaned up") {
                        inOrder(whiteConsole) {
                            verify(whiteConsole).print("Cleaning up: 3 containers (")
                            verify(whiteConsole).printBold("container-1")
                            verify(whiteConsole).print(", ")
                            verify(whiteConsole).printBold("container-2")
                            verify(whiteConsole).print(" and ")
                            verify(whiteConsole).printBold("container-3")
                            verify(whiteConsole).println(") left to remove...")
                        }
                    }
                }
            }

            describe("when there are four containers and the network to clean up") {
                val container1 = Container("container-1", imageSourceDoesNotMatter())
                val container2 = Container("container-2", imageSourceDoesNotMatter())
                val container3 = Container("container-3", imageSourceDoesNotMatter())
                val container4 = Container("container-4", imageSourceDoesNotMatter())

                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container1, DockerContainer("container-1-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container2, DockerContainer("container-2-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container3, DockerContainer("container-3-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container4, DockerContainer("container-4-id")))
                }

                on("and none of the containers have been removed yet") {
                    cleanupDisplay.print(console)

                    it("prints that all of the containers still need to be cleaned up") {
                        inOrder(whiteConsole) {
                            verify(whiteConsole).print("Cleaning up: 4 containers (")
                            verify(whiteConsole).printBold("container-1")
                            verify(whiteConsole).print(", ")
                            verify(whiteConsole).printBold("container-2")
                            verify(whiteConsole).print(", ")
                            verify(whiteConsole).printBold("container-3")
                            verify(whiteConsole).print(" and ")
                            verify(whiteConsole).printBold("container-4")
                            verify(whiteConsole).println(") left to remove...")
                        }
                    }
                }
            }
        }

        on("clearing progress previously printed to the console") {
            cleanupDisplay.clear(console)

            it("moves back up to the text previously printed and clears that line") {
                inOrder(console) {
                    verify(console).moveCursorUp()
                    verify(console).clearCurrentLine()
                }
            }
        }
    }
})
