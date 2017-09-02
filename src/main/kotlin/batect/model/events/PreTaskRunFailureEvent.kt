package batect.model.events

import batect.model.steps.BuildImageStep
import batect.model.steps.CleanUpContainerStep
import batect.model.steps.CreateContainerStep
import batect.model.steps.CreateTaskNetworkStep
import batect.model.steps.DeleteTaskNetworkStep
import batect.model.steps.DisplayTaskFailureStep
import batect.model.steps.StartContainerStep
import batect.model.steps.WaitForContainerToBecomeHealthyStep

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
