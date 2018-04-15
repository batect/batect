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

package batect.execution.model.stages

import batect.config.BuildImage
import batect.config.Configuration
import batect.config.Container
import batect.config.ContainerMap
import batect.config.PullImage
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.docker.DockerContainer
import batect.docker.DockerNetwork
import batect.logging.Logger
import batect.execution.ContainerCommandResolver
import batect.execution.DependencyGraph
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.events.TemporaryFileCreatedEvent
import batect.execution.model.rules.cleanup.CleanupTaskStepRule
import batect.execution.model.rules.cleanup.DeleteTaskNetworkStepRule
import batect.execution.model.rules.cleanup.DeleteTemporaryFileStepRule
import batect.execution.model.rules.cleanup.RemoveContainerStepRule
import batect.execution.model.rules.cleanup.StopContainerStepRule
import batect.os.Command
import batect.testutils.InMemoryLogSink
import batect.testutils.createForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasElement
import com.natpryce.hamkrest.hasSize
import com.natpryce.hamkrest.isEmpty
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.TestContainer
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.nio.file.Paths

object CleanupStagePlannerSpec : Spek({
    describe("a cleanup stage planner") {
        val commandResolver = mock<ContainerCommandResolver> {
            on { resolveCommand(any(), any()) } doReturn Command.parse("do-stuff")
        }

        val task = Task("the-task", TaskRunConfiguration("task-container"))
        val container1 = Container("container-1", BuildImage("./container-1"))
        val container2 = Container("container-2", PullImage("image-2"), dependencies = setOf(container1.name))
        val taskContainer = Container(task.runConfiguration.container, BuildImage("./task-container"), dependencies = setOf(container1.name, container2.name))
        val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer, container1, container2))
        val graph = DependencyGraph(config, task, commandResolver)
        val events by createForEachTest { mutableSetOf<TaskEvent>() }
        val logger = Logger("cleanup-stage-test", InMemoryLogSink())
        val planner = CleanupStagePlanner(logger)

        given("no events were posted") {
            on("creating the stage") {
                val stage = planner.createStage(graph, events)

                it("has no rules") {
                    assertThat(stage.rules, isEmpty)
                }
            }
        }

        given("the task network was created") {
            val network = DockerNetwork("the-network")

            beforeEachTest { events.add(TaskNetworkCreatedEvent(network)) }

            given("no containers were created") {
                given("no temporary files were created") {
                    on("creating the stage") {
                        val stage = planner.createStage(graph, events)

                        itHasExactlyTheRules(stage, mapOf(
                            "delete the task network" to DeleteTaskNetworkStepRule(network, emptySet())
                        ))
                    }
                }

                given("some temporary files were created") {
                    val file1 = Paths.get("/tmp/somefile")
                    val file2 = Paths.get("/tmp/someotherfile")

                    beforeEachTest {
                        events.add(TemporaryFileCreatedEvent(taskContainer, file1))
                        events.add(TemporaryFileCreatedEvent(container1, file2))
                    }

                    on("creating the stage") {
                        val stage = planner.createStage(graph, events)

                        itHasExactlyTheRules(stage, mapOf(
                            "delete the task network" to DeleteTaskNetworkStepRule(network, emptySet()),
                            "delete the first file" to DeleteTemporaryFileStepRule(file1, null),
                            "delete the second file" to DeleteTemporaryFileStepRule(file2, null)
                        ))
                    }
                }
            }

            given("only a single container was created") {
                val dockerContainer = DockerContainer("some-container-id")

                beforeEachTest { events.add(ContainerCreatedEvent(taskContainer, dockerContainer)) }

                given("the container was not started") {
                    given("no temporary files were created") {
                        on("creating the stage") {
                            val stage = planner.createStage(graph, events)

                            itHasExactlyTheRules(stage, mapOf(
                                "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer)),
                                "remove the container" to RemoveContainerStepRule(taskContainer, dockerContainer, false)
                            ))
                        }
                    }

                    given("some temporary files were created") {
                        val file1 = Paths.get("/tmp/somefile")
                        val file2 = Paths.get("/tmp/someotherfile")

                        beforeEachTest {
                            events.add(TemporaryFileCreatedEvent(taskContainer, file1))
                            events.add(TemporaryFileCreatedEvent(container1, file2))
                        }

                        on("creating the stage") {
                            val stage = planner.createStage(graph, events)

                            itHasExactlyTheRules(stage, mapOf(
                                "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer)),
                                "remove the container" to RemoveContainerStepRule(taskContainer, dockerContainer, false),
                                "delete the first file after the container is removed" to DeleteTemporaryFileStepRule(file1, taskContainer),
                                "delete the second file" to DeleteTemporaryFileStepRule(file2, null)
                            ))
                        }
                    }
                }

                given("the container was started") {
                    beforeEachTest { events.add(ContainerStartedEvent(taskContainer)) }

                    given("no temporary files were created") {
                        on("creating the stage") {
                            val stage = planner.createStage(graph, events)

                            itHasExactlyTheRules(stage, mapOf(
                                "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer)),
                                "stop the container" to StopContainerStepRule(taskContainer, dockerContainer, emptySet()),
                                "remove the container after it is stopped" to RemoveContainerStepRule(taskContainer, dockerContainer, true)
                            ))
                        }
                    }

                    given("some temporary files were created") {
                        val file1 = Paths.get("/tmp/somefile")
                        val file2 = Paths.get("/tmp/someotherfile")

                        beforeEachTest {
                            events.add(TemporaryFileCreatedEvent(taskContainer, file1))
                            events.add(TemporaryFileCreatedEvent(container1, file2))
                        }

                        on("creating the stage") {
                            val stage = planner.createStage(graph, events)

                            itHasExactlyTheRules(stage, mapOf(
                                "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer)),
                                "stop the container" to StopContainerStepRule(taskContainer, dockerContainer, emptySet()),
                                "remove the container after it is stopped" to RemoveContainerStepRule(taskContainer, dockerContainer, true),
                                "delete the first file after the container is removed" to DeleteTemporaryFileStepRule(file1, taskContainer),
                                "delete the second file" to DeleteTemporaryFileStepRule(file2, null)
                            ))
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
                    given("no temporary files were created") {
                        on("creating the stage") {
                            val stage = planner.createStage(graph, events)

                            itHasExactlyTheRules(stage, mapOf(
                                "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer, container1, container2)),
                                "remove the task container" to RemoveContainerStepRule(taskContainer, taskDockerContainer, false),
                                "remove the first dependency container" to RemoveContainerStepRule(container1, container1DockerContainer, false),
                                "remove the second dependency container" to RemoveContainerStepRule(container2, container2DockerContainer, false)
                            ))
                        }
                    }

                    given("some temporary files were created") {
                        val file1 = Paths.get("/tmp/somefile")
                        val file2 = Paths.get("/tmp/someotherfile")

                        beforeEachTest {
                            events.add(TemporaryFileCreatedEvent(taskContainer, file1))
                            events.add(TemporaryFileCreatedEvent(container1, file2))
                        }

                        on("creating the stage") {
                            val stage = planner.createStage(graph, events)

                            itHasExactlyTheRules(stage, mapOf(
                                "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer, container1, container2)),
                                "remove the task container" to RemoveContainerStepRule(taskContainer, taskDockerContainer, false),
                                "remove the first dependency container" to RemoveContainerStepRule(container1, container1DockerContainer, false),
                                "remove the second dependency container" to RemoveContainerStepRule(container2, container2DockerContainer, false),
                                "delete the first file after the task container is removed" to DeleteTemporaryFileStepRule(file1, taskContainer),
                                "delete the second file after the associated container is removed" to DeleteTemporaryFileStepRule(file2, container1)
                            ))
                        }
                    }
                }

                given("the container that is depended on by other containers was started") {
                    beforeEachTest {
                        events.add(ContainerStartedEvent(container1))
                    }

                    given("no temporary files were created") {
                        on("creating the stage") {
                            val stage = planner.createStage(graph, events)

                            itHasExactlyTheRules(stage, mapOf(
                                "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer, container1, container2)),
                                "stop the first dependency container" to StopContainerStepRule(container1, container1DockerContainer, emptySet()),
                                "remove the task container" to RemoveContainerStepRule(taskContainer, taskDockerContainer, false),
                                "remove the first dependency container after it is stopped" to RemoveContainerStepRule(container1, container1DockerContainer, true),
                                "remove the second dependency container" to RemoveContainerStepRule(container2, container2DockerContainer, false)
                            ))
                        }
                    }

                    given("some temporary files were created") {
                        val file1 = Paths.get("/tmp/somefile")
                        val file2 = Paths.get("/tmp/someotherfile")

                        beforeEachTest {
                            events.add(TemporaryFileCreatedEvent(taskContainer, file1))
                            events.add(TemporaryFileCreatedEvent(container1, file2))
                        }

                        on("creating the stage") {
                            val stage = planner.createStage(graph, events)

                            itHasExactlyTheRules(stage, mapOf(
                                "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer, container1, container2)),
                                "stop the first dependency container" to StopContainerStepRule(container1, container1DockerContainer, emptySet()),
                                "remove the task container" to RemoveContainerStepRule(taskContainer, taskDockerContainer, false),
                                "remove the first dependency container after it is stopped" to RemoveContainerStepRule(container1, container1DockerContainer, true),
                                "remove the second dependency container" to RemoveContainerStepRule(container2, container2DockerContainer, false),
                                "delete the first file after the task container is removed" to DeleteTemporaryFileStepRule(file1, taskContainer),
                                "delete the second file after the associated container is removed" to DeleteTemporaryFileStepRule(file2, container1)
                            ))
                        }
                    }
                }

                given("all of the containers were started") {
                    beforeEachTest {
                        events.add(ContainerStartedEvent(taskContainer))
                        events.add(ContainerStartedEvent(container1))
                        events.add(ContainerStartedEvent(container2))
                    }

                    given("no temporary files were created") {
                        on("creating the stage") {
                            val stage = planner.createStage(graph, events)

                            itHasExactlyTheRules(stage, mapOf(
                                "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer, container1, container2)),
                                "stop the task container" to StopContainerStepRule(taskContainer, taskDockerContainer, emptySet()),
                                "stop the first dependency container" to StopContainerStepRule(container1, container1DockerContainer, setOf(container2, taskContainer)),
                                "stop the second dependency container" to StopContainerStepRule(container2, container2DockerContainer, setOf(taskContainer)),
                                "remove the task container after it is stopped" to RemoveContainerStepRule(taskContainer, taskDockerContainer, true),
                                "remove the first dependency container after it is stopped" to RemoveContainerStepRule(container1, container1DockerContainer, true),
                                "remove the second dependency container after it is stopped" to RemoveContainerStepRule(container2, container2DockerContainer, true)
                            ))
                        }
                    }

                    given("some temporary files were created") {
                        val file1 = Paths.get("/tmp/somefile")
                        val file2 = Paths.get("/tmp/someotherfile")

                        beforeEachTest {
                            events.add(TemporaryFileCreatedEvent(taskContainer, file1))
                            events.add(TemporaryFileCreatedEvent(container1, file2))
                        }

                        on("creating the stage") {
                            val stage = planner.createStage(graph, events)

                            itHasExactlyTheRules(stage, mapOf(
                                "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer, container1, container2)),
                                "stop the task container" to StopContainerStepRule(taskContainer, taskDockerContainer, emptySet()),
                                "stop the first dependency container" to StopContainerStepRule(container1, container1DockerContainer, setOf(container2, taskContainer)),
                                "stop the second dependency container" to StopContainerStepRule(container2, container2DockerContainer, setOf(taskContainer)),
                                "remove the task container after it is stopped" to RemoveContainerStepRule(taskContainer, taskDockerContainer, true),
                                "remove the first dependency container after it is stopped" to RemoveContainerStepRule(container1, container1DockerContainer, true),
                                "remove the second dependency container after it is stopped" to RemoveContainerStepRule(container2, container2DockerContainer, true),
                                "delete the first file after the task container is removed" to DeleteTemporaryFileStepRule(file1, taskContainer),
                                "delete the second file after the associated container is removed" to DeleteTemporaryFileStepRule(file2, container1)
                            ))
                        }
                    }
                }
            }
        }
    }
})

private fun TestContainer.itHasExactlyTheRules(stage: CleanupStage, expectedRules: Map<String, CleanupTaskStepRule>) {
    expectedRules.forEach { description, expectedRule ->
        it("includes a rule to $description") {
            assertThat(stage.rules, hasElement(expectedRule))
        }
    }

    it("only includes the expected rules") {
        assertThat(stage.rules, hasSize(equalTo(expectedRules.size)))
    }
}
