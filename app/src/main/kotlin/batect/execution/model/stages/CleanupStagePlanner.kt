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

import batect.config.Container
import batect.docker.DockerContainer
import batect.execution.ContainerDependencyGraph
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.events.TemporaryDirectoryCreatedEvent
import batect.execution.model.events.TemporaryFileCreatedEvent
import batect.execution.model.rules.cleanup.DeleteTaskNetworkStepRule
import batect.execution.model.rules.cleanup.DeleteTemporaryDirectoryStepRule
import batect.execution.model.rules.cleanup.DeleteTemporaryFileStepRule
import batect.execution.model.rules.cleanup.RemoveContainerStepRule
import batect.execution.model.rules.cleanup.StopContainerStepRule
import batect.logging.Logger
import batect.os.SystemInfo
import batect.utils.filterToSet
import batect.utils.mapToSet

class CleanupStagePlanner(
    private val graph: ContainerDependencyGraph,
    private val systemInfo: SystemInfo,
    private val logger: Logger
) {
    fun createStage(pastEvents: Set<TaskEvent>): CleanupStage {
        val containersCreated = pastEvents
            .filterIsInstance<ContainerCreatedEvent>()
            .associate { it.container to it.dockerContainer }

        val containersStarted = pastEvents
            .filterIsInstance<ContainerStartedEvent>()
            .mapToSet { it.container }

        val rules = networkCleanupRules(pastEvents, containersCreated) +
            stopContainerRules(containersCreated, containersStarted) +
            removeContainerRules(containersCreated, containersStarted) +
            fileDeletionRules(pastEvents, containersCreated) +
            directoryDeletionRules(pastEvents, containersCreated)

        logger.info {
            message("Created cleanup plan.")
            data("rules", rules.map { it.toString() })
            data("pastEvents", pastEvents.map { it.toString() })
        }

        return CleanupStage(rules, systemInfo.operatingSystem)
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

    private fun fileDeletionRules(pastEvents: Set<TaskEvent>, containersCreated: Map<Container, DockerContainer>): Set<DeleteTemporaryFileStepRule> =
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

    private fun directoryDeletionRules(pastEvents: Set<TaskEvent>, containersCreated: Map<Container, DockerContainer>): Set<DeleteTemporaryDirectoryStepRule> =
        pastEvents
            .filterIsInstance<TemporaryDirectoryCreatedEvent>()
            .mapToSet {
                val containerThatMustBeRemovedFirst = if (containersCreated.containsKey(it.container)) {
                    it.container
                } else {
                    null
                }

                DeleteTemporaryDirectoryStepRule(it.directoryPath, containerThatMustBeRemovedFirst)
            }
}
