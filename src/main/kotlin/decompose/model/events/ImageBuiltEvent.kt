package decompose.model.events

import decompose.model.steps.CreateContainerStep
import decompose.config.Container
import decompose.docker.DockerImage

data class ImageBuiltEvent(val container: Container, val image: DockerImage) : TaskEvent() {
    override fun apply(context: TaskEventContext) {
        if (context.isAborting) {
            return
        }

        val networkCreationEvent = context.getSinglePastEventOfType<TaskNetworkCreatedEvent>()

        if (networkCreationEvent != null) {
            val command = context.commandForContainer(container)
            context.queueStep(CreateContainerStep(container, command, image, networkCreationEvent.network))
        }
    }

    override fun toString() = "${this::class.simpleName}(container: '${container.name}', image: '${image.id}')"
}
