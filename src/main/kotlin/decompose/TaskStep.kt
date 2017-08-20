package decompose

import decompose.config.Container
import decompose.docker.DockerContainer
import decompose.docker.DockerImage
import decompose.docker.DockerNetwork

sealed class TaskStep {
    override fun toString() = this.javaClass.canonicalName
}

object BeginTaskStep : TaskStep()

data class BuildImageStep(val container: Container) : TaskStep() {
    override fun toString() = super.toString() + "(container: ${container.name})"
}

object CreateTaskNetworkStep : TaskStep()

data class CreateContainerStep(val container: Container, val image: DockerImage, val network: DockerNetwork) : TaskStep() {
    override fun toString() = super.toString() + "(container: ${container.name}, image ${image.id}, network: ${network.id}"
}

data class RunContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = super.toString() + "(container: ${container.name}, Docker container: ${dockerContainer.id})"
}

data class StartContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = super.toString() + "(container: ${container.name}, Docker container: ${dockerContainer.id})"
}

data class StopContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = super.toString() + "(container: ${container.name}, Docker container: ${dockerContainer.id})"
}

data class CleanUpContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = super.toString() + "(container: ${container.name}, Docker container: ${dockerContainer.id})"
}

data class RemoveContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = super.toString() + "(container: ${container.name}, Docker container: ${dockerContainer.id})"
}

data class WaitForContainerToBecomeHealthyStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = super.toString() + "(container: ${container.name}, Docker container: ${dockerContainer.id})"
}

data class FinishTaskStep(val exitCode: Int) : TaskStep() {
    override fun toString() = super.toString() + "(exit code: $exitCode)"
}

data class DeleteTaskNetworkStep(val network: DockerNetwork) : TaskStep() {
    override fun toString() = super.toString() + "(network: ${network.id})"
}

data class DisplayTaskFailureStep(val message: String) : TaskStep() {
    override fun toString() = super.toString() + "(message: $message)"
}
