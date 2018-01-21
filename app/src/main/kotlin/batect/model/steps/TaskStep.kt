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

package batect.model.steps

import batect.config.Container
import batect.docker.DockerContainer
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.model.events.TaskEventContext
import batect.os.Command
import java.nio.file.Path

sealed class TaskStep {
    override fun toString() = this.javaClass.canonicalName
}

object BeginTaskStep : TaskStep()

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
    val image: DockerImage,
    val network: DockerNetwork
) : TaskStep() {
    override fun toString() = super.toString() + "(container: '${container.name}', command: '${(command ?: "")}', additional environment variables: $additionalEnvironmentVariables, image '${image.id}', network: '${network.id}')"

    constructor(container: Container, image: DockerImage, network: DockerNetwork, context: TaskEventContext) : this(
        container,
        context.commandForContainer(container),
        context.additionalEnvironmentVariablesForContainer(container),
        image,
        network
    )
}

data class RunContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = super.toString() + "(container: '${container.name}', Docker container: '${dockerContainer.id}')"
}

data class StartContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = super.toString() + "(container: '${container.name}', Docker container: '${dockerContainer.id}')"
}

data class StopContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = super.toString() + "(container: '${container.name}', Docker container: '${dockerContainer.id}')"
}

data class CleanUpContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = super.toString() + "(container: '${container.name}', Docker container: '${dockerContainer.id}')"
}

data class RemoveContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = super.toString() + "(container: '${container.name}', Docker container: '${dockerContainer.id}')"
}

data class DeleteTemporaryFileStep(val filePath: Path) : TaskStep() {
    override fun toString() = super.toString() + "(file path: '$filePath')"
}

data class WaitForContainerToBecomeHealthyStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep() {
    override fun toString() = super.toString() + "(container: '${container.name}', Docker container: '${dockerContainer.id}')"
}

data class FinishTaskStep(val exitCode: Int) : TaskStep() {
    override fun toString() = super.toString() + "(exit code: $exitCode)"
}

data class DeleteTaskNetworkStep(val network: DockerNetwork) : TaskStep() {
    override fun toString() = super.toString() + "(network: '${network.id}')"
}

data class DisplayTaskFailureStep(val message: String) : TaskStep() {
    override fun toString() = super.toString() + "(message: '$message')"
}
