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

import batect.model.steps.CreateContainerStep
import batect.model.steps.DeleteTaskNetworkStep
import batect.config.Container
import batect.logging.Logger

data class ContainerRemovedEvent(val container: Container) : TaskEvent() {
    override fun apply(context: TaskEventContext, logger: Logger) {
        val containersToRemove = context.getPendingAndProcessedStepsOfType<CreateContainerStep>()
                .mapTo(mutableSetOf()) { it.container }

        val containersRemoved = context.getPastEventsOfType<ContainerRemovedEvent>()
                .mapTo(mutableSetOf()) { it.container }

        val containersThatFailedToBeCreated = context.getPastEventsOfType<ContainerCreationFailedEvent>()
                .mapTo(mutableSetOf()) { it.container }

        val containersEffectivelyRemoved = containersRemoved + containersThatFailedToBeCreated

        if (containersEffectivelyRemoved.containsAll(containersToRemove)) {
            logger.info {
                message("All containers that need to be removed have been removed, deleting network.")
                data("event", this@ContainerRemovedEvent.toString())
            }

            val network = context.getSinglePastEventOfType<TaskNetworkCreatedEvent>()!!.network

            context.queueStep(DeleteTaskNetworkStep(network))
        } else {
            logger.debug {
                message("Not deleting network yet, some containers still need to be removed.")
                data("event", this@ContainerRemovedEvent.toString())
                data("containersToRemove", containersToRemove.map { it.name })
                data("containersRemoved", containersRemoved.map { it.name })
                data("containersThatFailedToBeCreated", containersThatFailedToBeCreated.map { it.name })
            }
        }
    }

    override fun toString() = "${this::class.simpleName}(container: '${container.name}')"
}
