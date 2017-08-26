package decompose.model.events

import decompose.model.steps.CreateContainerStep
import decompose.model.steps.DeleteTaskNetworkStep
import decompose.docker.DockerNetwork

data class TaskNetworkCreatedEvent(val network: DockerNetwork) : TaskEvent() {
    override fun apply(context: TaskEventContext) {
        if (context.isAborting) {
            context.queueStep(DeleteTaskNetworkStep(network))
            return
        }

        context.getPastEventsOfType<ImageBuiltEvent>()
                .forEach { context.queueStep(CreateContainerStep(it.container, it.image, network)) }
    }

    override fun toString() = "${this::class.simpleName}(network ID: '${network.id}')"
}
