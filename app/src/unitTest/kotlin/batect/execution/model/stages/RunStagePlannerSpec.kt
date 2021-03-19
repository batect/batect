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
import batect.config.PortMapping
import batect.config.PullImage
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.config.TaskSpecialisedConfiguration
import batect.execution.ContainerCommandResolver
import batect.execution.ContainerDependencyGraph
import batect.execution.ContainerEntrypointResolver
import batect.execution.model.rules.TaskStepRule
import batect.execution.model.rules.run.BuildImageStepRule
import batect.execution.model.rules.run.CreateContainerStepRule
import batect.execution.model.rules.run.InitialiseCachesStepRule
import batect.execution.model.rules.run.PrepareTaskNetworkStepRule
import batect.execution.model.rules.run.PullImageStepRule
import batect.execution.model.rules.run.RunContainerSetupCommandsStepRule
import batect.execution.model.rules.run.RunContainerStepRule
import batect.execution.model.rules.run.WaitForContainerToBecomeHealthyStepRule
import batect.os.Command
import batect.testutils.createLoggerForEachTest
import batect.testutils.given
import batect.testutils.on
import batect.testutils.pathResolutionContextDoesNotMatter
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasElement
import com.natpryce.hamkrest.hasSize
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe

object RunStagePlannerSpec : Spek({
    describe("a run stage planner") {
        val commandResolver = mock<ContainerCommandResolver> {
            on { resolveCommand(any(), any()) } doReturn Command.parse("do-stuff")
        }

        val entrypointResolver = mock<ContainerEntrypointResolver> {
            on { resolveEntrypoint(any(), any()) } doReturn Command.parse("some-entrypoint")
        }

        val logger by createLoggerForEachTest()

        fun Suite.itCreatesStageWithRules(graph: ContainerDependencyGraph, expectedRules: Map<String, TaskStepRule>) {
            val stage by runForEachTest { RunStagePlanner(graph, logger).createStage() }

            expectedRules.forEach { (description, expectedRule) ->
                it("includes a rule to $description") {
                    assertThat(stage.rules, hasElement(expectedRule))
                }
            }

            it("only includes the expected rules") {
                assertThat(stage.rules, hasSize(equalTo(expectedRules.size)))
            }

            it("passes the main container to the stage") {
                assertThat(stage.taskContainer, equalTo(graph.taskContainerNode.container))
            }
        }

        given("the task has a single container") {
            given("the task has no additional environment variables or port mappings") {
                val task = Task("the-task", TaskRunConfiguration("the-container"))

                on("that container pulls an existing image") {
                    val container = Container(task.runConfiguration!!.container, PullImage("some-image"))
                    val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(container))
                    val graph = ContainerDependencyGraph(config, task, commandResolver, entrypointResolver)

                    itCreatesStageWithRules(
                        graph,
                        mapOf(
                            "prepare the task network" to PrepareTaskNetworkStepRule,
                            "initialise caches" to InitialiseCachesStepRule,
                            "pull the image for the task container" to PullImageStepRule(PullImage("some-image")),
                            "create the task container" to CreateContainerStepRule(container, graph.nodeFor(container).config),
                            "run the task container" to RunContainerStepRule(container, emptySet()),
                            "wait for the task container to become healthy" to WaitForContainerToBecomeHealthyStepRule(container),
                            "run setup commands for the task container" to RunContainerSetupCommandsStepRule(container, graph.nodeFor(container).config)
                        )
                    )
                }

                on("that container builds an image from a Dockerfile") {
                    val imageSource = BuildImage(LiteralValue("./my-image"), pathResolutionContextDoesNotMatter(), mapOf("some_arg" to LiteralValue("some_value")), "some-Dockerfile")
                    val container = Container(task.runConfiguration!!.container, imageSource)
                    val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(container))
                    val graph = ContainerDependencyGraph(config, task, commandResolver, entrypointResolver)

                    itCreatesStageWithRules(
                        graph,
                        mapOf(
                            "prepare the task network" to PrepareTaskNetworkStepRule,
                            "initialise caches" to InitialiseCachesStepRule,
                            "build the image for the task container" to BuildImageStepRule(container),
                            "create the task container" to CreateContainerStepRule(container, graph.nodeFor(container).config),
                            "run the task container" to RunContainerStepRule(container, emptySet()),
                            "wait for the task container to become healthy" to WaitForContainerToBecomeHealthyStepRule(container),
                            "run setup commands for the task container" to RunContainerSetupCommandsStepRule(container, graph.nodeFor(container).config)
                        )
                    )
                }
            }

            on("the task has some additional environment variables") {
                val task = Task("the-task", TaskRunConfiguration("the-container", additionalEnvironmentVariables = mapOf("SOME_VAR" to LiteralValue("some value"))))
                val container = Container(task.runConfiguration!!.container, PullImage("some-image"))
                val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(container))
                val graph = ContainerDependencyGraph(config, task, commandResolver, entrypointResolver)

                itCreatesStageWithRules(
                    graph,
                    mapOf(
                        "prepare the task network" to PrepareTaskNetworkStepRule,
                        "initialise caches" to InitialiseCachesStepRule,
                        "pull the image for the task container" to PullImageStepRule(PullImage("some-image")),
                        "create the task container with the additional environment variables" to CreateContainerStepRule(container, graph.nodeFor(container).config),
                        "run the task container" to RunContainerStepRule(container, emptySet()),
                        "wait for the task container to become healthy" to WaitForContainerToBecomeHealthyStepRule(container),
                        "run setup commands for the task container" to RunContainerSetupCommandsStepRule(container, graph.nodeFor(container).config)
                    )
                )
            }

            on("the task some additional port mappings") {
                val task = Task("the-task", TaskRunConfiguration("the-container", additionalPortMappings = setOf(PortMapping(123, 456))))
                val container = Container(task.runConfiguration!!.container, PullImage("some-image"))
                val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(container))
                val graph = ContainerDependencyGraph(config, task, commandResolver, entrypointResolver)

                itCreatesStageWithRules(
                    graph,
                    mapOf(
                        "prepare the task network" to PrepareTaskNetworkStepRule,
                        "initialise caches" to InitialiseCachesStepRule,
                        "pull the image for the task container" to PullImageStepRule(PullImage("some-image")),
                        "create the task container with the additional environment variables" to CreateContainerStepRule(container, graph.nodeFor(container).config),
                        "run the task container" to RunContainerStepRule(container, emptySet()),
                        "wait for the task container to become healthy" to WaitForContainerToBecomeHealthyStepRule(container),
                        "run setup commands for the task container" to RunContainerSetupCommandsStepRule(container, graph.nodeFor(container).config)
                    )
                )
            }
        }

        given("the task has multiple containers") {
            val task = Task("the-task", TaskRunConfiguration("task-container", additionalEnvironmentVariables = mapOf("SOME_VAR" to LiteralValue("some value")), additionalPortMappings = setOf(PortMapping(123, 456))))

            given("each container has a unique build directory or existing image to pull") {
                val container1ImageSource = BuildImage(LiteralValue("./container-1"), pathResolutionContextDoesNotMatter())
                val container1 = Container("container-1", container1ImageSource)
                val container2ImageSource = PullImage("image-2")
                val container2 = Container("container-2", container2ImageSource)
                val container3ImageSource = PullImage("image-3")
                val container3 = Container("container-3", container3ImageSource, dependencies = setOf(container2.name))
                val taskContainerImageSource = BuildImage(LiteralValue("./task-container"), pathResolutionContextDoesNotMatter())
                val taskContainer = Container(task.runConfiguration!!.container, taskContainerImageSource, dependencies = setOf(container1.name, container2.name, container3.name))
                val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer, container1, container2, container3))
                val graph = ContainerDependencyGraph(config, task, commandResolver, entrypointResolver)

                itCreatesStageWithRules(
                    graph,
                    mapOf(
                        "prepare the task network" to PrepareTaskNetworkStepRule,
                        "initialise caches" to InitialiseCachesStepRule,
                        "build the image for the task container" to BuildImageStepRule(taskContainer),
                        "build the image for container 1" to BuildImageStepRule(container1),
                        "pull the image for container 2" to PullImageStepRule(container2ImageSource),
                        "pull the image for container 3" to PullImageStepRule(container3ImageSource),
                        "create the task container" to CreateContainerStepRule(taskContainer, graph.nodeFor(taskContainer).config),
                        "create the container for container 1" to CreateContainerStepRule(container1, graph.nodeFor(container1).config),
                        "create the container for container 2" to CreateContainerStepRule(container2, graph.nodeFor(container2).config),
                        "create the container for container 3" to CreateContainerStepRule(container3, graph.nodeFor(container3).config),
                        "run the task container" to RunContainerStepRule(taskContainer, graph.nodeFor(taskContainer).dependsOnContainers),
                        "run container 1" to RunContainerStepRule(container1, graph.nodeFor(container1).dependsOnContainers),
                        "run container 2" to RunContainerStepRule(container2, graph.nodeFor(container2).dependsOnContainers),
                        "run container 3" to RunContainerStepRule(container3, graph.nodeFor(container3).dependsOnContainers),
                        "wait for the task container to become healthy" to WaitForContainerToBecomeHealthyStepRule(taskContainer),
                        "wait for container 1 to become healthy" to WaitForContainerToBecomeHealthyStepRule(container1),
                        "wait for container 2 to become healthy" to WaitForContainerToBecomeHealthyStepRule(container2),
                        "wait for container 3 to become healthy" to WaitForContainerToBecomeHealthyStepRule(container3),
                        "run setup commands for the task container" to RunContainerSetupCommandsStepRule(taskContainer, graph.nodeFor(taskContainer).config),
                        "run setup commands for container 1" to RunContainerSetupCommandsStepRule(container1, graph.nodeFor(container1).config),
                        "run setup commands for container 2" to RunContainerSetupCommandsStepRule(container2, graph.nodeFor(container2).config),
                        "run setup commands for container 3" to RunContainerSetupCommandsStepRule(container3, graph.nodeFor(container3).config)
                    )
                )
            }

            given("some containers share an existing image to pull") {
                val sharedImageSource = PullImage("shared-image")
                val container1 = Container("container-1", sharedImageSource)
                val container2 = Container("container-2", sharedImageSource)
                val taskContainerImageSource = PullImage("task-image")
                val taskContainer = Container(task.runConfiguration!!.container, taskContainerImageSource, dependencies = setOf(container1.name, container2.name))
                val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer, container1, container2))
                val graph = ContainerDependencyGraph(config, task, commandResolver, entrypointResolver)

                itCreatesStageWithRules(
                    graph,
                    mapOf(
                        "prepare the task network" to PrepareTaskNetworkStepRule,
                        "initialise caches" to InitialiseCachesStepRule,
                        "pull the image for the task container" to PullImageStepRule(taskContainerImageSource),
                        "pull the image shared by both container 1 and 2" to PullImageStepRule(sharedImageSource),
                        "create the task container" to CreateContainerStepRule(taskContainer, graph.nodeFor(taskContainer).config),
                        "create the container for container 1" to CreateContainerStepRule(container1, graph.nodeFor(container1).config),
                        "create the container for container 2" to CreateContainerStepRule(container2, graph.nodeFor(container2).config),
                        "run the task container" to RunContainerStepRule(taskContainer, graph.nodeFor(taskContainer).dependsOnContainers),
                        "run container 1" to RunContainerStepRule(container1, graph.nodeFor(container1).dependsOnContainers),
                        "run container 2" to RunContainerStepRule(container2, graph.nodeFor(container2).dependsOnContainers),
                        "wait for the task container to become healthy" to WaitForContainerToBecomeHealthyStepRule(taskContainer),
                        "wait for container 1 to become healthy" to WaitForContainerToBecomeHealthyStepRule(container1),
                        "wait for container 2 to become healthy" to WaitForContainerToBecomeHealthyStepRule(container2),
                        "run setup commands for the task container" to RunContainerSetupCommandsStepRule(taskContainer, graph.nodeFor(taskContainer).config),
                        "run setup commands for container 1" to RunContainerSetupCommandsStepRule(container1, graph.nodeFor(container1).config),
                        "run setup commands for container 2" to RunContainerSetupCommandsStepRule(container2, graph.nodeFor(container2).config)
                    )
                )
            }
        }
    }
})
