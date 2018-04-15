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
import batect.config.Container
import batect.config.PullImage
import batect.execution.ContainerDependencyGraph
import batect.execution.ContainerDependencyGraphNode
import batect.execution.model.rules.TaskStepRule
import batect.execution.model.rules.run.BuildImageStepRule
import batect.execution.model.rules.run.CreateContainerStepRule
import batect.execution.model.rules.run.CreateTaskNetworkStepRule
import batect.execution.model.rules.run.PullImageStepRule
import batect.execution.model.rules.run.RunContainerStepRule
import batect.execution.model.rules.run.StartContainerStepRule
import batect.execution.model.rules.run.WaitForContainerToBecomeHealthyStepRule
import batect.logging.Logger
import batect.utils.flatMapToSet

class RunStagePlanner(private val logger: Logger) {
    fun createStage(graph: ContainerDependencyGraph): RunStage {
        val rules = graph.allNodes.flatMapToSet { stepsFor(it, graph.config.projectName) } +
            CreateTaskNetworkStepRule

        logger.info {
            message("Created run plan.")
            data("rules", rules.map { it.toString() })
        }

        return RunStage(rules)
    }

    private fun stepsFor(node: ContainerDependencyGraphNode, projectName: String): Set<TaskStepRule> {
        return startupRulesFor(node) +
            imageCreationRuleFor(node.container, projectName) +
            CreateContainerStepRule(node.container, node.command, node.additionalEnvironmentVariables, node.additionalPortMappings)
    }

    private fun imageCreationRuleFor(container: Container, projectName: String): TaskStepRule {
        return when (container.imageSource) {
            is PullImage -> PullImageStepRule(container.imageSource.imageName)
            is BuildImage -> BuildImageStepRule(projectName, container)
        }
    }

    private fun startupRulesFor(node: ContainerDependencyGraphNode): Set<TaskStepRule> {
        if (node == node.graph.taskContainerNode) {
            return setOf(RunContainerStepRule(node.container, node.dependsOnContainers))
        }

        return setOf(
            StartContainerStepRule(node.container, node.dependsOnContainers),
            WaitForContainerToBecomeHealthyStepRule(node.container)
        )
    }
}
