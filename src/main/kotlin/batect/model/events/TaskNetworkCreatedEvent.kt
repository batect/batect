package batect.model.events

import batect.model.steps.CreateContainerStep
import batect.model.steps.DeleteTaskNetworkStep
import batect.docker.DockerNetwork

data class TaskNetworkCreatedEvent(val network: DockerNetwork) : TaskEvent() {
    override fun apply(context: TaskEventContext) {
        if (context.isAborting) {
            context.queueStep(DeleteTaskNetworkStep(network))
            return
        }

        context.getPastEventsOfType<ImageBuiltEvent>()
                .forEach {
                    val command = context.commandForContainer(it.container)
                    context.queueStep(CreateContainerStep(it.container, command, it.image, network))
                }
    }

    override fun toString() = "${this::class.simpleName}(network ID: '${network.id}')"
}
