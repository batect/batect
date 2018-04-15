/*
   Copyright 2017-2018 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.execution.model.steps

import batect.config.Container
import batect.config.PortMapping
import batect.docker.DockerContainer
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.os.Command
import java.nio.file.Path

sealed class TaskStep {
    override fun toString() = this.javaClass.canonicalName
}

data class BuildImageStep(val projectName: String, val container: Container) : TaskStep() {
    override fun toString() = super.toString() + "(project name: '$projectName', container: '${container.name}')"
}

data class PullImageStep(val imageName: String) : TaskStep() {
    override fun toString() = super.toString() + "(image name: '$imageName')"
}

object CreateTaskNetworkStep : TaskStep()

data class CreateContainerStep(
    val container: Container,
    val command: Command?,
    val additionalEnvironmentVariables: Map<String, String>,
    val additionalPortMappings: Set<PortMapping>,
    val image: DockerImage,
    val network: DockerNetwork
) : TaskStep() {
    override fun toString() = super.toString() + "(container: '${container.name}', command: '${(command ?: "")}', additional environment variables: $additionalEnvironmentVariables, additional port mappings: $additionalPortMappings, image '${image.id}', network: '${network.id}')"
}

data class RunContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = super.toString() + "(container: '${container.name}', Docker container: '${dockerContainer.id}')"
}

data class StartContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = super.toString() + "(container: '${container.name}', Docker container: '${dockerContainer.id}')"
}

data class WaitForContainerToBecomeHealthyStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = super.toString() + "(container: '${container.name}', Docker container: '${dockerContainer.id}')"
}

sealed class CleanupStep : TaskStep()

data class StopContainerStep(val container: Container, val dockerContainer: DockerContainer) : CleanupStep() {
    override fun toString() = super.toString() + "(container: '${container.name}', Docker container: '${dockerContainer.id}')"
}

data class RemoveContainerStep(val container: Container, val dockerContainer: DockerContainer) : CleanupStep() {
    override fun toString() = super.toString() + "(container: '${container.name}', Docker container: '${dockerContainer.id}')"
}

data class DeleteTemporaryFileStep(val filePath: Path) : CleanupStep() {
    override fun toString() = super.toString() + "(file path: '$filePath')"
}

data class DeleteTaskNetworkStep(val network: DockerNetwork) : CleanupStep() {
    override fun toString() = super.toString() + "(network: '${network.id}')"
}
