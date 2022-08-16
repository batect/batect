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

@file:UseSerializers(
    NetworkReferenceSerializer::class
)
package batect.execution.model.rules.cleanup

import batect.config.Container
import batect.docker.NetworkReferenceSerializer
import batect.dockerclient.NetworkReference
import batect.execution.model.events.ContainerRemovedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.DeleteTaskNetworkStep
import batect.logging.ContainerNameSetSerializer
import batect.primitives.mapToSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers

@Serializable
data class DeleteTaskNetworkStepRule(
    val network: NetworkReference,
    @Serializable(with = ContainerNameSetSerializer::class) val containersThatMustBeRemovedFirst: Set<Container>
) : CleanupTaskStepRule() {
    override fun evaluate(pastEvents: Set<TaskEvent>): TaskStepRuleEvaluationResult {
        val removedContainers = pastEvents
            .filterIsInstance<ContainerRemovedEvent>()
            .mapToSet { it.container }

        if (removedContainers.containsAll(containersThatMustBeRemovedFirst)) {
            return TaskStepRuleEvaluationResult.Ready(DeleteTaskNetworkStep(network))
        }

        return TaskStepRuleEvaluationResult.NotReady
    }

    override val manualCleanupCommand: String = "docker network rm ${network.id}"

    @Transient
    override val manualCleanupSortOrder: ManualCleanupSortOrder = ManualCleanupSortOrder.DeleteTaskNetwork
}
