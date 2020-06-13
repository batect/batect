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

package batect.execution.model.rules.run

import batect.config.Container
import batect.execution.ContainerRuntimeConfiguration
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.rules.TaskStepRule
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.RunContainerSetupCommandsStep
import batect.logging.ContainerNameOnlySerializer
import kotlinx.serialization.Serializable

@Serializable
data class RunContainerSetupCommandsStepRule(
    @Serializable(with = ContainerNameOnlySerializer::class) val container: Container,
    val config: ContainerRuntimeConfiguration
) : TaskStepRule() {
    override fun evaluate(pastEvents: Set<TaskEvent>): TaskStepRuleEvaluationResult {
        if (!containerHasBecomeHealthy(pastEvents)) {
            return TaskStepRuleEvaluationResult.NotReady
        }

        val dockerContainer = findDockerContainer(pastEvents)

        return TaskStepRuleEvaluationResult.Ready(RunContainerSetupCommandsStep(container, config, dockerContainer))
    }

    private fun findDockerContainer(pastEvents: Set<TaskEvent>) =
        pastEvents
            .singleInstance<ContainerCreatedEvent> { it.container == container }
            .dockerContainer

    private fun containerHasBecomeHealthy(pastEvents: Set<TaskEvent>) =
        pastEvents.any { it is ContainerBecameHealthyEvent && it.container == container }
}
