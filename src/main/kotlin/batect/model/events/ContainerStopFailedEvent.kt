package batect.model.events

import batect.config.Container

data class ContainerStopFailedEvent(override val container: Container, val message: String) : PostTaskRunCleanupFailureEvent(container) {
    override val messageToDisplay: String
        get() = "the container '${container.name}' couldn't be stopped: $message"

    override fun toString() = "${this::class.simpleName}(container: '${container.name}', message: '$message')"
}
