package decompose.model.events

import decompose.CreateContainerStep
import decompose.DeleteTaskNetworkStep
import decompose.config.Container

data class ContainerRemovedEvent(val container: Container) : TaskEvent() {
    override fun apply(context: TaskEventContext) {
        val containersToRemove = context.getPendingAndProcessedStepsOfType<CreateContainerStep>()
                .mapTo(mutableSetOf()) { it.container }

        val containersRemoved = context.getPastEventsOfType<ContainerRemovedEvent>()
                .mapTo(mutableSetOf()) { it.container }

        val containersThatFailedToBeCreated = context.getPastEventsOfType<ContainerCreationFailedEvent>()
                .mapTo(mutableSetOf()) { it.container }

        val containersEffectivelyRemoved = containersRemoved + containersThatFailedToBeCreated

        if (containersEffectivelyRemoved.containsAll(containersToRemove)) {
            val network = context.getSinglePastEventOfType<TaskNetworkCreatedEvent>()!!.network

            context.queueStep(DeleteTaskNetworkStep(network))
        }
    }

    override fun toString() = "${this::class.simpleName}(container: '${container.name}')"
}
