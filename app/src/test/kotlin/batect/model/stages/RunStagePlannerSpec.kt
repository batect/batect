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

package batect.model.stages

import batect.config.BuildImage
import batect.config.Configuration
import batect.config.Container
import batect.config.ContainerMap
import batect.config.PortMapping
import batect.config.PullImage
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.logging.Logger
import batect.model.ContainerCommandResolver
import batect.model.DependencyGraph
import batect.model.rules.run.BuildImageStepRule
import batect.model.rules.run.CreateContainerStepRule
import batect.model.rules.run.CreateTaskNetworkStepRule
import batect.model.rules.run.PullImageStepRule
import batect.model.rules.run.RunContainerStepRule
import batect.model.rules.run.StartContainerStepRule
import batect.model.rules.TaskStepRule
import batect.model.rules.run.WaitForContainerToBecomeHealthyStepRule
import batect.os.Command
import batect.testutils.InMemoryLogSink
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasElement
import com.natpryce.hamkrest.hasSize
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.TestContainer
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it

object RunStagePlannerSpec : Spek({
    describe("a run stage planner") {
        val commandResolver = mock<ContainerCommandResolver> {
            on { resolveCommand(any(), any()) } doReturn Command.parse("do-stuff")
        }

        val logger = Logger("run-stage-test", InMemoryLogSink())
        val planner = RunStagePlanner(logger)

        given("the task has a single container") {
            given("the task has no additional environment variables or port mappings") {
                val task = Task("the-task", TaskRunConfiguration("the-container"))

                given("that container pulls an existing image") {
                    val container = Container(task.runConfiguration.container, PullImage("some-image"))
                    val config = Configuration("the-project", TaskMap(task), ContainerMap(container))
                    val graph = DependencyGraph(config, task, commandResolver)
                    val stage = planner.createStage(graph)

                    itHasExactlyTheRules(stage, mapOf(
                        "create the task network" to CreateTaskNetworkStepRule,
                        "pull the image for the task container" to PullImageStepRule("some-image"),
                        "create the task container" to CreateContainerStepRule(container, graph.nodeFor(container).command, emptyMap(), emptySet()),
                        "run the task container" to RunContainerStepRule(container, emptySet())
                    ))
                }

                given("that container builds an image from a Dockerfile") {
                    val container = Container(task.runConfiguration.container, BuildImage("./my-image"))
                    val config = Configuration("the-project", TaskMap(task), ContainerMap(container))
                    val graph = DependencyGraph(config, task, commandResolver)
                    val stage = planner.createStage(graph)

                    itHasExactlyTheRules(stage, mapOf(
                        "create the task network" to CreateTaskNetworkStepRule,
                        "build the image for the task container" to BuildImageStepRule("the-project", container),
                        "create the task container" to CreateContainerStepRule(container, graph.nodeFor(container).command, emptyMap(), emptySet()),
                        "run the task container" to RunContainerStepRule(container, emptySet())
                    ))
                }
            }

            given("the task some additional environment variables") {
                val task = Task("the-task", TaskRunConfiguration("the-container", additionalEnvironmentVariables = mapOf("SOME_VAR" to "some value")))
                val container = Container(task.runConfiguration.container, PullImage("some-image"))
                val config = Configuration("the-project", TaskMap(task), ContainerMap(container))
                val graph = DependencyGraph(config, task, commandResolver)
                val stage = planner.createStage(graph)

                itHasExactlyTheRules(stage, mapOf(
                    "create the task network" to CreateTaskNetworkStepRule,
                    "pull the image for the task container" to PullImageStepRule("some-image"),
                    "create the task container with the additional environment variables" to CreateContainerStepRule(container, graph.nodeFor(container).command, task.runConfiguration.additionalEnvironmentVariables, emptySet()),
                    "run the task container" to RunContainerStepRule(container, emptySet())
                ))
            }

            given("the task some additional port mappings") {
                val task = Task("the-task", TaskRunConfiguration("the-container", additionalPortMappings = setOf(PortMapping(123, 456))))
                val container = Container(task.runConfiguration.container, PullImage("some-image"))
                val config = Configuration("the-project", TaskMap(task), ContainerMap(container))
                val graph = DependencyGraph(config, task, commandResolver)
                val stage = planner.createStage(graph)

                itHasExactlyTheRules(stage, mapOf(
                    "create the task network" to CreateTaskNetworkStepRule,
                    "pull the image for the task container" to PullImageStepRule("some-image"),
                    "create the task container with the additional environment variables" to CreateContainerStepRule(container, graph.nodeFor(container).command, emptyMap(), task.runConfiguration.additionalPortMappings),
                    "run the task container" to RunContainerStepRule(container, emptySet())
                ))
            }
        }

        given("the task has multiple containers") {
            val task = Task("the-task", TaskRunConfiguration("task-container", additionalEnvironmentVariables = mapOf("SOME_VAR" to "some value"), additionalPortMappings = setOf(PortMapping(123, 456))))

            given("each container has a unique build directory or existing image to pull") {
                val container1 = Container("container-1", BuildImage("./container-1"))
                val container2 = Container("container-2", PullImage("image-2"))
                val container3 = Container("container-3", PullImage("image-3"), dependencies = setOf(container2.name))
                val taskContainer = Container(task.runConfiguration.container, BuildImage("./task-container"), dependencies = setOf(container1.name, container2.name, container3.name))
                val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer, container1, container2, container3))
                val graph = DependencyGraph(config, task, commandResolver)
                val stage = planner.createStage(graph)

                itHasExactlyTheRules(stage, mapOf(
                    "create the task network" to CreateTaskNetworkStepRule,
                    "build the image for the task container" to BuildImageStepRule("the-project", taskContainer),
                    "build the image for container 1" to BuildImageStepRule("the-project", container1),
                    "pull the image for container 2" to PullImageStepRule("image-2"),
                    "pull the image for container 3" to PullImageStepRule("image-3"),
                    "create the task container" to CreateContainerStepRule(taskContainer, graph.nodeFor(taskContainer).command, task.runConfiguration.additionalEnvironmentVariables, task.runConfiguration.additionalPortMappings),
                    "create the container for container 1" to CreateContainerStepRule(container1, graph.nodeFor(container1).command, emptyMap(), emptySet()),
                    "create the container for container 2" to CreateContainerStepRule(container2, graph.nodeFor(container2).command, emptyMap(), emptySet()),
                    "create the container for container 3" to CreateContainerStepRule(container3, graph.nodeFor(container3).command, emptyMap(), emptySet()),
                    "start container 1" to StartContainerStepRule(container1, graph.nodeFor(container1).dependsOnContainers),
                    "start container 2" to StartContainerStepRule(container2, graph.nodeFor(container2).dependsOnContainers),
                    "start container 3" to StartContainerStepRule(container3, graph.nodeFor(container3).dependsOnContainers),
                    "wait for container 1 to become healthy" to WaitForContainerToBecomeHealthyStepRule(container1),
                    "wait for container 2 to become healthy" to WaitForContainerToBecomeHealthyStepRule(container2),
                    "wait for container 3 to become healthy" to WaitForContainerToBecomeHealthyStepRule(container3),
                    "run the task container" to RunContainerStepRule(taskContainer, graph.nodeFor(taskContainer).dependsOnContainers)
                ))
            }

            given("some containers share an existing image to pull") {
                val container1 = Container("container-1", PullImage("shared-image"))
                val container2 = Container("container-2", PullImage("shared-image"))
                val taskContainer = Container(task.runConfiguration.container, PullImage("task-image"), dependencies = setOf(container1.name, container2.name))
                val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer, container1, container2))
                val graph = DependencyGraph(config, task, commandResolver)
                val stage = planner.createStage(graph)

                itHasExactlyTheRules(stage, mapOf(
                    "create the task network" to CreateTaskNetworkStepRule,
                    "pull the image for the task container" to PullImageStepRule("task-image"),
                    "pull the image shared by both container 1 and 2" to PullImageStepRule("shared-image"),
                    "create the task container" to CreateContainerStepRule(taskContainer, graph.nodeFor(taskContainer).command, task.runConfiguration.additionalEnvironmentVariables, task.runConfiguration.additionalPortMappings),
                    "create the container for container 1" to CreateContainerStepRule(container1, graph.nodeFor(container1).command, emptyMap(), emptySet()),
                    "create the container for container 2" to CreateContainerStepRule(container2, graph.nodeFor(container2).command, emptyMap(), emptySet()),
                    "start container 1" to StartContainerStepRule(container1, graph.nodeFor(container1).dependsOnContainers),
                    "start container 2" to StartContainerStepRule(container2, graph.nodeFor(container2).dependsOnContainers),
                    "wait for container 1 to become healthy" to WaitForContainerToBecomeHealthyStepRule(container1),
                    "wait for container 2 to become healthy" to WaitForContainerToBecomeHealthyStepRule(container2),
                    "run the task container" to RunContainerStepRule(taskContainer, graph.nodeFor(taskContainer).dependsOnContainers)
                ))
            }
        }
    }
})

private fun TestContainer.itHasExactlyTheRules(stage: RunStage, expectedRules: Map<String, TaskStepRule>) {
    expectedRules.forEach { description, expectedRule ->
        it("includes a rule to $description") {
            assertThat(stage.rules, hasElement(expectedRule))
        }
    }

    it("only includes the expected rules") {
        assertThat(stage.rules, hasSize(equalTo(expectedRules.size)))
    }
}
