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

import batect.model.DependencyGraph
import batect.model.events.ContainerCreatedEvent
import batect.model.events.ContainerStartedEvent
import batect.model.events.TaskEvent
import batect.model.events.TaskNetworkCreatedEvent
import batect.model.events.TemporaryFileCreatedEvent
import batect.model.rules.cleanup.CleanupTaskStepRule
import batect.model.rules.cleanup.DeleteTaskNetworkStepRule
import batect.model.rules.cleanup.DeleteTemporaryFileStepRule
import batect.model.rules.cleanup.RemoveContainerStepRule
import batect.model.rules.cleanup.StopContainerStepRule
import batect.utils.filterToSet
import batect.utils.mapToSet

class CleanupStage(private val graph: DependencyGraph, private val pastEvents: Set<TaskEvent>) {
    private val containersCreated = pastEvents
        .filterIsInstance<ContainerCreatedEvent>()
        .associate { it.container to it.dockerContainer }

    private val containersStarted = pastEvents
        .filterIsInstance<ContainerStartedEvent>()
        .mapToSet { it.container }

    val rules: Set<CleanupTaskStepRule> = generateRules()

    private fun generateRules(): Set<CleanupTaskStepRule> {
        return networkCleanupRules() +
            stopContainerRules() +
            removeContainerRules() +
            fileDeletionRules()
    }

    private fun networkCleanupRules(): Set<DeleteTaskNetworkStepRule> =
        pastEvents
            .filterIsInstance<TaskNetworkCreatedEvent>()
            .mapToSet {
                val containersThatMustBeRemovedFirst = containersCreated.keys.toSet()

                DeleteTaskNetworkStepRule(it.network, containersThatMustBeRemovedFirst)
            }

    private fun stopContainerRules(): Set<StopContainerStepRule> =
        containersStarted
            .mapToSet { container ->
                val dockerContainer = containersCreated.getValue(container)
                val containersThatMustBeStoppedFirst = graph.nodeFor(container).dependedOnByContainers
                    .filterToSet { it in containersStarted }

                StopContainerStepRule(container, dockerContainer, containersThatMustBeStoppedFirst)
            }

    private fun removeContainerRules(): Set<RemoveContainerStepRule> =
        containersCreated
            .mapToSet { (container, dockerContainer) ->
                val containerWasStarted = containersStarted.contains(container)

                RemoveContainerStepRule(container, dockerContainer, containerWasStarted)
            }

    private fun fileDeletionRules(): Set<DeleteTemporaryFileStepRule> =
        pastEvents
            .filterIsInstance<TemporaryFileCreatedEvent>()
            .mapToSet {
                val containerThatMustBeRemovedFirst = if (containersCreated.containsKey(it.container)) {
                    it.container
                } else {
                    null
                }

                DeleteTemporaryFileStepRule(it.filePath, containerThatMustBeRemovedFirst)
            }
}
