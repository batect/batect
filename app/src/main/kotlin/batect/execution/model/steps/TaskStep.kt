/*
   Copyright 2017-2020 Charles Korn.

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

import batect.config.BuildImage
import batect.config.Container
import batect.config.PullImage
import batect.docker.DockerContainer
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.docker.client.DockerContainerType
import batect.execution.ContainerRuntimeConfiguration
import java.nio.file.Path

sealed class TaskStep

data class BuildImageStep(
    val source: BuildImage,
    val imageTags: Set<String>
) : TaskStep() {
    override fun toString() = "${this.javaClass.simpleName}(source: $source, image tags: $imageTags)"
}

data class PullImageStep(val source: PullImage) : TaskStep() {
    override fun toString() = "${this.javaClass.simpleName}(source: $source)"
}

data class CreateTaskNetworkStep(val containerType: DockerContainerType) : TaskStep() {
    override fun toString(): String = "${this.javaClass.simpleName}(container type: $containerType)"
}

data class CreateContainerStep(
    val container: Container,
    val config: ContainerRuntimeConfiguration,
    val allContainersInNetwork: Set<Container>,
    val image: DockerImage,
    val network: DockerNetwork
) : TaskStep() {
    override fun toString() = "${this.javaClass.simpleName}(container: '${container.name}', " +
        "config: $config, " +
        "all containers in network: ${allContainersInNetwork.map { "'${it.name}'" }}, " +
        "image: '${image.id}', network: '${network.id}')"
}

data class RunContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = "${this.javaClass.simpleName}(container: '${container.name}', Docker container: '${dockerContainer.id}')"
}

data class RunContainerSetupCommandsStep(
    val container: Container,
    val config: ContainerRuntimeConfiguration,
    val allContainersInNetwork: Set<Container>,
    val dockerContainer: DockerContainer
) : TaskStep() {
    override fun toString() = "${this.javaClass.simpleName}(container: '${container.name}', " +
        "config: $config, " +
        "all containers in network: ${allContainersInNetwork.map { "'${it.name}'" }}, " +
        "Docker container: '${dockerContainer.id}')"
}

data class WaitForContainerToBecomeHealthyStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = "${this.javaClass.simpleName}(container: '${container.name}', Docker container: '${dockerContainer.id}')"
}

sealed class CleanupStep : TaskStep()

data class StopContainerStep(val container: Container, val dockerContainer: DockerContainer) : CleanupStep() {
    override fun toString() = "${this.javaClass.simpleName}(container: '${container.name}', Docker container: '${dockerContainer.id}')"
}

data class RemoveContainerStep(val container: Container, val dockerContainer: DockerContainer) : CleanupStep() {
    override fun toString() = "${this.javaClass.simpleName}(container: '${container.name}', Docker container: '${dockerContainer.id}')"
}

data class DeleteTemporaryFileStep(val filePath: Path) : CleanupStep() {
    override fun toString() = "${this.javaClass.simpleName}(file path: '$filePath')"
}

data class DeleteTemporaryDirectoryStep(val directoryPath: Path) : CleanupStep() {
    override fun toString() = "${this.javaClass.simpleName}(directory path: '$directoryPath')"
}

data class DeleteTaskNetworkStep(val network: DockerNetwork) : CleanupStep() {
    override fun toString() = "${this.javaClass.simpleName}(network: '${network.id}')"
}
