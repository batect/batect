/*
   Copyright 2017 Charles Korn.

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
import batect.utils.mapToSet

data class ContainerStoppedEvent(val container: Container) : TaskEvent() {
    override fun apply(context: TaskEventContext, logger: Logger) {
        if (context.isAborting) {
            logger.info {
                message("Task is aborting, not queuing any further work.")
                data("event", this@ContainerStoppedEvent.toString())
            }

            return
        }

        val dockerContainer = context.getPastEventsOfType<ContainerCreatedEvent>()
                .single { it.container == container }
                .dockerContainer

        context.queueStep(RemoveContainerStep(container, dockerContainer))

        val stoppedContainers = context.getPastEventsOfType<ContainerStoppedEvent>()
                .mapToSet() { it.container }

        context.dependenciesOf(container)
                .filter { noContainersRunningThatDependOn(it, stoppedContainers, context) }
                .forEach { stopContainer(it, context) }
    }

    private fun noContainersRunningThatDependOn(container: Container, stoppedContainers: Set<Container>, context: TaskEventContext): Boolean {
        val containersThatDependOnContainer = context.containersThatDependOn(container)

        return stoppedContainers.containsAll(containersThatDependOnContainer)
    }

    private fun stopContainer(container: Container, context: TaskEventContext) {
        val dockerContainer = context.getPastEventsOfType<ContainerCreatedEvent>()
                .single { it.container == container }
                .dockerContainer

        context.queueStep(StopContainerStep(container, dockerContainer))
    }

    override fun toString() = "${this::class.simpleName}(container: '${container.name}')"
}
