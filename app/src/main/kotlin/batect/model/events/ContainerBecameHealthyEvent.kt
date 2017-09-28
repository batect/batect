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

import batect.model.steps.RunContainerStep
import batect.model.steps.StartContainerStep
import batect.config.Container
import batect.docker.DockerContainer
import batect.logging.Logger

data class ContainerBecameHealthyEvent(val container: Container) : TaskEvent() {
    override fun apply(context: TaskEventContext, logger: Logger) {
        if (context.isAborting) {
            logger.info {
                message("Task is aborting, not queuing any further work.")
                data("event", this@ContainerBecameHealthyEvent.toString())
            }

            return
        }

        val containersThatDependOnThisContainer = context.containersThatDependOn(container)
        val healthyContainers = context.getPastEventsOfType<ContainerBecameHealthyEvent>()
                .mapTo(mutableSetOf()) { it.container }

        containersThatDependOnThisContainer
                .filter { dependenciesAreHealthy(it, healthyContainers, context) }
                .associate { it to dockerContainerFor(it, context) }
                .filter { (_, dockerContainer) -> dockerContainer != null }
                .forEach { (container, dockerContainer) -> runContainer(container, dockerContainer!!, context) }
    }

    private fun dependenciesAreHealthy(container: Container, healthyContainers: Set<Container>, context: TaskEventContext): Boolean {
        val dependencies = context.dependenciesOf(container)

        return healthyContainers.containsAll(dependencies)
    }

    private fun dockerContainerFor(container: Container, context: TaskEventContext): DockerContainer? {
        val event = context.getPastEventsOfType<ContainerCreatedEvent>()
                .singleOrNull { it.container == container }

        return event?.dockerContainer
    }

    private fun runContainer(container: Container, dockerContainer: DockerContainer, context: TaskEventContext) {
        if (context.isTaskContainer(container)) {
            context.queueStep(RunContainerStep(container, dockerContainer))
        } else {
            context.queueStep(StartContainerStep(container, dockerContainer))
        }
    }

    override fun toString() = "${this::class.simpleName}(container: '${container.name}')"
}
