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

package batect.config

import batect.cli.CommandLineOptions
import batect.config.io.ConfigurationException
import batect.execution.ContainerDoesNotExistException
import batect.os.Command
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.pathResolutionContextDoesNotMatter
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TaskSpecialisedConfigurationFactorySpec : Spek({
    val logger by createLoggerForEachTestWithoutCustomSerializers()

    describe("overriding image sources") {
        val container1 = Container("container-1", BuildImage(LiteralValue("some-build-dir"), pathResolutionContextDoesNotMatter()))
        val container2 = Container("container-2", PullImage("some-image"))
        val rawConfig = createRawConfiguration(container1, container2)
        val task = Task("task-1", TaskRunConfiguration("container-1"))

        given("no overrides") {
            val commandLineOptions = CommandLineOptions(imageOverrides = emptyMap())
            val factory by createForEachTest { TaskSpecialisedConfigurationFactory(rawConfig, commandLineOptions, logger) }

            it("returns the original configuration unmodified") {
                assertThat(factory.create(task), equalTo(createTaskSpecialisedConfiguration(container1, container2)))
            }
        }

        given("a single override") {
            val commandLineOptions = CommandLineOptions(
                imageOverrides = mapOf(
                    container1.name to "another-image"
                )
            )

            val factory by createForEachTest { TaskSpecialisedConfigurationFactory(rawConfig, commandLineOptions, logger) }

            it("returns a new configuration with the image for the given container overridden") {
                assertThat(factory.create(task), equalTo(createTaskSpecialisedConfiguration(Container("container-1", PullImage("another-image")), container2)))
            }
        }

        given("multiple overrides") {
            val commandLineOptions = CommandLineOptions(
                imageOverrides = mapOf(
                    container1.name to "another-image",
                    container2.name to "another-other-image",
                )
            )

            val factory by createForEachTest { TaskSpecialisedConfigurationFactory(rawConfig, commandLineOptions, logger) }

            it("returns a new configuration with the images for the given containers overridden") {
                assertThat(
                    factory.create(task),
                    equalTo(
                        createTaskSpecialisedConfiguration(
                            Container("container-1", PullImage("another-image")),
                            Container("container-2", PullImage("another-other-image"))
                        )
                    )
                )
            }
        }

        given("an override for a container that doesn't exist") {
            val commandLineOptions = CommandLineOptions(
                imageOverrides = mapOf(
                    "another-container" to "another-image"
                )
            )

            val factory by createForEachTest { TaskSpecialisedConfigurationFactory(rawConfig, commandLineOptions, logger) }

            it("throws an appropriate exception") {
                assertThat(
                    { factory.create(task) },
                    throws<ConfigurationException>(withMessage("Cannot override image for container 'another-container' because there is no container named 'another-container' defined."))
                )
            }
        }
    }

    describe("applying configuration for the main task container") {
        val container1 = Container(
            "container-1",
            BuildImage(LiteralValue("some-build-dir"), pathResolutionContextDoesNotMatter()),
            command = Command.parse("original container-1 command"),
            entrypoint = Command.parse("original container-1 entrypoint"),
            workingDirectory = "/some/work/dir",
            portMappings = setOf(PortMapping(123, 456)),
            environment = mapOf("SOME_VAR" to LiteralValue("SOME_VAR_VALUE"), "SOME_OTHER_VAR" to LiteralValue("SOME_OTHER_VAR_VALUE"))
        )

        val container2 = Container(
            "container-2",
            PullImage("some-image"),
            command = Command.parse("original container-2 command"),
            entrypoint = Command.parse("original container-2 entrypoint"),
            workingDirectory = "/some/other/work/dir",
            portMappings = setOf(PortMapping(789, 1000)),
            environment = mapOf("SOME_VAR" to LiteralValue("SOME_VAR_VALUE"), "SOME_OTHER_VAR" to LiteralValue("SOME_OTHER_VAR_VALUE"))
        )

        val container3 = Container(
            "container-3",
            PullImage("some-other-image"),
            command = null
        )

        val rawConfig = createRawConfiguration(container1, container2, container3)
        val mainTaskName = "task-1"

        given("there are no additional arguments for the main task container from the command line") {
            val commandLineOptions = CommandLineOptions(taskName = mainTaskName)
            val factory by createForEachTest { TaskSpecialisedConfigurationFactory(rawConfig, commandLineOptions, logger) }

            given("the task does not contain any customisation for the main task container") {
                given("the task container contains an explicit command") {
                    val task = Task(mainTaskName, TaskRunConfiguration("container-1"))
                    val taskSpecialisedConfig by createForEachTest { factory.create(task) }

                    it("returns the main task container unchanged") {
                        assertThat(taskSpecialisedConfig.containers[container1.name], equalTo(container1))
                    }

                    it("returns the other containers unchanged") {
                        assertThat(taskSpecialisedConfig.containers[container2.name], equalTo(container2))
                        assertThat(taskSpecialisedConfig.containers[container3.name], equalTo(container3))
                    }
                }

                given("the task container does not contain an explicit command") {
                    val task = Task(mainTaskName, TaskRunConfiguration("container-3"))
                    val taskSpecialisedConfig by createForEachTest { factory.create(task) }

                    it("returns the main task container unchanged") {
                        assertThat(taskSpecialisedConfig.containers[container3.name], equalTo(container3))
                    }

                    it("returns the other containers unchanged") {
                        assertThat(taskSpecialisedConfig.containers[container1.name], equalTo(container1))
                        assertThat(taskSpecialisedConfig.containers[container2.name], equalTo(container2))
                    }
                }
            }

            given("the task does contain customisations for the main task container") {
                val task = Task(
                    mainTaskName,
                    TaskRunConfiguration(
                        "container-1",
                        Command.parse("overridden command"),
                        Command.parse("overridden entrypoint"),
                        mapOf("SOME_VAR" to LiteralValue("SOME_VAR_REPLACED"), "SOME_NEW_VAR" to LiteralValue("SOME_NEW_VALUE")),
                        setOf(PortMapping(2000, 3000)),
                        "/task/specific/work/dir"
                    )
                )

                val taskSpecialisedConfig by createForEachTest { factory.create(task) }

                it("returns the main task container with customisations applied") {
                    assertThat(
                        taskSpecialisedConfig.containers[container1.name],
                        equalTo(
                            Container(
                                "container-1",
                                container1.imageSource,
                                command = Command.parse("overridden command"),
                                entrypoint = Command.parse("overridden entrypoint"),
                                workingDirectory = "/task/specific/work/dir",
                                portMappings = setOf(PortMapping(123, 456), PortMapping(2000, 3000)),
                                environment = mapOf(
                                    "SOME_VAR" to LiteralValue("SOME_VAR_REPLACED"),
                                    "SOME_OTHER_VAR" to LiteralValue("SOME_OTHER_VAR_VALUE"),
                                    "SOME_NEW_VAR" to LiteralValue("SOME_NEW_VALUE")
                                )
                            )
                        )
                    )
                }

                it("returns the other containers unchanged") {
                    assertThat(taskSpecialisedConfig.containers[container2.name], equalTo(container2))
                    assertThat(taskSpecialisedConfig.containers[container3.name], equalTo(container3))
                }
            }

            given("the main task container doesn't exist") {
                val task = Task(mainTaskName, TaskRunConfiguration("container-5"))

                it("throws an appropriate exception") {
                    assertThat({ factory.create(task) }, throws<ContainerDoesNotExistException>(withMessage("The container 'container-5' referenced by task 'task-1' does not exist.")))
                }
            }
        }

        given("there are additional arguments for the main task container on the command line") {
            val commandLineOptions = CommandLineOptions(taskName = mainTaskName, additionalTaskCommandArguments = listOf("--some-extra-arg"))
            val factory by createForEachTest { TaskSpecialisedConfigurationFactory(rawConfig, commandLineOptions, logger) }

            given("we are generating specialised config for the main task") {
                given("the task contains an overridden command") {
                    val task = Task(mainTaskName, TaskRunConfiguration("container-1", command = Command.parse("task command")))
                    val taskSpecialisedConfig by createForEachTest { factory.create(task) }

                    it("returns the main task container with the extra command line arguments appended to the command from the task") {
                        assertThat(taskSpecialisedConfig.containers.getValue(container1.name).command, equalTo(task.runConfiguration!!.command!!.plus(listOf("--some-extra-arg"))))
                    }

                    it("returns the other containers unchanged") {
                        assertThat(taskSpecialisedConfig.containers[container2.name], equalTo(container2))
                        assertThat(taskSpecialisedConfig.containers[container3.name], equalTo(container3))
                    }
                }

                given("the task does not contain an overridden command") {
                    given("the container has an explicit command") {
                        val task = Task(mainTaskName, TaskRunConfiguration("container-1"))
                        val taskSpecialisedConfig by createForEachTest { factory.create(task) }

                        it("returns the main task container with the extra command line arguments appended to the command from the command") {
                            assertThat(taskSpecialisedConfig.containers.getValue(container1.name).command, equalTo(container1.command!!.plus(listOf("--some-extra-arg"))))
                        }

                        it("returns the other containers unchanged") {
                            assertThat(taskSpecialisedConfig.containers[container2.name], equalTo(container2))
                            assertThat(taskSpecialisedConfig.containers[container3.name], equalTo(container3))
                        }
                    }

                    given("neither the task nor the container have an explicit command") {
                        val task = Task(mainTaskName, TaskRunConfiguration("container-3"))

                        it("throws an appropriate exception") {
                            assertThat({ factory.create(task) }, throws<ContainerCommandResolutionException>(withMessage("Additional command line arguments for the task have been provided, but neither the task (task-1) nor the main task container (container-3) have an explicit command in the configuration file.")))
                        }
                    }
                }
            }

            given("we are generating specialised config for a prerequisite task") {
                val task = Task("prereq-task", TaskRunConfiguration("container-1"))
                val taskSpecialisedConfig by createForEachTest { factory.create(task) }

                it("returns the main task container unchanged") {
                    assertThat(taskSpecialisedConfig.containers[container1.name], equalTo(container1))
                }

                it("returns the other containers unchanged") {
                    assertThat(taskSpecialisedConfig.containers[container2.name], equalTo(container2))
                    assertThat(taskSpecialisedConfig.containers[container3.name], equalTo(container3))
                }
            }
        }
    }

    describe("applying customisations for dependency containers") {
        val container1 = Container(
            "container-1",
            BuildImage(LiteralValue("some-build-dir"), pathResolutionContextDoesNotMatter()),
            command = Command.parse("original container-1 command"),
            entrypoint = Command.parse("original container-1 entrypoint"),
            workingDirectory = "/some/work/dir",
            portMappings = setOf(PortMapping(123, 456)),
            environment = mapOf("SOME_VAR" to LiteralValue("SOME_VAR_VALUE"), "SOME_OTHER_VAR" to LiteralValue("SOME_OTHER_VAR_VALUE"))
        )

        val container2 = Container(
            "container-2",
            PullImage("some-image"),
            command = Command.parse("original container-2 command"),
            entrypoint = Command.parse("original container-2 entrypoint"),
            workingDirectory = "/some/other/work/dir",
            portMappings = setOf(PortMapping(789, 1000)),
            environment = mapOf("SOME_VAR" to LiteralValue("SOME_VAR_VALUE"), "SOME_OTHER_VAR" to LiteralValue("SOME_OTHER_VAR_VALUE"))
        )

        val container3 = Container(
            "container-3",
            PullImage("some-other-image"),
            command = null
        )

        val rawConfig = createRawConfiguration(container1, container2, container3)
        val mainTaskName = "task-1"
        val commandLineOptions = CommandLineOptions(taskName = mainTaskName)
        val factory by createForEachTest { TaskSpecialisedConfigurationFactory(rawConfig, commandLineOptions, logger) }

        given("the task contains no customisations") {
            val task = Task(mainTaskName, TaskRunConfiguration("container-1"), customisations = emptyMap())
            val taskSpecialisedConfig by createForEachTest { factory.create(task) }

            it("returns the main task container unchanged") {
                assertThat(taskSpecialisedConfig.containers[container1.name], equalTo(container1))
            }

            it("returns the other containers unchanged") {
                assertThat(taskSpecialisedConfig.containers[container2.name], equalTo(container2))
                assertThat(taskSpecialisedConfig.containers[container3.name], equalTo(container3))
            }
        }

        given("the task contains customisation for a dependency container") {
            val task = Task(
                mainTaskName,
                TaskRunConfiguration("container-1"),
                customisations = mapOf(
                    "container-2" to TaskContainerCustomisation(
                        mapOf("SOME_VAR" to LiteralValue("SOME_VAR_REPLACED"), "SOME_NEW_VAR" to LiteralValue("SOME_NEW_VALUE")),
                        setOf(PortMapping(2000, 3000)),
                        "/task/specific/work/dir"
                    )
                )
            )

            val taskSpecialisedConfig by createForEachTest { factory.create(task) }

            it("returns the main task container unchanged") {
                assertThat(taskSpecialisedConfig.containers[container1.name], equalTo(container1))
            }

            it("applies the customisations to the dependency container") {
                assertThat(
                    taskSpecialisedConfig.containers[container2.name],
                    equalTo(
                        container2.copy(
                            environment = mapOf(
                                "SOME_VAR" to LiteralValue("SOME_VAR_REPLACED"),
                                "SOME_NEW_VAR" to LiteralValue("SOME_NEW_VALUE"),
                                "SOME_OTHER_VAR" to LiteralValue("SOME_OTHER_VAR_VALUE")
                            ),
                            portMappings = setOf(PortMapping(789, 1000), PortMapping(2000, 3000)),
                            workingDirectory = "/task/specific/work/dir"
                        )
                    )
                )
            }

            it("returns the other container unchanged") {
                assertThat(taskSpecialisedConfig.containers[container3.name], equalTo(container3))
            }
        }

        given("the task contains customisation for a dependency container with no changes applied") {
            val task = Task(
                mainTaskName,
                TaskRunConfiguration("container-1"),
                customisations = mapOf(
                    "container-2" to TaskContainerCustomisation()
                )
            )

            val taskSpecialisedConfig by createForEachTest { factory.create(task) }

            it("returns the main task container unchanged") {
                assertThat(taskSpecialisedConfig.containers[container1.name], equalTo(container1))
            }

            it("returns the dependency container unchanged") {
                assertThat(taskSpecialisedConfig.containers[container2.name], equalTo(container2))
            }

            it("returns the other container unchanged") {
                assertThat(taskSpecialisedConfig.containers[container3.name], equalTo(container3))
            }
        }

        given("the task contains customisation for a container that does not exist") {
            val task = Task(
                mainTaskName,
                TaskRunConfiguration("container-1"),
                customisations = mapOf(
                    "container-5" to TaskContainerCustomisation()
                )
            )

            it("throws an appropriate exception") {
                assertThat({ factory.create(task) }, throws<ConfigurationException>(withMessage("Cannot apply customisations to container 'container-5' because there is no container named 'container-5' defined.")))
            }
        }
    }
})

private fun createRawConfiguration(vararg containers: Container): RawConfiguration = RawConfiguration("my_project", TaskMap(), ContainerMap(*containers))
private fun createTaskSpecialisedConfiguration(vararg containers: Container): TaskSpecialisedConfiguration =
    TaskSpecialisedConfiguration("my_project", TaskMap(), ContainerMap(*containers))
