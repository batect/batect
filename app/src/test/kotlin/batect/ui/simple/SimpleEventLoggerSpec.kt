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

package batect.ui.simple

import batect.config.BuildImage
import batect.config.Container
import batect.docker.DockerContainer
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.execution.RunOptions
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.ImagePulledEvent
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
import batect.ui.FailureErrorMessageFormatter
import batect.ui.text.Text
import batect.ui.text.TextRun
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object SimpleEventLoggerSpec : Spek({
    describe("a simple event logger") {
        val container1 = Container("container-1", BuildImage("/some-image-dir"))
        val container2 = Container("container-2", BuildImage("/some-image-dir"))
        val container3 = Container("container-3", BuildImage("/some-other-image-dir"))
        val containers = setOf(container1, container2, container3)

        val failureErrorMessageFormatter by createForEachTest { mock<FailureErrorMessageFormatter>() }
        val runOptions by createForEachTest { mock<RunOptions>() }
        val console by createForEachTest { mock<Console>() }
        val errorConsole by createForEachTest { mock<Console>() }

        val logger by createForEachTest { SimpleEventLogger(containers, failureErrorMessageFormatter, runOptions, console, errorConsole) }
        val container = Container("the-cool-container", imageSourceDoesNotMatter())

        describe("handling when steps start") {
            on("when a 'build image' step is starting") {
                val step = BuildImageStep("/some-image-dir")
                logger.onStartingTaskStep(step)

                it("prints a message to the output for each container that uses that built image") {
                    verify(console).println(Text.white(Text("Building ") + Text.bold("container-1") + Text("...")))
                    verify(console).println(Text.white(Text("Building ") + Text.bold("container-2") + Text("...")))
                }
            }

            on("when a 'pull image' step is starting") {
                val step = PullImageStep("some-image:1.2.3")
                logger.onStartingTaskStep(step)

                it("prints a message to the output") {
                    verify(console).println(Text.white(Text("Pulling ") + Text.bold("some-image:1.2.3") + Text("...")))
                }
            }

            on("when a 'start container' step is starting") {
                val step = StartContainerStep(container, DockerContainer("not-important"))
                logger.onStartingTaskStep(step)

                it("prints a message to the output") {
                    verify(console).println(Text.white(Text("Starting ") + Text.bold("the-cool-container") + Text("...")))
                }
            }

            describe("when a 'run container' step is starting") {
                on("and no 'create container' step has been seen") {
                    val step = RunContainerStep(container, DockerContainer("not-important"))
                    logger.onStartingTaskStep(step)

                    it("prints a message to the output without mentioning a command") {
                        verify(console).println(Text.white(Text("Running ") + Text.bold("the-cool-container") + Text("...")))
                    }
                }

                describe("and a 'create container' step has been seen") {
                    on("and that step did not contain a command") {
                        val createContainerStep = CreateContainerStep(container, null, emptyMap(), emptySet(), emptySet(), DockerImage("some-image"), DockerNetwork("some-network"))
                        val runContainerStep = RunContainerStep(container, DockerContainer("not-important"))

                        logger.onStartingTaskStep(createContainerStep)
                        logger.onStartingTaskStep(runContainerStep)

                        it("prints a message to the output without mentioning a command") {
                            verify(console).println(Text.white(Text("Running ") + Text.bold("the-cool-container") + Text("...")))
                        }
                    }

                    on("and that step contained a command") {
                        val createContainerStep = CreateContainerStep(container, Command.parse("do-stuff.sh"), emptyMap(), emptySet(), emptySet(), DockerImage("some-image"), DockerNetwork("some-network"))
                        val runContainerStep = RunContainerStep(container, DockerContainer("not-important"))

                        logger.onStartingTaskStep(createContainerStep)
                        logger.onStartingTaskStep(runContainerStep)

                        it("prints a message to the output including the original command") {
                            verify(console).println(Text.white(Text("Running ") + Text.bold("do-stuff.sh") + Text(" in ") + Text.bold("the-cool-container") + Text("...")))
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
                        logger.onStartingTaskStep(previousStep)
                    }

                    on("that step starting") {
                        logger.onStartingTaskStep(cleanupStep)

                        it("only prints one message to the output") {
                            verify(console, times(1)).println(Text.white("Cleaning up..."))
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
                whenever(failureErrorMessageFormatter.formatErrorMessage(event, runOptions)).doReturn(TextRun("Something went wrong."))

                logger.postEvent(event)

                it("prints the message to the console") {
                    verify(errorConsole).println()
                    verify(errorConsole).println(TextRun("Something went wrong."))
                }
            }

            on("when an 'image built' event is posted") {
                val event = ImageBuiltEvent("/some-image-dir", DockerImage("abc-123"))
                logger.postEvent(event)

                it("prints a message to the output for each container that uses that built image") {
                    verify(console).println(Text.white(Text("Built ") + Text.bold("container-1") + Text(".")))
                    verify(console).println(Text.white(Text("Built ") + Text.bold("container-2") + Text(".")))
                }
            }

            on("when an 'image pulled' event is posted") {
                val event = ImagePulledEvent(DockerImage("the-cool-image:1.2.3"))
                logger.postEvent(event)

                it("prints a message to the output") {
                    verify(console).println(Text.white(Text("Pulled ") + Text.bold("the-cool-image:1.2.3") + Text(".")))
                }
            }

            on("when a 'container started' event is posted") {
                val event = ContainerStartedEvent(container)
                logger.postEvent(event)

                it("prints a message to the output") {
                    verify(console).println(Text.white(Text("Started ") + Text.bold("the-cool-container") + Text(".")))
                }
            }

            on("when a 'container became healthy' event is posted") {
                val event = ContainerBecameHealthyEvent(container)
                logger.postEvent(event)

                it("prints a message to the output") {
                    verify(console).println(Text.white(Text.bold("the-cool-container") + Text(" has become healthy.")))
                }
            }
        }

        on("when the task starts") {
            logger.onTaskStarting("some-task")

            it("prints a message to the output") {
                verify(console).println(Text.white(Text("Running ") + Text.bold("some-task") + Text("...")))
            }
        }

        on("when the task finishes") {
            logger.onTaskFinished("some-task", 234)

            it("prints a message to the output") {
                verify(console).println(Text.white(Text.bold("some-task") + Text(" finished with exit code 234.")))
            }
        }

        describe("when the task fails") {
            given("there are no cleanup instructions") {
                on("when logging that the task has failed") {
                    logger.onTaskFailed("some-task", TextRun())

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
                    logger.onTaskFailed("some-task", TextRun("Do this to clean up."))

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
