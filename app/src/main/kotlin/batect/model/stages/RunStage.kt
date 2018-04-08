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
import batect.config.Container
import batect.config.PullImage
import batect.model.DependencyGraph
import batect.model.DependencyGraphNode
import batect.model.rules.run.BuildImageStepRule
import batect.model.rules.run.CreateContainerStepRule
import batect.model.rules.run.CreateTaskNetworkStepRule
import batect.model.rules.run.PullImageStepRule
import batect.model.rules.run.RunContainerStepRule
import batect.model.rules.run.StartContainerStepRule
import batect.model.rules.TaskStepRule
import batect.model.rules.run.WaitForContainerToBecomeHealthyStepRule
import batect.utils.flatMapToSet

class RunStage(private val graph: DependencyGraph) {
    val rules: Set<TaskStepRule> = generateRules()

    private fun generateRules(): Set<TaskStepRule> {
        return graph.allNodes.flatMapToSet { stepsFor(it) } +
            CreateTaskNetworkStepRule
    }

    private fun stepsFor(node: DependencyGraphNode): Set<TaskStepRule> {
        return startupRulesFor(node) +
            imageCreationRuleFor(node.container) +
            CreateContainerStepRule(node.container, node.command, node.additionalEnvironmentVariables, node.additionalPortMappings)
    }

    private fun imageCreationRuleFor(container: Container): TaskStepRule {
        return when (container.imageSource) {
            is PullImage -> PullImageStepRule(container.imageSource.imageName)
            is BuildImage -> BuildImageStepRule(graph.config.projectName, container)
        }
    }

    private fun startupRulesFor(node: DependencyGraphNode): Set<TaskStepRule> {
        if (node == graph.taskContainerNode) {
            return setOf(RunContainerStepRule(node.container, node.dependsOnContainers))
        } else {
            return setOf(
                StartContainerStepRule(node.container, node.dependsOnContainers),
                WaitForContainerToBecomeHealthyStepRule(node.container)
            )
        }
    }
}
