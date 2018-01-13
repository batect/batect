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

package batect.model.events

import batect.model.steps.RemoveContainerStep
import batect.model.steps.StopContainerStep
import batect.config.Container
import batect.logging.Logger

data class RunningContainerExitedEvent(val container: Container, val exitCode: Int) : TaskEvent() {
    override fun apply(context: TaskEventContext, logger: Logger) {
        if (!context.isTaskContainer(container)) {
            throw IllegalArgumentException("The container '${container.name}' is not the task container.")
        }

        val dockerContainer = context.getPastEventsOfType<ContainerCreatedEvent>()
                .single { it.container == container }
                .dockerContainer

        context.queueStep(RemoveContainerStep(container, dockerContainer))

        context.dependenciesOf(container)
                .forEach { stopContainer(it, context) }
    }

    private fun stopContainer(container: Container, context: TaskEventContext) {
        val dockerContainer = context.getPastEventsOfType<ContainerCreatedEvent>()
                .single { it.container == container }
                .dockerContainer

        context.queueStep(StopContainerStep(container, dockerContainer))
    }

    override fun toString() = "${this::class.simpleName}(container: '${container.name}', exit code: $exitCode)"
}
