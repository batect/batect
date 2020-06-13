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

@file:UseSerializers(
    ContainerNameOnlySerializer::class,
    PathSerializer::class
)

package batect.execution.model.steps

import batect.config.Container
import batect.logging.ContainerNameOnlySerializer
import batect.config.PullImage
import batect.docker.DockerContainer
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.execution.ContainerRuntimeConfiguration
import batect.logging.LogMessageBuilder
import batect.logging.PathSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.nio.file.Path

@Serializable
sealed class TaskStep

@Serializable
data class BuildImageStep(val container: Container) : TaskStep()

@Serializable
data class PullImageStep(val source: PullImage) : TaskStep()

@Serializable
object CreateTaskNetworkStep : TaskStep()

@Serializable
object InitialiseCachesStep : TaskStep()

@Serializable
data class CreateContainerStep(
    val container: Container,
    val config: ContainerRuntimeConfiguration,
    val image: DockerImage,
    val network: DockerNetwork
) : TaskStep()

@Serializable
data class RunContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep()

@Serializable
data class RunContainerSetupCommandsStep(
    val container: Container,
    val config: ContainerRuntimeConfiguration,
    val dockerContainer: DockerContainer
) : TaskStep()

@Serializable
data class WaitForContainerToBecomeHealthyStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep()

sealed class CleanupStep : TaskStep()

@Serializable
data class StopContainerStep(val container: Container, val dockerContainer: DockerContainer) : CleanupStep()

@Serializable
data class RemoveContainerStep(val container: Container, val dockerContainer: DockerContainer) : CleanupStep()

@Serializable
data class DeleteTemporaryFileStep(val filePath: Path) : CleanupStep()

@Serializable
data class DeleteTemporaryDirectoryStep(val directoryPath: Path) : CleanupStep()

@Serializable
data class DeleteTaskNetworkStep(val network: DockerNetwork) : CleanupStep()

fun LogMessageBuilder.data(name: String, step: TaskStep) = this.data(name, step, TaskStep.serializer())
