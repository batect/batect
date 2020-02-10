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

package batect.execution

import batect.config.Container
import batect.config.Task
import batect.config.TaskRunConfiguration
import batect.os.Command
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ContainerCommandResolverSpec : Spek({
    describe("a container command resolver") {
        given("the container is the task container") {
            given("the task is the main task") {
                val taskName = "main-task"

                given("additional command line arguments for the task container have been provided") {
                    val runOptions = runOptionsForTask(taskName, listOf("extra-arg-1", "extra-arg-2"))
                    val resolver = ContainerCommandResolver(runOptions)

                    given("the task defines a command for the container to run") {
                        val container = Container("some-container", imageSourceDoesNotMatter(), Command.parse("some-container-command"))
                        val task = Task(taskName, TaskRunConfiguration(container.name, Command.parse("some-task-command")))

                        on("resolving the command for the container") {
                            val command = resolver.resolveCommand(container, task)

                            it("returns the command from the task with the additional command line arguments") {
                                assertThat(command, equalTo(Command.parse("some-task-command extra-arg-1 extra-arg-2")))
                            }
                        }
                    }

                    given("the task does not define a command for the container to run") {
                        val containerName = "some-container"
                        val task = Task(taskName, TaskRunConfiguration(containerName, command = null))

                        given("the container defines a command to run") {
                            val container = Container(containerName, imageSourceDoesNotMatter(), Command.parse("some-command"))

                            on("resolving the command for the container") {
                                val command = resolver.resolveCommand(container, task)

                                it("returns the command from the container with the additional command line arguments") {
                                    assertThat(command, equalTo(Command.parse("some-command extra-arg-1 extra-arg-2")))
                                }
                            }
                        }

                        given("the container does not define a command to run") {
                            val container = Container(containerName, imageSourceDoesNotMatter(), command = null)

                            on("resolving the command for the container") {
                                it("throws an appropriate exception") {
                                    assertThat({ resolver.resolveCommand(container, task) },
                                        throws<ContainerCommandResolutionException>(withMessage("Additional command line arguments for the task have been provided, but neither the task ($taskName) nor the main task container ($containerName) have an explicit command in the configuration file.")))
                                }
                            }
                        }
                    }
                }

                given("additional command line arguments for the task container have not been provided") {
                    val runOptions = runOptionsForTask(taskName, emptyList())
                    val resolver = ContainerCommandResolver(runOptions)

                    given("the task defines a command for the container to run") {
                        val container = Container("the-container", imageSourceDoesNotMatter(), Command.parse("the-container-command"))
                        val task = Task(taskName, TaskRunConfiguration(container.name, Command.parse("the-task-command")))

                        on("resolving the command for the container") {
                            val command = resolver.resolveCommand(container, task)

                            it("returns the command from the task") {
                                assertThat(command, equalTo(task.runConfiguration.command))
                            }
                        }
                    }

                    given("the task does not define a command for the container to run") {
                        val container = Container("the-container", imageSourceDoesNotMatter(), Command.parse("the-container-command"))
                        val task = Task(taskName, TaskRunConfiguration(container.name, command = null))

                        on("resolving the command for the container") {
                            val command = resolver.resolveCommand(container, task)

                            it("returns the command from the container") {
                                assertThat(command, equalTo(container.command))
                            }
                        }
                    }
                }
            }

            given("the task is not the main task") {
                val taskName = "other-task"
                val runOptions = runOptionsForTask("main-task", listOf("extra-arg-1", "extra-arg-2"))
                val resolver = ContainerCommandResolver(runOptions)

                given("the task defines a command for the container to run") {
                    val container = Container("the-container", imageSourceDoesNotMatter(), Command.parse("the-container-command"))
                    val task = Task(taskName, TaskRunConfiguration(container.name, Command.parse("the-task-command")))

                    on("resolving the command for the container") {
                        val command = resolver.resolveCommand(container, task)

                        it("returns the command from the task") {
                            assertThat(command, equalTo(task.runConfiguration.command))
                        }
                    }
                }

                given("the task does not define a command for the container to run") {
                    val container = Container("the-container", imageSourceDoesNotMatter(), Command.parse("the-container-command"))
                    val task = Task(taskName, TaskRunConfiguration(container.name, command = null))

                    on("resolving the command for the container") {
                        val command = resolver.resolveCommand(container, task)

                        it("returns the command from the container") {
                            assertThat(command, equalTo(container.command))
                        }
                    }
                }
            }
        }

        given("the container is not the task container") {
            val container = Container("the-container", imageSourceDoesNotMatter(), Command.parse("the-container-command"))
            val task = Task("main-task", TaskRunConfiguration("main-container", Command.parse("the-task-command")))
            val runOptions = runOptionsForTask(task.name, listOf("extra-arg-1", "extra-arg-2"))
            val resolver = ContainerCommandResolver(runOptions)

            on("resolving the command for the container") {
                val command = resolver.resolveCommand(container, task)

                it("returns the command from the container") {
                    assertThat(command, equalTo(container.command))
                }
            }
        }
    }
})

private fun runOptionsForTask(taskName: String, additionalCommandLineArguments: Iterable<String>) =
    RunOptions(taskName, additionalCommandLineArguments, CleanupOption.Cleanup, CleanupOption.Cleanup, false, emptyMap())
