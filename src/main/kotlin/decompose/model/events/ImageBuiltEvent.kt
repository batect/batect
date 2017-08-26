package decompose.model.events

import decompose.CreateContainerStep
import decompose.config.Container
import decompose.docker.DockerImage

data class ImageBuiltEvent(val container: Container, val image: DockerImage) : TaskEvent() {
    override fun apply(context: TaskEventContext) {
        if (context.isAborting) {
            return
        }

        val networkCreationEvent = context.getSinglePastEventOfType<TaskNetworkCreatedEvent>()

        if (networkCreationEvent != null) {
            context.queueStep(CreateContainerStep(container, image, networkCreationEvent.network))
        }
    }

    override fun toString() = "${this::class.simpleName}(container: '${container.name}', image: '${image.id}')"
}
