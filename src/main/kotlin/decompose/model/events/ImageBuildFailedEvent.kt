package decompose.model.events

import decompose.config.Container

data class ImageBuildFailedEvent(val container: Container, val message: String) : PreTaskRunFailureEvent() {
    override val messageToDisplay: String
        get() = "Could not build image for container '${container.name}': $message"

    override fun toString() = "${this::class.simpleName}(container: '${container.name}', message: '$message')"
}
