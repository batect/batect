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

import batect.logging.Logger
import batect.model.steps.BuildImageStep
import batect.model.steps.CleanUpContainerStep
import batect.model.steps.CreateContainerStep
import batect.model.steps.CreateTaskNetworkStep
import batect.model.steps.DeleteTaskNetworkStep
import batect.model.steps.DisplayTaskFailureStep
import batect.model.steps.PullImageStep
import batect.model.steps.StartContainerStep
import batect.model.steps.WaitForContainerToBecomeHealthyStep

abstract class PreTaskRunFailureEvent() : TaskEvent() {
    override fun apply(context: TaskEventContext, logger: Logger) {
        context.abort()
        context.removePendingStepsOfType<BuildImageStep>()
        context.removePendingStepsOfType<PullImageStep>()
        context.removePendingStepsOfType<CreateContainerStep>()
        context.removePendingStepsOfType<CreateTaskNetworkStep>()
        context.removePendingStepsOfType<StartContainerStep>()
        context.removePendingStepsOfType<WaitForContainerToBecomeHealthyStep>()

        context.queueStep(DisplayTaskFailureStep(messageToDisplay))

        val containerCreationEvents = context.getPastEventsOfType<ContainerCreatedEvent>()
        val networkCreationEvent = context.getSinglePastEventOfType<TaskNetworkCreatedEvent>()

        if (containerCreationEvents.isNotEmpty()) {
            logger.info {
                message("Need to clean up some containers that have already been created.")
                data("containers", containerCreationEvents.map { it.container.name })
                data("event", this@PreTaskRunFailureEvent.toString())
            }

            containerCreationEvents.forEach {
                context.queueStep(CleanUpContainerStep(it.container, it.dockerContainer))
            }
        } else if (networkCreationEvent != null) {
            logger.info {
                message("No containers have been created yet, but do need to remove network.")
                data("event", this@PreTaskRunFailureEvent.toString())
            }

            context.queueStep(DeleteTaskNetworkStep(networkCreationEvent.network))
        } else {
            logger.info {
                message("Neither the network nor any containers have been created yet, no clean up required.")
                data("event", this@PreTaskRunFailureEvent.toString())
            }
        }
    }

    abstract val messageToDisplay: String
}
