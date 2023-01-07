/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

@file:UseSerializers(
    ContainerNameOnlySerializer::class,
    PathSerializer::class,
    ImageReferenceSerializer::class,
    NetworkReferenceSerializer::class,
)

package batect.execution.model.steps

import batect.config.Container
import batect.config.PullImage
import batect.docker.DockerContainer
import batect.docker.ImageReferenceSerializer
import batect.docker.NetworkReferenceSerializer
import batect.dockerclient.ImageReference
import batect.dockerclient.NetworkReference
import batect.logging.ContainerNameOnlySerializer
import batect.logging.LogMessageBuilder
import batect.logging.PathSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers

@Serializable
sealed class TaskStep(
    @Transient val countsAgainstParallelismCap: Boolean = true,
)

@Serializable
data class BuildImageStep(val container: Container) : TaskStep()

@Serializable
data class PullImageStep(val source: PullImage) : TaskStep()

@Serializable
object PrepareTaskNetworkStep : TaskStep()

@Serializable
data class CreateContainerStep(
    val container: Container,
    val image: ImageReference,
    val network: NetworkReference,
) : TaskStep()

@Serializable
data class RunContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep(countsAgainstParallelismCap = false)

@Serializable
data class RunContainerSetupCommandsStep(
    val container: Container,
    val dockerContainer: DockerContainer,
) : TaskStep()

@Serializable
data class WaitForContainerToBecomeHealthyStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep()

sealed class CleanupStep : TaskStep()

@Serializable
data class StopContainerStep(val container: Container, val dockerContainer: DockerContainer) : CleanupStep()

@Serializable
data class RemoveContainerStep(val container: Container, val dockerContainer: DockerContainer) : CleanupStep()

@Serializable
data class DeleteTaskNetworkStep(val network: NetworkReference) : CleanupStep()

fun LogMessageBuilder.data(name: String, step: TaskStep) = this.data(name, step, TaskStep.serializer())
