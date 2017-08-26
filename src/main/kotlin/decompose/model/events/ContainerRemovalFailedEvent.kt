package decompose.model.events

import decompose.config.Container

data class ContainerRemovalFailedEvent(override val container: Container, val message: String) : PostTaskRunCleanupFailureEvent(container) {
    override val messageToDisplay: String
        get() = "the container '${container.name}' couldn't be removed: $message"

    override fun toString() = "${this::class.simpleName}(container: '${container.name}', message: '$message')"
}
