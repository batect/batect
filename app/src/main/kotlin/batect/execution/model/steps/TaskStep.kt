/*
   Copyright 2017-2019 Charles Korn.

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
import batect.config.EnvironmentVariableExpression
import batect.config.PortMapping
import batect.docker.DockerContainer
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.os.Command
import java.nio.file.Path

sealed class TaskStep

data class BuildImageStep(
    val buildDirectory: String,
    val buildArgs: Map<String, String>,
    val dockerfilePath: String,
    val imageTags: Set<String>
) : TaskStep() {
    override fun toString() = "${this.javaClass.simpleName}(" +
        "build directory: '$buildDirectory', " +
        "build args: [${buildArgs.map { "${it.key}=${it.value}" }.joinToString(", ")}], " +
        "Dockerfile path: '$dockerfilePath', " +
        "image tags: $imageTags)"
}

data class PullImageStep(val imageName: String) : TaskStep() {
    override fun toString() = "${this.javaClass.simpleName}(image name: '$imageName')"
}

object CreateTaskNetworkStep : TaskStep() {
    override fun toString(): String = this.javaClass.simpleName
}

data class CreateContainerStep(
    val container: Container,
    val command: Command?,
    val workingDirectory: String?,
    val additionalEnvironmentVariables: Map<String, EnvironmentVariableExpression>,
    val additionalPortMappings: Set<PortMapping>,
    val allContainersInNetwork: Set<Container>,
    val image: DockerImage,
    val network: DockerNetwork
) : TaskStep() {
    override fun toString() = "${this.javaClass.simpleName}(container: '${container.name}', " +
        "command: ${command?.parsedCommand ?: "null"}, " +
        "working directory: ${workingDirectory ?: "null"}, " +
        "additional environment variables: [${additionalEnvironmentVariables.map { "${it.key}=${it.value}" }.joinToString(", ")}], " +
        "additional port mappings: $additionalPortMappings, " +
        "all containers in network: ${allContainersInNetwork.map { "'${it.name}'" }}, " +
        "image: '${image.id}', network: '${network.id}')"
}

data class RunContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = "${this.javaClass.simpleName}(container: '${container.name}', Docker container: '${dockerContainer.id}')"
}

data class StartContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = "${this.javaClass.simpleName}(container: '${container.name}', Docker container: '${dockerContainer.id}')"
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
