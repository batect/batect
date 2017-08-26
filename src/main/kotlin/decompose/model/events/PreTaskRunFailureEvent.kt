package decompose.model.events

import decompose.model.steps.BuildImageStep
import decompose.model.steps.CleanUpContainerStep
import decompose.model.steps.CreateContainerStep
import decompose.model.steps.CreateTaskNetworkStep
import decompose.model.steps.DeleteTaskNetworkStep
import decompose.model.steps.DisplayTaskFailureStep
import decompose.model.steps.StartContainerStep
import decompose.model.steps.WaitForContainerToBecomeHealthyStep

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
