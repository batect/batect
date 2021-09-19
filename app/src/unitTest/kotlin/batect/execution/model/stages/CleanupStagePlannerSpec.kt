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
import batect.execution.model.rules.cleanup.CleanupTaskStepRule
import batect.execution.model.rules.cleanup.DeleteTaskNetworkStepRule
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe

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
                on("creating the stage") {
                    itHasExactlyTheRules(
                        { planner.createStage(events) },
                        mapOf(
                            "delete the task network" to DeleteTaskNetworkStepRule(network, emptySet())
                        )
                    )
                }
            }

            given("only a single container was created") {
                val dockerContainer = DockerContainer("some-container-id")

                beforeEachTest { events.add(ContainerCreatedEvent(taskContainer, dockerContainer)) }

                given("the container was not started") {
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

                given("the container was started") {
                    beforeEachTest { events.add(ContainerStartedEvent(taskContainer)) }

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

                given("the container that is depended on by other containers was started") {
                    beforeEachTest {
                        events.add(ContainerStartedEvent(container1))
                    }

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

                given("all of the containers were started") {
                    beforeEachTest {
                        events.add(ContainerStartedEvent(taskContainer))
                        events.add(ContainerStartedEvent(container1))
                        events.add(ContainerStartedEvent(container2))
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
                                "remove the second dependency container after it is stopped" to RemoveContainerStepRule(container2, container2DockerContainer, true)
                            )
                        )
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
