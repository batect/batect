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

import batect.config.BuildImage
import batect.config.CacheMount
import batect.config.Container
import batect.config.PullImage
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.execution.ContainerRuntimeConfiguration
import batect.execution.model.events.CachesInitialisedEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.ImagePulledEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.rules.TaskStepRule
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.CreateContainerStep

data class CreateContainerStepRule(
    val container: Container,
    val config: ContainerRuntimeConfiguration
) : TaskStepRule() {
    private val needToWaitForCacheInitialisation = container.volumeMounts.any { it is CacheMount }

    override fun evaluate(pastEvents: Set<TaskEvent>): TaskStepRuleEvaluationResult {
        val network = findNetwork(pastEvents)
        val image = findImage(pastEvents)

        if (network == null || image == null) {
            return TaskStepRuleEvaluationResult.NotReady
        }

        if (needToWaitForCacheInitialisation && !cachesAreInitialised(pastEvents)) {
            return TaskStepRuleEvaluationResult.NotReady
        }

        return TaskStepRuleEvaluationResult.Ready(CreateContainerStep(
            container,
            config,
            image,
            network
        ))
    }

    private fun findNetwork(pastEvents: Set<TaskEvent>): DockerNetwork? =
        pastEvents.singleInstanceOrNull<TaskNetworkCreatedEvent>()
            ?.network

    private fun findImage(pastEvents: Set<TaskEvent>): DockerImage? {
        return when (container.imageSource) {
            is PullImage -> pastEvents
                .singleInstanceOrNull<ImagePulledEvent> { it.source == container.imageSource }
                ?.image
            is BuildImage -> pastEvents
                .singleInstanceOrNull<ImageBuiltEvent> { it.container == container }
                ?.image
        }
    }

    private fun cachesAreInitialised(pastEvents: Set<TaskEvent>) = pastEvents.contains(CachesInitialisedEvent)

    override fun toString() = "${this::class.simpleName}(container: '${container.name}', config: $config)"
}
