package decompose

import decompose.config.Container
import decompose.docker.DockerContainer
import decompose.docker.DockerImage
import decompose.docker.DockerNetwork

sealed class TaskStep
object BeginTaskStep : TaskStep()
data class BuildImageStep(val container: Container) : TaskStep()
object CreateTaskNetworkStep : TaskStep()
data class CreateContainerStep(val container: Container, val image: DockerImage, val network: DockerNetwork) : TaskStep()
data class RunContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep()
data class StartContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep()
data class StopContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep()
data class RemoveContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep()
data class WaitForContainerToBecomeHealthyStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep()
data class FinishTaskStep(val exitCode: Int) : TaskStep()
data class DeleteTaskNetworkStep(val network: DockerNetwork) : TaskStep()
data class DisplayTaskFailureStep(val message: String) : TaskStep()
