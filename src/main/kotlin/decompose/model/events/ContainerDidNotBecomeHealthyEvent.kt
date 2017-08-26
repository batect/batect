package decompose.model.events

import decompose.config.Container

data class ContainerDidNotBecomeHealthyEvent(val container: Container, val message: String) : PreTaskRunFailureEvent() {
    override val messageToDisplay: String
        get() = "Container '${container.name}' did not become healthy: $message"

    override fun toString() = "${this::class.simpleName}(container: '${container.name}', message: '$message')"
}
