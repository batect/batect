package batect.model.events

import batect.model.steps.CleanUpContainerStep
import batect.model.steps.DisplayTaskFailureStep
import batect.model.steps.RemoveContainerStep
import batect.model.steps.StopContainerStep
import batect.config.Container

abstract class PostTaskRunCleanupFailureEvent(open val container: Container) : TaskEvent() {
    override fun apply(context: TaskEventContext) {
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
                .mapTo(mutableSetOf()) { it.container }

        val containersLeftToRemove = containersCreated - containersAlreadyRemoved

        containersLeftToRemove
                .forEach { (container, dockerContainer) -> context.queueStep(CleanUpContainerStep(container, dockerContainer)) }
    }

    abstract val messageToDisplay: String
}
