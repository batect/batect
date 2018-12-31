/*
   Copyright 2017-2019 Charles Korn.

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

package batect.execution.model.rules.run

import batect.config.Container
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.rules.TaskStepRule
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.WaitForContainerToBecomeHealthyStep

data class WaitForContainerToBecomeHealthyStepRule(val container: Container) : TaskStepRule() {
    override fun evaluate(pastEvents: Set<TaskEvent>): TaskStepRuleEvaluationResult {
        if (!containerHasStarted(pastEvents)) {
            return TaskStepRuleEvaluationResult.NotReady
        }

        val dockerContainer = findDockerContainer(pastEvents)

        return TaskStepRuleEvaluationResult.Ready(WaitForContainerToBecomeHealthyStep(container, dockerContainer))
    }

    private fun findDockerContainer(pastEvents: Set<TaskEvent>) =
        pastEvents
            .singleInstance<ContainerCreatedEvent> { it.container == container }
            .dockerContainer

    private fun containerHasStarted(pastEvents: Set<TaskEvent>) =
        pastEvents.any { it is ContainerStartedEvent && it.container == container }

    override fun toString() = "${this::class.simpleName}(container: '${container.name}')"
}
