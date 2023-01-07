/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.execution.model.stages

import batect.config.Container
import batect.docker.DockerContainer
import batect.execution.CleanupOption
import batect.execution.ContainerDependencyGraph
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.events.data
import batect.execution.model.rules.cleanup.CleanupTaskStepRule
import batect.execution.model.rules.cleanup.DeleteTaskNetworkStepRule
import batect.execution.model.rules.cleanup.RemoveContainerStepRule
import batect.execution.model.rules.cleanup.StopContainerStepRule
import batect.execution.model.rules.data
import batect.logging.Logger
import batect.primitives.filterToSet
import batect.primitives.mapToSet

class CleanupStagePlanner(
    private val graph: ContainerDependencyGraph,
    private val logger: Logger,
) {
    fun createStage(pastEvents: Set<TaskEvent>, cleanupType: CleanupOption): CleanupStage {
        val containersCreated = pastEvents
            .filterIsInstance<ContainerCreatedEvent>()
            .associate { it.container to it.dockerContainer }

        val containersStarted = pastEvents
            .filterIsInstance<ContainerStartedEvent>()
            .mapToSet { it.container }

        val stopContainerRules = stopContainerRules(containersCreated, containersStarted)

        val allRules = networkCleanupRules(pastEvents, containersCreated) +
            stopContainerRules +
            removeContainerRules(containersCreated, containersStarted)

        val manualCleanupCommands: List<String> = manualCleanupCommands(allRules)

        val rules = when (cleanupType) {
            CleanupOption.DontCleanup -> stopContainerRules
            CleanupOption.Cleanup -> allRules
        }

        val stage = CleanupStage(rules, manualCleanupCommands)

        logger.info {
            message("Created cleanup stage.")
            data("rules", stage.rules)
            data("manualCleanupCommands", stage.manualCleanupCommands)
            data("pastEvents", pastEvents)
        }

        return stage
    }

    private fun networkCleanupRules(pastEvents: Set<TaskEvent>, containersCreated: Map<Container, DockerContainer>): Set<DeleteTaskNetworkStepRule> =
        pastEvents
            .filterIsInstance<TaskNetworkCreatedEvent>()
            .mapToSet {
                val containersThatMustBeRemovedFirst = containersCreated.keys.toSet()

                DeleteTaskNetworkStepRule(it.network, containersThatMustBeRemovedFirst)
            }

    private fun stopContainerRules(containersCreated: Map<Container, DockerContainer>, containersStarted: Set<Container>): Set<StopContainerStepRule> =
        containersStarted
            .mapToSet { container ->
                val dockerContainer = containersCreated.getValue(container)
                val containersThatMustBeStoppedFirst = graph.nodeFor(container).dependedOnByContainers
                    .filterToSet { it in containersStarted }

                StopContainerStepRule(container, dockerContainer, containersThatMustBeStoppedFirst)
            }

    private fun removeContainerRules(containersCreated: Map<Container, DockerContainer>, containersStarted: Set<Container>): Set<RemoveContainerStepRule> =
        containersCreated
            .mapToSet { (container, dockerContainer) ->
                val containerWasStarted = containersStarted.contains(container)

                RemoveContainerStepRule(container, dockerContainer, containerWasStarted)
            }

    private fun manualCleanupCommands(allRules: Set<CleanupTaskStepRule>): List<String> = allRules
        .map { it to it.manualCleanupCommand }
        .filter { (_, command) -> command != null }
        .sortedBy { (rule, _) -> rule.manualCleanupSortOrder }
        .map { (_, command) -> command!! }
}
