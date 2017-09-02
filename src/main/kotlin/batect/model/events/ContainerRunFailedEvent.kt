package batect.model.events

import batect.config.Container

data class ContainerRunFailedEvent(val container: Container, val message: String) : PreTaskRunFailureEvent() {
    override val messageToDisplay: String
        get() = "Could not run container '${container.name}': $message"

    override fun toString() = "${this::class.simpleName}(container: '${container.name}', message: '$message')"
}
