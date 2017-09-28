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
import batect.model.steps.DisplayTaskFailureStep
import batect.model.steps.RemoveContainerStep
import batect.model.steps.StopContainerStep
import batect.config.Container
import batect.logging.Logger
import batect.utils.mapToSet

abstract class PostTaskRunCleanupFailureEvent(open val container: Container) : TaskEvent() {
    override fun apply(context: TaskEventContext, logger: Logger) {
        val dockerContainer = context.getPastEventsOfType<ContainerCreatedEvent>()
                .single { it.container == container }
                .dockerContainer

        val network = context.getSinglePastEventOfType<TaskNetworkCreatedEvent>()!!
                .network

        val message = "${situationDescription(context)}, $messageToDisplay\n\n" +
                "This container may not have been cleaned up completely, so you may need to remove this container yourself by running 'docker rm --force ${dockerContainer.id}'.\n" +
                "Furthermore, the task network cannot be automatically cleaned up, so you will need to clean up this network yourself by running 'docker network rm ${network.id}'.\n"

        context.queueStep(DisplayTaskFailureStep(message))

        if (!context.isAborting) {
            context.abort()
            cleanUp(context)
        }
    }

    private fun situationDescription(context: TaskEventContext): String {
        if (context.isAborting) {
            return "During clean up after the previous failure"
        } else {
            val exitCode = context.getSinglePastEventOfType<RunningContainerExitedEvent>()!!.exitCode

            return "After the task exited with exit code $exitCode"
        }
    }

    private fun cleanUp(context: TaskEventContext) {
        context.removePendingStepsOfType<StopContainerStep>()
        context.removePendingStepsOfType<RemoveContainerStep>()

        val containersCreated = context.getPastEventsOfType<ContainerCreatedEvent>()
                .associate { it.container to it.dockerContainer }

        val containersAlreadyRemoved = context.getProcessedStepsOfType<RemoveContainerStep>()
                .mapToSet() { it.container }

        val containersLeftToRemove = containersCreated - containersAlreadyRemoved

        containersLeftToRemove
                .forEach { (container, dockerContainer) -> context.queueStep(CleanUpContainerStep(container, dockerContainer)) }
    }

    abstract val messageToDisplay: String
}
