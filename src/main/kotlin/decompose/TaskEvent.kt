package decompose

import decompose.config.Container
import decompose.docker.DockerContainer
import decompose.docker.DockerImage
import decompose.docker.DockerNetwork

sealed class TaskEvent
object TaskStartedEvent : TaskEvent()
data class TaskNetworkCreatedEvent(val network: DockerNetwork) : TaskEvent()
data class TaskNetworkCreationFailedEvent(val message: String) : TaskEvent()
data class ImageBuiltEvent(val container: Container, val image: DockerImage) : TaskEvent()
data class ImageBuildFailedEvent(val container: Container, val message: String) : TaskEvent()
data class ContainerCreatedEvent(val container: Container, val dockerContainer: DockerContainer) : TaskEvent()
data class ContainerCreationFailedEvent(val container: Container, val message: String) : TaskEvent()
data class ContainerExitedEvent(val container: Container, val exitCode: Int) : TaskEvent()
data class ContainerStartedEvent(val container: Container) : TaskEvent()
data class ContainerStoppedEvent(val container: Container) : TaskEvent()
data class ContainerBecameHealthyEvent(val container: Container) : TaskEvent()
data class ContainerRunFailedEvent(val container: Container, val message: String) : TaskEvent()
object TaskNetworkDeletedEvent : TaskEvent()
data class TaskNetworkDeletionFailedEvent(val message: String) : TaskEvent()
data class ContainerRemovedEvent(val container: Container) : TaskEvent()
data class ContainerRemovalFailedEvent(val container: Container, val message: String) : TaskEvent()
