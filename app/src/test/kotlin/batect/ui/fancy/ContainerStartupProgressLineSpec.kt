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

import batect.config.BuildImage
import batect.config.Container
import batect.config.PullImage
import batect.docker.DockerContainer
import batect.docker.DockerImage
import batect.docker.DockerImageBuildProgress
import batect.docker.DockerNetwork
import batect.model.events.ContainerBecameHealthyEvent
import batect.model.events.ContainerCreatedEvent
import batect.model.events.ContainerStartedEvent
import batect.model.events.ImageBuildProgressEvent
import batect.model.events.ImageBuiltEvent
import batect.model.events.ImagePulledEvent
import batect.model.events.TaskNetworkCreatedEvent
import batect.model.steps.BuildImageStep
import batect.model.steps.CreateContainerStep
import batect.model.steps.PullImageStep
import batect.model.steps.RunContainerStep
import batect.model.steps.StartContainerStep
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
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerStartupProgressLineSpec : Spek({
    describe("a container startup progress line") {
        val dependencyA = Container("dependency-a", imageSourceDoesNotMatter())
        val dependencyB = Container("dependency-b", imageSourceDoesNotMatter())
        val dependencyC = Container("dependency-c", imageSourceDoesNotMatter())
        val otherContainer = Container("other-container", BuildImage("/other-build-dir"))
        val containerName = "some-container"

        val whiteConsole by CreateForEachTest(this) { mock<Console>() }
        val console by CreateForEachTest(this) {
            mock<Console> {
                on { withColor(eq(ConsoleColor.White), any()) } doAnswer {
                    val printStatements = it.getArgument<Console.() -> Unit>(1)
                    printStatements(whiteConsole)
                }
            }
        }

        fun verifyPrintedDescription(description: String) {
            inOrder(whiteConsole) {
                verify(whiteConsole).printBold(containerName)
                verify(whiteConsole).print(": ")
                verify(whiteConsole).print(description)
            }
        }

        given("the container's image comes from building an image") {
            val container = Container(containerName, BuildImage("/some-build-dir"))

            val line: ContainerStartupProgressLine by CreateForEachTest(this) {
                ContainerStartupProgressLine(container, setOf(dependencyA, dependencyB, dependencyC))
            }

            on("initial state") {
                line.print(console)

                it("prints that the container is waiting to build its image") {
                    verifyPrintedDescription("ready to build image")
                }
            }

            describe("after receiving an 'image build starting' notification") {
                on("that notification being for this line's container") {
                    val step = BuildImageStep("some-project", container)
                    line.onStepStarting(step)
                    line.print(console)

                    it("prints that the container is building") {
                        verifyPrintedDescription("building image...")
                    }
                }

                on("that notification being for another container") {
                    val step = BuildImageStep("some-project", otherContainer)
                    line.onStepStarting(step)
                    line.print(console)

                    it("prints that the container is still waiting to build its image") {
                        verifyPrintedDescription("ready to build image")
                    }
                }
            }

            describe("after receiving an 'image build progress' notification") {
                on("that notification being for this line's container") {
                    val event = ImageBuildProgressEvent(container, DockerImageBuildProgress(2, 5, "COPY health-check.sh /tools/"))
                    line.onEventPosted(event)
                    line.print(console)

                    it("prints detailed build progress") {
                        verifyPrintedDescription("building image: step 2 of 5: COPY health-check.sh /tools/")
                    }
                }

                on("that notification being for another container") {
                    val event = ImageBuildProgressEvent(otherContainer, DockerImageBuildProgress(2, 5, "COPY health-check.sh /tools/"))
                    line.onEventPosted(event)
                    line.print(console)

                    it("prints that the container is still waiting to build its image") {
                        verifyPrintedDescription("ready to build image")
                    }
                }
            }

            on("after receiving an 'image pull starting' notification") {
                val step = PullImageStep("some-image")
                line.onStepStarting(step)
                line.print(console)

                it("prints that the container is still waiting to build its image") {
                    verifyPrintedDescription("ready to build image")
                }
            }

            describe("after receiving an 'image built' notification") {
                describe("and that notification being for this line's container") {
                    val event = ImageBuiltEvent(container, DockerImage("some-image"))

                    on("when the task network has already been created") {
                        line.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                        line.onEventPosted(event)
                        line.print(console)

                        it("prints that the container is ready to be created") {
                            verifyPrintedDescription("image built, ready to create container")
                        }
                    }

                    on("when the task network has not already been created") {
                        line.onEventPosted(event)
                        line.print(console)

                        it("prints that the container is ready to be created") {
                            verifyPrintedDescription("image built, waiting for network to be ready...")
                        }
                    }
                }

                on("that notification being for another container") {
                    val event = ImageBuiltEvent(otherContainer, DockerImage("some-image"))
                    line.onEventPosted(event)
                    line.print(console)

                    it("prints that the container is still waiting to build its image") {
                        verifyPrintedDescription("ready to build image")
                    }
                }
            }

            on("after receiving a 'image pull completed' notification") {
                val event = ImagePulledEvent(DockerImage("some-image"))
                line.onEventPosted(event)
                line.print(console)

                it("prints that the container is still waiting to build its image") {
                    verifyPrintedDescription("ready to build image")
                }
            }

            describe("after receiving a 'task network created' notification") {
                val event = TaskNetworkCreatedEvent(DockerNetwork("some-network"))

                on("when the image has not started building yet") {
                    line.onEventPosted(event)
                    line.print(console)

                    it("prints that the container is still waiting to build its image") {
                        verifyPrintedDescription("ready to build image")
                    }
                }

                on("when the image is still building") {
                    line.onStepStarting(BuildImageStep("some-project", container))
                    line.onEventPosted(event)
                    line.print(console)

                    it("prints that the container is building") {
                        verifyPrintedDescription("building image...")
                    }
                }

                on("when the image has been built") {
                    line.onEventPosted(ImageBuiltEvent(container, DockerImage("some-image")))
                    line.onEventPosted(event)
                    line.print(console)

                    it("prints that the container is ready to be created") {
                        verifyPrintedDescription("image built, ready to create container")
                    }
                }
            }

            describe("after receiving a 'creating container' notification") {
                on("that notification being for this line's container") {
                    val step = CreateContainerStep(container, "some-command", DockerImage("some-image"), DockerNetwork("some-network"))
                    line.onStepStarting(step)
                    line.print(console)

                    it("prints that the container is being created") {
                        verifyPrintedDescription("creating container...")
                    }
                }

                on("that notification being for another container") {
                    val step = CreateContainerStep(otherContainer, "some-command", DockerImage("some-image"), DockerNetwork("some-network"))
                    line.onStepStarting(step)
                    line.print(console)

                    it("prints that the container is still waiting to build its image") {
                        verifyPrintedDescription("ready to build image")
                    }
                }
            }

            describe("after receiving a 'container created' notification") {
                describe("on that notification being for this line's container") {
                    val event = ContainerCreatedEvent(container, DockerContainer("some-id"))

                    on("and none of the container's dependencies being ready") {
                        line.onEventPosted(event)
                        line.print(console)

                        it("prints that the container is waiting for the dependencies to be ready") {
                            inOrder(whiteConsole) {
                                verify(whiteConsole).printBold(container.name)
                                verify(whiteConsole).print(": ")
                                verify(whiteConsole).print("waiting for dependencies ")
                                verify(whiteConsole).printBold(dependencyA.name)
                                verify(whiteConsole).print(", ")
                                verify(whiteConsole).printBold(dependencyB.name)
                                verify(whiteConsole).print(" and ")
                                verify(whiteConsole).printBold(dependencyC.name)
                                verify(whiteConsole).print(" to be ready...")
                            }
                        }
                    }

                    on("and two of the container's dependencies not being ready") {
                        line.onEventPosted(ContainerBecameHealthyEvent(dependencyA))
                        line.onEventPosted(event)
                        line.print(console)

                        it("prints that the container is waiting for the dependencies to be ready") {
                            inOrder(whiteConsole) {
                                verify(whiteConsole).printBold(container.name)
                                verify(whiteConsole).print(": ")
                                verify(whiteConsole).print("waiting for dependencies ")
                                verify(whiteConsole).printBold(dependencyB.name)
                                verify(whiteConsole).print(" and ")
                                verify(whiteConsole).printBold(dependencyC.name)
                                verify(whiteConsole).print(" to be ready...")
                            }
                        }
                    }

                    on("and one of the container's dependencies not being ready") {
                        line.onEventPosted(ContainerBecameHealthyEvent(dependencyA))
                        line.onEventPosted(ContainerBecameHealthyEvent(dependencyB))
                        line.onEventPosted(event)
                        line.print(console)

                        it("prints that the container is waiting for that dependency to be ready") {
                            inOrder(whiteConsole) {
                                verify(whiteConsole).printBold(container.name)
                                verify(whiteConsole).print(": ")
                                verify(whiteConsole).print("waiting for dependency ")
                                verify(whiteConsole).printBold(dependencyC.name)
                                verify(whiteConsole).print(" to be ready...")
                            }
                        }
                    }

                    on("and all of the container's dependencies are ready") {
                        line.onEventPosted(ContainerBecameHealthyEvent(dependencyA))
                        line.onEventPosted(ContainerBecameHealthyEvent(dependencyB))
                        line.onEventPosted(ContainerBecameHealthyEvent(dependencyC))
                        line.onEventPosted(event)
                        line.print(console)

                        it("prints that the container is ready to start") {
                            verifyPrintedDescription("ready to start")
                        }
                    }
                }

                on("that notification being for another container") {
                    val event = ContainerCreatedEvent(otherContainer, DockerContainer("some-id"))
                    line.onEventPosted(event)
                    line.print(console)

                    it("prints that the container is still waiting to build its image") {
                        verifyPrintedDescription("ready to build image")
                    }
                }
            }

            describe("after receiving a 'container starting' notification") {
                on("that notification being for this line's container") {
                    val step = StartContainerStep(container, DockerContainer("some-id"))
                    line.onStepStarting(step)
                    line.print(console)

                    it("prints that the container is starting") {
                        verifyPrintedDescription("starting container...")
                    }
                }

                on("that notification being for another container") {
                    val step = StartContainerStep(otherContainer, DockerContainer("some-id"))
                    line.onStepStarting(step)
                    line.print(console)

                    it("prints that the container is still waiting to build its image") {
                        verifyPrintedDescription("ready to build image")
                    }
                }
            }

            describe("after receiving a 'container started' notification") {
                on("that notification being for this line's container") {
                    val event = ContainerStartedEvent(container)
                    line.onEventPosted(event)
                    line.print(console)

                    it("prints that the container is waiting to become healthy") {
                        verifyPrintedDescription("container started, waiting for it to become healthy...")
                    }
                }

                on("that notification being for another container") {
                    val event = ContainerStartedEvent(otherContainer)
                    line.onEventPosted(event)
                    line.print(console)

                    it("prints that the container is still waiting to build its image") {
                        verifyPrintedDescription("ready to build image")
                    }
                }
            }

            describe("after receiving a 'container became healthy' notification") {
                on("that notification being for this line's container") {
                    val event = ContainerBecameHealthyEvent(container)
                    line.onEventPosted(event)
                    line.print(console)

                    it("prints that the container has finished starting up") {
                        verifyPrintedDescription("running")
                    }
                }

                on("that notification being for another container") {
                    val event = ContainerBecameHealthyEvent(otherContainer)
                    line.onEventPosted(event)
                    line.print(console)

                    it("prints that the container is still waiting to build its image") {
                        verifyPrintedDescription("ready to build image")
                    }
                }
            }

            describe("after receiving a 'running container' notification") {
                describe("that notification being for this line's container") {
                    val step = RunContainerStep(container, DockerContainer("some-id"))

                    on("and the container has no command specified in the configuration file") {
                        line.onStepStarting(CreateContainerStep(container, null, DockerImage("some-image"), DockerNetwork("some-network")))
                        line.onStepStarting(step)
                        line.print(console)

                        it("prints that the container has finished starting up") {
                            verifyPrintedDescription("running")
                        }
                    }

                    on("and the container has a command specified in the configuration file") {
                        line.onStepStarting(CreateContainerStep(container, "some-command", DockerImage("some-image"), DockerNetwork("some-network")))
                        line.onStepStarting(step)
                        line.print(console)

                        it("prints that the container has finished starting up and the command that it is running") {
                            inOrder(whiteConsole) {
                                verify(whiteConsole).printBold(container.name)
                                verify(whiteConsole).print(": ")
                                verify(whiteConsole).print("running ")
                                verify(whiteConsole).printBold("some-command")
                            }
                        }
                    }

                    on("and another container has a command specified in the configuration file") {
                        line.onStepStarting(CreateContainerStep(otherContainer, "some-command", DockerImage("some-image"), DockerNetwork("some-network")))
                        line.onStepStarting(step)
                        line.print(console)

                        it("prints that the container has finished starting up") {
                            verifyPrintedDescription("running")
                        }
                    }
                }

                on("that notification being for another container") {
                    val step = RunContainerStep(otherContainer, DockerContainer("some-id"))
                    line.onStepStarting(step)
                    line.print(console)

                    it("prints that the container is still waiting to build its image") {
                        verifyPrintedDescription("ready to build image")
                    }
                }
            }
        }

        given("the container's image comes from a pre-existing image that needs to be pulled") {
            val container = Container(containerName, PullImage("some-image"))

            val line: ContainerStartupProgressLine by CreateForEachTest(this) {
                ContainerStartupProgressLine(container, setOf(dependencyA, dependencyB, dependencyC))
            }

            on("initial state") {
                line.print(console)

                it("prints that the container is waiting to pull its image") {
                    verifyPrintedDescription("ready to pull image")
                }
            }

            describe("after receiving an 'image pull starting' notification") {
                on("that notification being for this line's container's image") {
                    val step = PullImageStep("some-image")
                    line.onStepStarting(step)
                    line.print(console)

                    it("prints that the image is being pulled") {
                        inOrder(whiteConsole) {
                            verify(whiteConsole).printBold(container.name)
                            verify(whiteConsole).print(": ")
                            verify(whiteConsole).print("pulling ")
                            verify(whiteConsole).printBold("some-image")
                        }
                    }
                }

                on("that notification being for another image") {
                    val step = PullImageStep("some-other-image")
                    line.onStepStarting(step)
                    line.print(console)

                    it("prints that the container is still waiting to pull its image") {
                        verifyPrintedDescription("ready to pull image")
                    }
                }
            }

            describe("after receiving an 'image pulled' notification") {
                describe("and that notification being for this line's container's image") {
                    val event = ImagePulledEvent(DockerImage("some-image"))

                    on("when the task network has already been created") {
                        line.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                        line.onEventPosted(event)
                        line.print(console)

                        it("prints that the container is ready to be created") {
                            verifyPrintedDescription("image pulled, ready to create container")
                        }
                    }

                    on("when the task network has not already been created") {
                        line.onEventPosted(event)
                        line.print(console)

                        it("prints that the container is ready to be created") {
                            verifyPrintedDescription("image pulled, waiting for network to be ready...")
                        }
                    }
                }

                on("that notification being for another image") {
                    val event = ImagePulledEvent(DockerImage("some-other-image"))
                    line.onEventPosted(event)
                    line.print(console)

                    it("prints that the container is still waiting to pull its image") {
                        verifyPrintedDescription("ready to pull image")
                    }
                }
            }

            describe("after receiving a 'task network created' notification") {
                val event = TaskNetworkCreatedEvent(DockerNetwork("some-network"))

                on("when the image pull has not started yet") {
                    line.onEventPosted(event)
                    line.print(console)

                    it("prints that the container is still waiting to pull its image") {
                        verifyPrintedDescription("ready to pull image")
                    }
                }

                on("when the image is still being pulled") {
                    line.onStepStarting(PullImageStep("some-image"))
                    line.onEventPosted(event)
                    line.print(console)

                    it("prints that the container is building") {
                        inOrder(whiteConsole) {
                            verify(whiteConsole).printBold(container.name)
                            verify(whiteConsole).print(": ")
                            verify(whiteConsole).print("pulling ")
                            verify(whiteConsole).printBold("some-image")
                        }
                    }
                }

                on("when the image has been pulled") {
                    line.onEventPosted(ImagePulledEvent(DockerImage("some-image")))
                    line.onEventPosted(event)
                    line.print(console)

                    it("prints that the container is ready to be created") {
                        verifyPrintedDescription("image pulled, ready to create container")
                    }
                }
            }
        }
    }
})
