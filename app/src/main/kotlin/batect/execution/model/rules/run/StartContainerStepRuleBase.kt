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
import batect.docker.DockerContainer
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.rules.TaskStepRule
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.TaskStep

abstract class StartContainerStepRuleBase(open val container: Container, open val dependencies: Set<Container>) : TaskStepRule() {
    override fun evaluate(pastEvents: Set<TaskEvent>): TaskStepRuleEvaluationResult {
        val dockerContainer = findDockerContainer(pastEvents)

        if (dockerContainer == null || !allDependenciesAreReady(pastEvents)) {
            return TaskStepRuleEvaluationResult.NotReady
        }

        return TaskStepRuleEvaluationResult.Ready(createStep(dockerContainer))
    }

    protected abstract fun createStep(dockerContainer: DockerContainer): TaskStep

    private fun findDockerContainer(pastEvents: Set<TaskEvent>) =
        pastEvents
            .singleInstanceOrNull<ContainerCreatedEvent> { it.container == container }
            ?.dockerContainer

    private fun allDependenciesAreReady(pastEvents: Set<TaskEvent>): Boolean {
        val readyContainers = pastEvents
            .filterIsInstance<ContainerBecameHealthyEvent>()
            .map { it.container }

        return readyContainers.containsAll(dependencies)
    }

    override fun toString() = "${this::class.simpleName}(container: '${container.name}', dependencies: ${dependencies.map { "'${it.name}'" }})"
}
