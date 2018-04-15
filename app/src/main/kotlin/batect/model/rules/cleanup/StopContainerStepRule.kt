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

package batect.model.rules.cleanup

import batect.config.Container
import batect.docker.DockerContainer
import batect.model.events.ContainerStoppedEvent
import batect.model.events.TaskEvent
import batect.model.rules.TaskStepRuleEvaluationResult
import batect.model.steps.StopContainerStep
import batect.utils.mapToSet

data class StopContainerStepRule(val container: Container, val dockerContainer: DockerContainer, val containersThatMustBeStoppedFirst: Set<Container>) : CleanupTaskStepRule() {
    override fun evaluate(pastEvents: Set<TaskEvent>): TaskStepRuleEvaluationResult {
        val stoppedContainers = pastEvents
            .filterIsInstance<ContainerStoppedEvent>()
            .mapToSet { it.container }

        if (stoppedContainers.containsAll(containersThatMustBeStoppedFirst)) {
            return TaskStepRuleEvaluationResult.Ready(StopContainerStep(container, dockerContainer))
        }

        return TaskStepRuleEvaluationResult.NotReady
    }

    override val manualCleanupInstruction: String? = null
    override val manualCleanupSortOrder: ManualCleanupSortOrder
        get() = throw UnsupportedOperationException("This rule has no manual cleanup instruction.")
    override fun toString() = "${this::class.simpleName}(container=${container.name}, dockerContainer=$dockerContainer, containersThatMustBeStoppedFirst=${containersThatMustBeStoppedFirst.map(Container::name)})"
}
