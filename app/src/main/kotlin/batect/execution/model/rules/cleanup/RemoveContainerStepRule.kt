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

package batect.execution.model.rules.cleanup

import batect.config.Container
import batect.docker.DockerContainer
import batect.execution.model.events.ContainerStoppedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.RemoveContainerStep

data class RemoveContainerStepRule(val container: Container, val dockerContainer: DockerContainer, val containerWasStarted: Boolean) : CleanupTaskStepRule() {
    override fun evaluate(pastEvents: Set<TaskEvent>): TaskStepRuleEvaluationResult {
        if (containerWasStarted && !containerHasStopped(pastEvents)) {
            return TaskStepRuleEvaluationResult.NotReady
        }

        return TaskStepRuleEvaluationResult.Ready(RemoveContainerStep(container, dockerContainer))
    }

    private fun containerHasStopped(pastEvents: Set<TaskEvent>): Boolean =
        pastEvents.contains(ContainerStoppedEvent(container))

    override val manualCleanupInstruction: String? = "docker rm --force --volumes ${dockerContainer.id}"
    override val manualCleanupSortOrder: ManualCleanupSortOrder = ManualCleanupSortOrder.RemoveContainers
    override fun toString() = "${this::class.simpleName}(container=${container.name}, dockerContainer=$dockerContainer, containerWasStarted=$containerWasStarted)"
}
