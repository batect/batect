package decompose.model.events

import decompose.BuildImageStep
import decompose.CleanUpContainerStep
import decompose.CreateContainerStep
import decompose.CreateTaskNetworkStep
import decompose.DeleteTaskNetworkStep
import decompose.DisplayTaskFailureStep
import decompose.StartContainerStep
import decompose.WaitForContainerToBecomeHealthyStep

abstract class PreTaskRunFailureEvent() : TaskEvent() {
    override fun apply(context: TaskEventContext) {
        context.abort()
        context.removePendingStepsOfType<BuildImageStep>()
        context.removePendingStepsOfType<CreateContainerStep>()
        context.removePendingStepsOfType<CreateTaskNetworkStep>()
        context.removePendingStepsOfType<StartContainerStep>()
        context.removePendingStepsOfType<WaitForContainerToBecomeHealthyStep>()

        context.queueStep(DisplayTaskFailureStep(messageToDisplay))

        val containerCreationEvents = context.getPastEventsOfType<ContainerCreatedEvent>()
        val networkCreationEvent = context.getSinglePastEventOfType<TaskNetworkCreatedEvent>()

        if (containerCreationEvents.isNotEmpty()) {
            containerCreationEvents.forEach {
                context.queueStep(CleanUpContainerStep(it.container, it.dockerContainer))
            }
        } else if (networkCreationEvent != null) {
            context.queueStep(DeleteTaskNetworkStep(networkCreationEvent.network))
        }
    }

    abstract val messageToDisplay: String
}
