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

import batect.model.steps.CleanUpContainerStep
import batect.model.steps.RunContainerStep
import batect.model.steps.StartContainerStep
import batect.config.Container
import batect.docker.DockerContainer
import batect.logging.Logger
import batect.model.BehaviourAfterFailure
import batect.model.steps.DisplayTaskFailureStep
import batect.utils.mapToSet

data class ContainerCreatedEvent(val container: Container, val dockerContainer: DockerContainer) : TaskEvent() {
    override fun apply(context: TaskEventContext, logger: Logger) {
        if (context.isAborting) {
            handleTaskAborting(logger, context)
            return
        }

        val dependencies = context.dependenciesOf(container)
        val healthyContainers = context.getPastEventsOfType<ContainerBecameHealthyEvent>()
                .mapToSet() { it.container }

        if (healthyContainers.containsAll(dependencies)) {
            if (context.isTaskContainer(container)) {
                context.queueStep(RunContainerStep(container, dockerContainer))
            } else {
                context.queueStep(StartContainerStep(container, dockerContainer))
            }
        }
    }

    private fun handleTaskAborting(logger: Logger, context: TaskEventContext) {
        if (context.behaviourAfterFailure == BehaviourAfterFailure.Cleanup) {
            logger.info {
                message("Task is aborting, queuing clean up of container that was just created.")
                data("event", this@ContainerCreatedEvent.toString())
            }

            context.queueStep(CleanUpContainerStep(container, dockerContainer))
        } else {
            context.queueStep(DisplayTaskFailureStep("The creation of container '${container.name}' finished after the previous task failure. You can remove it by running 'docker rm --force ${dockerContainer.id}'."))
        }
    }

    override fun toString() = "${this::class.simpleName}(container: '${container.name}', Docker container ID: '${dockerContainer.id}')"
}
