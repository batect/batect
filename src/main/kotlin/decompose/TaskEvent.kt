package decompose

import decompose.config.Container
import decompose.docker.DockerContainer
import decompose.docker.DockerImage
import decompose.docker.DockerNetwork

sealed class TaskEvent {
    override fun toString() = this.javaClass.canonicalName
}

object TaskStartedEvent : TaskEvent()

data class TaskNetworkCreatedEvent(val network: DockerNetwork) : TaskEvent() {
    override fun toString() = super.toString() + "(network: ${network.id})"
}

data class TaskNetworkCreationFailedEvent(val message: String) : TaskEvent() {
    override fun toString() = super.toString() + "(message: $message)"
}

data class ImageBuiltEvent(val container: Container, val image: DockerImage) : TaskEvent() {
    override fun toString() = super.toString() + "(container: ${container.name}, image: ${image.id})"
}

data class ImageBuildFailedEvent(val container: Container, val message: String) : TaskEvent() {
    override fun toString() = super.toString() + "(container: ${container.name}, message: $message)"
}

data class ContainerCreatedEvent(val container: Container, val dockerContainer: DockerContainer) : TaskEvent() {
    override fun toString() = super.toString() + "(container: ${container.name}, Docker container: ${dockerContainer.id})"
}

data class ContainerCreationFailedEvent(val container: Container, val message: String) : TaskEvent() {
    override fun toString() = super.toString() + "(container: ${container.name}, message: $message)"
}

data class ContainerExitedEvent(val container: Container, val exitCode: Int) : TaskEvent() {
    override fun toString() = super.toString() + "(container: ${container.name}, exit code: $exitCode)"
}

data class ContainerStartedEvent(val container: Container) : TaskEvent() {
    override fun toString() = super.toString() + "(container: ${container.name})"
}

data class ContainerStoppedEvent(val container: Container) : TaskEvent() {
    override fun toString() = super.toString() + "(container: ${container.name})"
}

data class ContainerBecameHealthyEvent(val container: Container) : TaskEvent() {
    override fun toString() = super.toString() + "(container: ${container.name})"
}

data class ContainerRunFailedEvent(val container: Container, val message: String) : TaskEvent() {
    override fun toString() = super.toString() + "(container: ${container.name}, message: $message)"
}

object TaskNetworkDeletedEvent : TaskEvent()

data class TaskNetworkDeletionFailedEvent(val message: String) : TaskEvent() {
    override fun toString() = super.toString() + "(message: $message)"
}

data class ContainerRemovedEvent(val container: Container) : TaskEvent() {
    override fun toString() = super.toString() + "(container: ${container.name})"
}

data class ContainerRemovalFailedEvent(val container: Container, val message: String) : TaskEvent() {
    override fun toString() = super.toString() + "(container: ${container.name}, message: $message)"
}
