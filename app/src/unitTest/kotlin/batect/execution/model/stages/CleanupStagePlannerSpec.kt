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

package batect.execution.model.stages

import batect.config.BuildImage
import batect.config.Container
import batect.config.ContainerMap
import batect.config.LiteralValue
import batect.config.PullImage
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.config.TaskSpecialisedConfiguration
import batect.docker.DockerContainer
import batect.docker.DockerNetwork
import batect.execution.ContainerDependencyGraph
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.events.TemporaryDirectoryCreatedEvent
import batect.execution.model.events.TemporaryFileCreatedEvent
import batect.execution.model.rules.cleanup.CleanupTaskStepRule
import batect.execution.model.rules.cleanup.DeleteTaskNetworkStepRule
import batect.execution.model.rules.cleanup.DeleteTemporaryDirectoryStepRule
import batect.execution.model.rules.cleanup.DeleteTemporaryFileStepRule
import batect.execution.model.rules.cleanup.RemoveContainerStepRule
import batect.execution.model.rules.cleanup.StopContainerStepRule
import batect.os.OperatingSystem
import batect.os.SystemInfo
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.given
import batect.testutils.on
import batect.testutils.pathResolutionContextDoesNotMatter
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasElement
import com.natpryce.hamkrest.hasSize
import com.natpryce.hamkrest.isEmpty
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths

object CleanupStagePlannerSpec : Spek({
    describe("a cleanup stage planner") {
        val systemInfo = mock<SystemInfo> {
            on { operatingSystem } doReturn OperatingSystem.Other
        }

        val task = Task("the-task", TaskRunConfiguration("task-container"))
        val container1 = Container("container-1", BuildImage(LiteralValue("./container-1"), pathResolutionContextDoesNotMatter()))
        val container2 = Container("container-2", PullImage("image-2"), dependencies = setOf(container1.name))
        val taskContainer = Container(task.runConfiguration!!.container, BuildImage(LiteralValue("./task-container"), pathResolutionContextDoesNotMatter()), dependencies = setOf(container1.name, container2.name))
        val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer, container1, container2))
        val graph = ContainerDependencyGraph(config, task)
        val events by createForEachTest { mutableSetOf<TaskEvent>() }
        val logger by createLoggerForEachTest()
        val planner by createForEachTest { CleanupStagePlanner(graph, systemInfo, logger) }

        given("no events were posted") {
            on("creating the stage") {
                val stage by runForEachTest { planner.createStage(events) }

                it("has no rules") {
                    assertThat(stage.rules, isEmpty)
                }

                it("propagates the operating system") {
                    assertThat(stage.operatingSystem, equalTo(OperatingSystem.Other))
                }
            }
        }

        given("the task network was created") {
            val network = DockerNetwork("the-network")

            beforeEachTest { events.add(TaskNetworkCreatedEvent(network)) }

            given("no containers were created") {
                given("no temporary files or directories were created") {
                    on("creating the stage") {
                        itHasExactlyTheRules(
                            { planner.createStage(events) },
                            mapOf(
                                "delete the task network" to DeleteTaskNetworkStepRule(network, emptySet())
                            )
                        )
                    }
                }

                given("some temporary files and directories were created") {
                    val file1 = Paths.get("/tmp/somefile")
                    val file2 = Paths.get("/tmp/someotherfile")
                    val directory = Paths.get("/tmp/somedirectory")

                    beforeEachTest {
                        events.add(TemporaryFileCreatedEvent(taskContainer, file1))
                        events.add(TemporaryFileCreatedEvent(container1, file2))
                        events.add(TemporaryDirectoryCreatedEvent(container1, directory))
                    }

                    on("creating the stage") {
                        itHasExactlyTheRules(
                            { planner.createStage(events) },
                            mapOf(
                                "delete the task network" to DeleteTaskNetworkStepRule(network, emptySet()),
                                "delete the first file" to DeleteTemporaryFileStepRule(file1, null),
                                "delete the second file" to DeleteTemporaryFileStepRule(file2, null),
                                "delete the directory" to DeleteTemporaryDirectoryStepRule(directory, null)
                            )
                        )
                    }
                }
            }

            given("only a single container was created") {
                val dockerContainer = DockerContainer("some-container-id")

                beforeEachTest { events.add(ContainerCreatedEvent(taskContainer, dockerContainer)) }

                given("the container was not started") {
                    given("no temporary files or directories were created") {
                        on("creating the stage") {
                            itHasExactlyTheRules(
                                { planner.createStage(events) },
                                mapOf(
                                    "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer)),
                                    "remove the container" to RemoveContainerStepRule(taskContainer, dockerContainer, false)
                                )
                            )
                        }
                    }

                    given("some temporary files and directories were created") {
                        val file1 = Paths.get("/tmp/somefile")
                        val file2 = Paths.get("/tmp/someotherfile")
                        val directory1 = Paths.get("/tmp/somedirectory")
                        val directory2 = Paths.get("/tmp/someotherdirectory")

                        beforeEachTest {
                            events.add(TemporaryFileCreatedEvent(taskContainer, file1))
                            events.add(TemporaryFileCreatedEvent(container1, file2))
                            events.add(TemporaryDirectoryCreatedEvent(taskContainer, directory1))
                            events.add(TemporaryDirectoryCreatedEvent(container1, directory2))
                        }

                        on("creating the stage") {
                            itHasExactlyTheRules(
                                { planner.createStage(events) },
                                mapOf(
                                    "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer)),
                                    "remove the container" to RemoveContainerStepRule(taskContainer, dockerContainer, false),
                                    "delete the first file after the container is removed" to DeleteTemporaryFileStepRule(file1, taskContainer),
                                    "delete the second file" to DeleteTemporaryFileStepRule(file2, null),
                                    "delete the first directory after the container is removed" to DeleteTemporaryDirectoryStepRule(directory1, taskContainer),
                                    "delete the second directory" to DeleteTemporaryDirectoryStepRule(directory2, null)
                                )
                            )
                        }
                    }
                }

                given("the container was started") {
                    beforeEachTest { events.add(ContainerStartedEvent(taskContainer)) }

                    given("no temporary files or directories were created") {
                        on("creating the stage") {
                            itHasExactlyTheRules(
                                { planner.createStage(events) },
                                mapOf(
                                    "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer)),
                                    "stop the container" to StopContainerStepRule(taskContainer, dockerContainer, emptySet()),
                                    "remove the container after it is stopped" to RemoveContainerStepRule(taskContainer, dockerContainer, true)
                                )
                            )
                        }
                    }

                    given("some temporary files and directories were created") {
                        val file1 = Paths.get("/tmp/somefile")
                        val file2 = Paths.get("/tmp/someotherfile")
                        val directory1 = Paths.get("/tmp/somedirectory")
                        val directory2 = Paths.get("/tmp/someotherdirectory")

                        beforeEachTest {
                            events.add(TemporaryFileCreatedEvent(taskContainer, file1))
                            events.add(TemporaryFileCreatedEvent(container1, file2))
                            events.add(TemporaryDirectoryCreatedEvent(taskContainer, directory1))
                            events.add(TemporaryDirectoryCreatedEvent(container1, directory2))
                        }

                        on("creating the stage") {
                            itHasExactlyTheRules(
                                { planner.createStage(events) },
                                mapOf(
                                    "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer)),
                                    "stop the container" to StopContainerStepRule(taskContainer, dockerContainer, emptySet()),
                                    "remove the container after it is stopped" to RemoveContainerStepRule(taskContainer, dockerContainer, true),
                                    "delete the first file after the container is removed" to DeleteTemporaryFileStepRule(file1, taskContainer),
                                    "delete the second file" to DeleteTemporaryFileStepRule(file2, null),
                                    "delete the first directory after the container is removed" to DeleteTemporaryDirectoryStepRule(directory1, taskContainer),
                                    "delete the second directory" to DeleteTemporaryDirectoryStepRule(directory2, null)
                                )
                            )
                        }
                    }
                }
            }

            given("all of the containers were created") {
                val taskDockerContainer = DockerContainer("task-container-id")
                val container1DockerContainer = DockerContainer("container-1-id")
                val container2DockerContainer = DockerContainer("container-2-id")

                beforeEachTest {
                    events.add(ContainerCreatedEvent(taskContainer, taskDockerContainer))
                    events.add(ContainerCreatedEvent(container1, container1DockerContainer))
                    events.add(ContainerCreatedEvent(container2, container2DockerContainer))
                }

                given("none of the containers were started") {
                    given("no temporary files or directories were created") {
                        on("creating the stage") {
                            itHasExactlyTheRules(
                                { planner.createStage(events) },
                                mapOf(
                                    "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer, container1, container2)),
                                    "remove the task container" to RemoveContainerStepRule(taskContainer, taskDockerContainer, false),
                                    "remove the first dependency container" to RemoveContainerStepRule(container1, container1DockerContainer, false),
                                    "remove the second dependency container" to RemoveContainerStepRule(container2, container2DockerContainer, false)
                                )
                            )
                        }
                    }

                    given("some temporary files and directories were created") {
                        val file1 = Paths.get("/tmp/somefile")
                        val file2 = Paths.get("/tmp/someotherfile")
                        val directory1 = Paths.get("/tmp/somedirectory")
                        val directory2 = Paths.get("/tmp/someotherdirectory")

                        beforeEachTest {
                            events.add(TemporaryFileCreatedEvent(taskContainer, file1))
                            events.add(TemporaryFileCreatedEvent(container1, file2))
                            events.add(TemporaryDirectoryCreatedEvent(taskContainer, directory1))
                            events.add(TemporaryDirectoryCreatedEvent(container1, directory2))
                        }

                        on("creating the stage") {
                            itHasExactlyTheRules(
                                { planner.createStage(events) },
                                mapOf(
                                    "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer, container1, container2)),
                                    "remove the task container" to RemoveContainerStepRule(taskContainer, taskDockerContainer, false),
                                    "remove the first dependency container" to RemoveContainerStepRule(container1, container1DockerContainer, false),
                                    "remove the second dependency container" to RemoveContainerStepRule(container2, container2DockerContainer, false),
                                    "delete the first file after the task container is removed" to DeleteTemporaryFileStepRule(file1, taskContainer),
                                    "delete the second file after the associated container is removed" to DeleteTemporaryFileStepRule(file2, container1),
                                    "delete the first directory after the container is removed" to DeleteTemporaryDirectoryStepRule(directory1, taskContainer),
                                    "delete the second directory after the associated container is removed" to DeleteTemporaryDirectoryStepRule(directory2, container1)
                                )
                            )
                        }
                    }
                }

                given("the container that is depended on by other containers was started") {
                    beforeEachTest {
                        events.add(ContainerStartedEvent(container1))
                    }

                    given("no temporary files or directories were created") {
                        on("creating the stage") {
                            itHasExactlyTheRules(
                                { planner.createStage(events) },
                                mapOf(
                                    "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer, container1, container2)),
                                    "stop the first dependency container" to StopContainerStepRule(container1, container1DockerContainer, emptySet()),
                                    "remove the task container" to RemoveContainerStepRule(taskContainer, taskDockerContainer, false),
                                    "remove the first dependency container after it is stopped" to RemoveContainerStepRule(container1, container1DockerContainer, true),
                                    "remove the second dependency container" to RemoveContainerStepRule(container2, container2DockerContainer, false)
                                )
                            )
                        }
                    }

                    given("some temporary files and directories were created") {
                        val file1 = Paths.get("/tmp/somefile")
                        val file2 = Paths.get("/tmp/someotherfile")
                        val directory1 = Paths.get("/tmp/somedirectory")
                        val directory2 = Paths.get("/tmp/someotherdirectory")

                        beforeEachTest {
                            events.add(TemporaryFileCreatedEvent(taskContainer, file1))
                            events.add(TemporaryFileCreatedEvent(container1, file2))
                            events.add(TemporaryDirectoryCreatedEvent(taskContainer, directory1))
                            events.add(TemporaryDirectoryCreatedEvent(container1, directory2))
                        }

                        on("creating the stage") {
                            itHasExactlyTheRules(
                                { planner.createStage(events) },
                                mapOf(
                                    "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer, container1, container2)),
                                    "stop the first dependency container" to StopContainerStepRule(container1, container1DockerContainer, emptySet()),
                                    "remove the task container" to RemoveContainerStepRule(taskContainer, taskDockerContainer, false),
                                    "remove the first dependency container after it is stopped" to RemoveContainerStepRule(container1, container1DockerContainer, true),
                                    "remove the second dependency container" to RemoveContainerStepRule(container2, container2DockerContainer, false),
                                    "delete the first file after the task container is removed" to DeleteTemporaryFileStepRule(file1, taskContainer),
                                    "delete the second file after the associated container is removed" to DeleteTemporaryFileStepRule(file2, container1),
                                    "delete the first directory after the container is removed" to DeleteTemporaryDirectoryStepRule(directory1, taskContainer),
                                    "delete the second directory after the associated container is removed" to DeleteTemporaryDirectoryStepRule(directory2, container1)
                                )
                            )
                        }
                    }
                }

                given("all of the containers were started") {
                    beforeEachTest {
                        events.add(ContainerStartedEvent(taskContainer))
                        events.add(ContainerStartedEvent(container1))
                        events.add(ContainerStartedEvent(container2))
                    }

                    given("no temporary files or directories were created") {
                        on("creating the stage") {
                            itHasExactlyTheRules(
                                { planner.createStage(events) },
                                mapOf(
                                    "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer, container1, container2)),
                                    "stop the task container" to StopContainerStepRule(taskContainer, taskDockerContainer, emptySet()),
                                    "stop the first dependency container" to StopContainerStepRule(container1, container1DockerContainer, setOf(container2, taskContainer)),
                                    "stop the second dependency container" to StopContainerStepRule(container2, container2DockerContainer, setOf(taskContainer)),
                                    "remove the task container after it is stopped" to RemoveContainerStepRule(taskContainer, taskDockerContainer, true),
                                    "remove the first dependency container after it is stopped" to RemoveContainerStepRule(container1, container1DockerContainer, true),
                                    "remove the second dependency container after it is stopped" to RemoveContainerStepRule(container2, container2DockerContainer, true)
                                )
                            )
                        }
                    }

                    given("some temporary files and directories were created") {
                        val file1 = Paths.get("/tmp/somefile")
                        val file2 = Paths.get("/tmp/someotherfile")
                        val directory1 = Paths.get("/tmp/somedirectory")
                        val directory2 = Paths.get("/tmp/someotherdirectory")

                        beforeEachTest {
                            events.add(TemporaryFileCreatedEvent(taskContainer, file1))
                            events.add(TemporaryFileCreatedEvent(container1, file2))
                            events.add(TemporaryDirectoryCreatedEvent(taskContainer, directory1))
                            events.add(TemporaryDirectoryCreatedEvent(container1, directory2))
                        }

                        on("creating the stage") {
                            itHasExactlyTheRules(
                                { planner.createStage(events) },
                                mapOf(
                                    "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer, container1, container2)),
                                    "stop the task container" to StopContainerStepRule(taskContainer, taskDockerContainer, emptySet()),
                                    "stop the first dependency container" to StopContainerStepRule(container1, container1DockerContainer, setOf(container2, taskContainer)),
                                    "stop the second dependency container" to StopContainerStepRule(container2, container2DockerContainer, setOf(taskContainer)),
                                    "remove the task container after it is stopped" to RemoveContainerStepRule(taskContainer, taskDockerContainer, true),
                                    "remove the first dependency container after it is stopped" to RemoveContainerStepRule(container1, container1DockerContainer, true),
                                    "remove the second dependency container after it is stopped" to RemoveContainerStepRule(container2, container2DockerContainer, true),
                                    "delete the first file after the task container is removed" to DeleteTemporaryFileStepRule(file1, taskContainer),
                                    "delete the second file after the associated container is removed" to DeleteTemporaryFileStepRule(file2, container1),
                                    "delete the first directory after the container is removed" to DeleteTemporaryDirectoryStepRule(directory1, taskContainer),
                                    "delete the second directory after the associated container is removed" to DeleteTemporaryDirectoryStepRule(directory2, container1)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
})

private fun Suite.itHasExactlyTheRules(stageCreator: () -> CleanupStage, expectedRules: Map<String, CleanupTaskStepRule>) {
    val stage by runForEachTest(stageCreator)

    expectedRules.forEach { (description, expectedRule) ->
        it("includes a rule to $description") {
            assertThat(stage.rules, hasElement(expectedRule))
        }
    }

    it("only includes the expected rules") {
        assertThat(stage.rules, hasSize(equalTo(expectedRules.size)))
    }
}
