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

import batect.cli.CommandLineOptionsParser
import batect.logging.Logger
import batect.model.BehaviourAfterFailure
import batect.model.steps.BuildImageStep
import batect.model.steps.CleanUpContainerStep
import batect.model.steps.CreateContainerStep
import batect.model.steps.CreateTaskNetworkStep
import batect.model.steps.DeleteTaskNetworkStep
import batect.model.steps.DisplayTaskFailureStep
import batect.model.steps.PullImageStep
import batect.model.steps.StartContainerStep
import batect.model.steps.WaitForContainerToBecomeHealthyStep
import batect.utils.mapToSet

abstract class PreTaskRunFailureEvent(private val displayCleanupFlagMessage: Boolean) : TaskEvent() {
    override fun apply(context: TaskEventContext, logger: Logger) {
        context.abort()
        context.removePendingStepsOfType<BuildImageStep>()
        context.removePendingStepsOfType<PullImageStep>()
        context.removePendingStepsOfType<CreateContainerStep>()
        context.removePendingStepsOfType<CreateTaskNetworkStep>()
        context.removePendingStepsOfType<StartContainerStep>()
        context.removePendingStepsOfType<WaitForContainerToBecomeHealthyStep>()

        val containerCreationEvents = context.getPastEventsOfType<ContainerCreatedEvent>()
        val networkCreationEvent = context.getSinglePastEventOfType<TaskNetworkCreatedEvent>()

        queueDisplayingMessage(containerCreationEvents, networkCreationEvent, context)

        if (containerCreationEvents.isNotEmpty()) {
            cleanUpContainers(containerCreationEvents, context, logger)
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

    private fun queueDisplayingMessage(
        containerCreationEvents: Set<ContainerCreatedEvent>,
        networkCreatedEvent: TaskNetworkCreatedEvent?,
        context: TaskEventContext
    ) {
        if (containerCreationEvents.isNotEmpty() && context.behaviourAfterFailure == BehaviourAfterFailure.DontCleanup) {
            val containersStarted = context.getPastEventsOfType<ContainerStartedEvent>()
                .mapToSet { it.container }

            val logInstructions = containerCreationEvents
                .filter { it.container in containersStarted }
                .map { "For container '${it.container.name}': view its output by running 'docker logs ${it.dockerContainer.id}', or run a command in the container with 'docker exec -it ${it.dockerContainer.id} <command>'." }
                .joinToString("\n")

            val dockerContainerIDs = containerCreationEvents.map { it.dockerContainer.id }.joinToString(" ")
            val cleanupCommand = "docker rm --force $dockerContainerIDs && docker network rm ${networkCreatedEvent!!.network.id}"

            val message = "$messageToDisplay\n" +
                "\n" +
                "As the task was run with --${CommandLineOptionsParser.disableCleanupAfterFailureFlagName}, the created containers will not be cleaned up.\n" +
                "\n" +
                logInstructions + "\n" +
                "\n" +
                "To clean up the containers and task network once you have finished investigating the issue, run '$cleanupCommand'."

            context.queueStep(DisplayTaskFailureStep(message))
        } else if (displayCleanupFlagMessage) {
            context.queueStep(DisplayTaskFailureStep(messageToDisplay + "\n\nYou can re-run the task with --${CommandLineOptionsParser.disableCleanupAfterFailureFlagName} to leave the created containers running to diagnose the issue."))
        } else {
            context.queueStep(DisplayTaskFailureStep(messageToDisplay))
        }
    }

    private fun cleanUpContainers(containerCreationEvents: Set<ContainerCreatedEvent>, context: TaskEventContext, logger: Logger) {
        if (context.behaviourAfterFailure == BehaviourAfterFailure.Cleanup) {
            logger.info {
                message("Need to clean up some containers that have already been created.")
                data("containers", containerCreationEvents.map { it.container.name })
                data("event", this@PreTaskRunFailureEvent.toString())
            }

            containerCreationEvents.forEach {
                context.queueStep(CleanUpContainerStep(it.container, it.dockerContainer))
            }
        } else {
            logger.info {
                message("Not cleaning up containers that have already been created because task was started with --${CommandLineOptionsParser.disableCleanupAfterFailureFlagName}.")
                data("containers", containerCreationEvents.map { it.container.name })
                data("event", this@PreTaskRunFailureEvent.toString())
            }
        }
    }

    abstract val messageToDisplay: String
}
