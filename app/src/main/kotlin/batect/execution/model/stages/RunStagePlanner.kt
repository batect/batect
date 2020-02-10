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

package batect.execution.model.stages

import batect.config.BuildImage
import batect.config.Configuration
import batect.config.Container
import batect.config.PullImage
import batect.docker.client.DockerContainerType
import batect.execution.ContainerDependencyGraph
import batect.execution.ContainerDependencyGraphNode
import batect.execution.model.rules.TaskStepRule
import batect.execution.model.rules.run.BuildImageStepRule
import batect.execution.model.rules.run.CreateContainerStepRule
import batect.execution.model.rules.run.CreateTaskNetworkStepRule
import batect.execution.model.rules.run.PullImageStepRule
import batect.execution.model.rules.run.RunContainerSetupCommandsStepRule
import batect.execution.model.rules.run.RunContainerStepRule
import batect.execution.model.rules.run.WaitForContainerToBecomeHealthyStepRule
import batect.logging.Logger
import batect.utils.flatMapToSet
import batect.utils.mapToSet

class RunStagePlanner(
    private val config: Configuration,
    private val graph: ContainerDependencyGraph,
    private val containerType: DockerContainerType,
    private val logger: Logger
) {
    fun createStage(): RunStage {
        val allContainersInNetwork = graph.allNodes.mapToSet { it.container }

        val rules = imageCreationRulesFor(graph) +
            graph.allNodes.flatMapToSet { executionStepsFor(it, allContainersInNetwork) } +
            CreateTaskNetworkStepRule(containerType)

        logger.info {
            message("Created run plan.")
            data("rules", rules.map { it.toString() })
        }

        return RunStage(rules, graph.taskContainerNode.container)
    }

    private fun executionStepsFor(node: ContainerDependencyGraphNode, allContainersInNetwork: Set<Container>) = setOf(
        CreateContainerStepRule(node.container, node.config, allContainersInNetwork),
        RunContainerStepRule(node.container, node.dependsOnContainers),
        WaitForContainerToBecomeHealthyStepRule(node.container),
        RunContainerSetupCommandsStepRule(node.container, node.config, allContainersInNetwork)
    )

    private fun imageCreationRulesFor(graph: ContainerDependencyGraph): Set<TaskStepRule> {
        return graph.allNodes.map { it.container }
            .groupBy { it.imageSource }
            .mapToSet { (imageSource, containers) ->
                when (imageSource) {
                    is PullImage -> PullImageStepRule(imageSource)
                    is BuildImage -> BuildImageStepRule(imageSource, imageTagsFor(config.projectName, containers))
                }
            }
    }

    private fun imageTagsFor(projectName: String, containers: List<Container>): Set<String> =
        containers.mapToSet { "$projectName-${it.name}" }
}
