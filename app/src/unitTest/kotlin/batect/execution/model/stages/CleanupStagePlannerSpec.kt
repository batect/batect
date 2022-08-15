/*
    Copyright 2017-2022 Charles Korn.

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
import batect.dockerclient.NetworkReference
import batect.execution.CleanupOption
import batect.execution.ContainerDependencyGraph
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.rules.cleanup.CleanupTaskStepRule
import batect.execution.model.rules.cleanup.DeleteTaskNetworkStepRule
import batect.execution.model.rules.cleanup.RemoveContainerStepRule
import batect.execution.model.rules.cleanup.StopContainerStepRule
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
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe

object CleanupStagePlannerSpec : Spek({
    describe("a cleanup stage planner") {
        val task = Task("the-task", TaskRunConfiguration("task-container"))
        val container1 = Container("container-1", BuildImage(LiteralValue("./container-1"), pathResolutionContextDoesNotMatter()))
        val container2 = Container("container-2", PullImage("image-2"), dependencies = setOf(container1.name))
        val taskContainer = Container(task.runConfiguration!!.container, BuildImage(LiteralValue("./task-container"), pathResolutionContextDoesNotMatter()), dependencies = setOf(container1.name, container2.name))
        val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer, container1, container2))
        val graph = ContainerDependencyGraph(config, task)
        val events by createForEachTest { mutableSetOf<TaskEvent>() }
        val logger by createLoggerForEachTest()
        val planner by createForEachTest { CleanupStagePlanner(graph, logger) }

        given("no events were posted") {
            given("automatic cleanup is being performed") {
                on("creating the stage") {
                    val stage by runForEachTest { planner.createStage(events, CleanupOption.Cleanup) }

                    it("has no rules") {
                        assertThat(stage.rules, isEmpty)
                    }

                    it("has no manual cleanup commands") {
                        assertThat(stage.manualCleanupCommands, isEmpty)
                    }
                }
            }

            given("manual cleanup is enabled") {
                given("manual cleanup is being performed") {
                    val stage by runForEachTest { planner.createStage(events, CleanupOption.DontCleanup) }

                    it("has no rules") {
                        assertThat(stage.rules, isEmpty)
                    }

                    it("has no manual cleanup commands") {
                        assertThat(stage.manualCleanupCommands, isEmpty)
                    }
                }
            }
        }

        given("the task network was created") {
            val network = NetworkReference("the-network")

            beforeEachTest { events.add(TaskNetworkCreatedEvent(network)) }

            given("no containers were created") {
                val expectedCleanupCommands = listOf("docker network rm the-network")

                given("automatic cleanup is being performed") {
                    on("creating the stage") {
                        val stage by runForEachTest { planner.createStage(events, CleanupOption.Cleanup) }

                        itHasExactlyTheRules(
                            { stage },
                            mapOf(
                                "delete the task network" to DeleteTaskNetworkStepRule(network, emptySet())
                            )
                        )

                        it("provides a manual cleanup instruction to remove the network") {
                            assertThat(stage.manualCleanupCommands, equalTo(expectedCleanupCommands))
                        }
                    }
                }

                given("manual cleanup is being performed") {
                    on("creating the stage") {
                        val stage by runForEachTest { planner.createStage(events, CleanupOption.DontCleanup) }

                        it("has no rules") {
                            assertThat(stage.rules, isEmpty)
                        }

                        it("provides a manual cleanup instruction to remove the network") {
                            assertThat(stage.manualCleanupCommands, equalTo(expectedCleanupCommands))
                        }
                    }
                }
            }

            given("only a single container was created") {
                val dockerContainer = DockerContainer("some-container-id", "some-container-name")
                val expectedCleanupCommands = listOf("docker rm --force --volumes some-container-id", "docker network rm the-network")

                beforeEachTest { events.add(ContainerCreatedEvent(taskContainer, dockerContainer)) }

                given("the container was not started") {
                    given("automatic cleanup is being performed") {
                        on("creating the stage") {
                            val stage by runForEachTest { planner.createStage(events, CleanupOption.Cleanup) }

                            itHasExactlyTheRules(
                                { stage },
                                mapOf(
                                    "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer)),
                                    "remove the container" to RemoveContainerStepRule(taskContainer, dockerContainer, false)
                                )
                            )

                            it("provides manual cleanup commands to remove the network and the container") {
                                assertThat(stage.manualCleanupCommands, equalTo(expectedCleanupCommands))
                            }
                        }
                    }

                    given("manual cleanup is being performed") {
                        on("creating the stage") {
                            val stage by runForEachTest { planner.createStage(events, CleanupOption.DontCleanup) }

                            it("has no rules") {
                                assertThat(stage.rules, isEmpty)
                            }

                            it("provides manual cleanup commands to remove the network and the container") {
                                assertThat(stage.manualCleanupCommands, equalTo(expectedCleanupCommands))
                            }
                        }
                    }
                }

                given("the container was started") {
                    beforeEachTest { events.add(ContainerStartedEvent(taskContainer)) }

                    given("automatic cleanup is being performed") {
                        on("creating the stage") {
                            val stage by runForEachTest { planner.createStage(events, CleanupOption.Cleanup) }

                            itHasExactlyTheRules(
                                { stage },
                                mapOf(
                                    "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer)),
                                    "stop the container" to StopContainerStepRule(taskContainer, dockerContainer, emptySet()),
                                    "remove the container after it is stopped" to RemoveContainerStepRule(taskContainer, dockerContainer, true)
                                )
                            )

                            it("provides manual cleanup commands to remove the network and the container") {
                                assertThat(stage.manualCleanupCommands, equalTo(expectedCleanupCommands))
                            }
                        }
                    }

                    given("manual cleanup is being performed") {
                        on("creating the stage") {
                            val stage by runForEachTest { planner.createStage(events, CleanupOption.DontCleanup) }

                            itHasExactlyTheRules(
                                { stage },
                                mapOf(
                                    "stop the container" to StopContainerStepRule(taskContainer, dockerContainer, emptySet())
                                )
                            )

                            it("provides manual cleanup commands to remove the network and the container") {
                                assertThat(stage.manualCleanupCommands, equalTo(expectedCleanupCommands))
                            }
                        }
                    }
                }
            }

            given("all of the containers were created") {
                val taskDockerContainer = DockerContainer("task-container-id", "task-container-name")
                val container1DockerContainer = DockerContainer("container-1-id", "container-1-name")
                val container2DockerContainer = DockerContainer("container-2-id", "container-2-name")

                beforeEachTest {
                    events.add(ContainerCreatedEvent(taskContainer, taskDockerContainer))
                    events.add(ContainerCreatedEvent(container1, container1DockerContainer))
                    events.add(ContainerCreatedEvent(container2, container2DockerContainer))
                }

                val expectedCleanupCommands = listOf(
                    "docker rm --force --volumes task-container-id",
                    "docker rm --force --volumes container-1-id",
                    "docker rm --force --volumes container-2-id",
                    "docker network rm the-network"
                )

                given("none of the containers were started") {
                    given("automatic cleanup is being performed") {
                        on("creating the stage") {
                            val stage by runForEachTest { planner.createStage(events, CleanupOption.Cleanup) }

                            itHasExactlyTheRules(
                                { stage },
                                mapOf(
                                    "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer, container1, container2)),
                                    "remove the task container" to RemoveContainerStepRule(taskContainer, taskDockerContainer, false),
                                    "remove the first dependency container" to RemoveContainerStepRule(container1, container1DockerContainer, false),
                                    "remove the second dependency container" to RemoveContainerStepRule(container2, container2DockerContainer, false)
                                )
                            )

                            it("provides manual cleanup commands to remove the network and the containers") {
                                assertThat(stage.manualCleanupCommands, equalTo(expectedCleanupCommands))
                            }
                        }
                    }

                    given("manual cleanup is being performed") {
                        on("creating the stage") {
                            val stage by runForEachTest { planner.createStage(events, CleanupOption.DontCleanup) }

                            it("has no rules") {
                                assertThat(stage.rules, isEmpty)
                            }

                            it("provides manual cleanup commands to remove the network and the containers") {
                                assertThat(stage.manualCleanupCommands, equalTo(expectedCleanupCommands))
                            }
                        }
                    }
                }

                given("the container that is depended on by other containers was started") {
                    beforeEachTest {
                        events.add(ContainerStartedEvent(container1))
                    }

                    given("automatic cleanup is being performed") {
                        on("creating the stage") {
                            val stage by runForEachTest { planner.createStage(events, CleanupOption.Cleanup) }

                            itHasExactlyTheRules(
                                { stage },
                                mapOf(
                                    "delete the task network" to DeleteTaskNetworkStepRule(network, setOf(taskContainer, container1, container2)),
                                    "stop the first dependency container" to StopContainerStepRule(container1, container1DockerContainer, emptySet()),
                                    "remove the task container" to RemoveContainerStepRule(taskContainer, taskDockerContainer, false),
                                    "remove the first dependency container after it is stopped" to RemoveContainerStepRule(container1, container1DockerContainer, true),
                                    "remove the second dependency container" to RemoveContainerStepRule(container2, container2DockerContainer, false)
                                )
                            )

                            it("provides manual cleanup commands to remove the network and the containers") {
                                assertThat(stage.manualCleanupCommands, equalTo(expectedCleanupCommands))
                            }
                        }
                    }

                    given("manual cleanup is being performed") {
                        on("creating the stage") {
                            val stage by runForEachTest { planner.createStage(events, CleanupOption.DontCleanup) }

                            itHasExactlyTheRules(
                                { stage },
                                mapOf(
                                    "stop the first dependency container" to StopContainerStepRule(container1, container1DockerContainer, emptySet())
                                )
                            )

                            it("provides manual cleanup commands to remove the network and the containers") {
                                assertThat(stage.manualCleanupCommands, equalTo(expectedCleanupCommands))
                            }
                        }
                    }
                }

                given("all of the containers were started") {
                    beforeEachTest {
                        events.add(ContainerStartedEvent(taskContainer))
                        events.add(ContainerStartedEvent(container1))
                        events.add(ContainerStartedEvent(container2))
                    }

                    given("automatic cleanup is being performed") {
                        on("creating the stage") {
                            val stage by runForEachTest { planner.createStage(events, CleanupOption.Cleanup) }

                            itHasExactlyTheRules(
                                { stage },
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

                            it("provides manual cleanup commands to remove the network and the containers") {
                                assertThat(stage.manualCleanupCommands, equalTo(expectedCleanupCommands))
                            }
                        }
                    }

                    given("manual cleanup is being performed") {
                        on("creating the stage") {
                            val stage by runForEachTest { planner.createStage(events, CleanupOption.DontCleanup) }

                            itHasExactlyTheRules(
                                { stage },
                                mapOf(
                                    "stop the task container" to StopContainerStepRule(taskContainer, taskDockerContainer, emptySet()),
                                    "stop the first dependency container" to StopContainerStepRule(container1, container1DockerContainer, setOf(container2, taskContainer)),
                                    "stop the second dependency container" to StopContainerStepRule(container2, container2DockerContainer, setOf(taskContainer))
                                )
                            )

                            it("provides manual cleanup commands to remove the network and the containers") {
                                assertThat(stage.manualCleanupCommands, equalTo(expectedCleanupCommands))
                            }
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
