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

package batect.execution.model.events

import batect.config.Container
import batect.config.PullImage
import batect.config.SetupCommand
import batect.docker.DockerContainer
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.docker.client.DockerImageBuildProgress
import batect.docker.pull.DockerImageProgress
import batect.execution.model.steps.TaskStep
import batect.logging.ContainerNameOnlySerializer
import batect.logging.LogMessageBuilder
import batect.logging.PathSerializer
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.SetSerializer

@Serializable
sealed class TaskEvent(
    @Transient val isInformationalEvent: Boolean = false
)

@Serializable
object CachesInitialisedEvent : TaskEvent()

@Serializable
data class ContainerBecameHealthyEvent(val container: Container) : TaskEvent()

@Serializable
data class ContainerBecameReadyEvent(val container: Container) : TaskEvent()

@Serializable
data class ContainerCreatedEvent(val container: Container, val dockerContainer: DockerContainer) : TaskEvent()

@Serializable
data class ContainerRemovedEvent(val container: Container) : TaskEvent()

@Serializable
data class ContainerStartedEvent(val container: Container) : TaskEvent()

@Serializable
data class ContainerStoppedEvent(val container: Container) : TaskEvent()

@Serializable
data class ImageBuildProgressEvent(val container: Container, val buildProgress: DockerImageBuildProgress) : TaskEvent(isInformationalEvent = true)

@Serializable
data class ImageBuiltEvent(val container: Container, val image: DockerImage) : TaskEvent()

@Serializable
data class ImagePulledEvent(val source: PullImage, val image: DockerImage) : TaskEvent()

@Serializable
data class ImagePullProgressEvent(val source: PullImage, val progress: DockerImageProgress) : TaskEvent(isInformationalEvent = true)

@Serializable
data class RunningContainerExitedEvent(val container: Container, val exitCode: Long) : TaskEvent()

@Serializable
data class RunningSetupCommandEvent(val container: Container, val command: SetupCommand, val commandIndex: Int) : TaskEvent()

@Serializable
data class SetupCommandsCompletedEvent(val container: Container) : TaskEvent()

@Serializable
data class StepStartingEvent(val step: TaskStep) : TaskEvent(true)

@Serializable
sealed class TaskNetworkReadyEvent : TaskEvent() {
    abstract val network: DockerNetwork
}

@Serializable
data class TaskNetworkCreatedEvent(override val network: DockerNetwork) : TaskNetworkReadyEvent()

@Serializable
data class CustomTaskNetworkCheckedEvent(override val network: DockerNetwork) : TaskNetworkReadyEvent()

@Serializable
object TaskNetworkDeletedEvent : TaskEvent()

@Serializable
data class TemporaryDirectoryCreatedEvent(val container: Container, val directoryPath: Path) : TaskEvent()

@Serializable
data class TemporaryDirectoryDeletedEvent(val directoryPath: Path) : TaskEvent()

@Serializable
data class TemporaryFileCreatedEvent(val container: Container, val filePath: Path) : TaskEvent()

@Serializable
data class TemporaryFileDeletedEvent(val filePath: Path) : TaskEvent()

sealed class TaskFailedEvent : TaskEvent()

@Serializable
data class ExecutionFailedEvent(val message: String) : TaskFailedEvent()

@Serializable
data class TaskNetworkCreationFailedEvent(val message: String) : TaskFailedEvent()

@Serializable
data class CustomTaskNetworkCheckFailedEvent(val networkIdentifier: String, val message: String) : TaskFailedEvent()

@Serializable
data class ImageBuildFailedEvent(val container: Container, val message: String) : TaskFailedEvent()

@Serializable
data class ImagePullFailedEvent(val source: PullImage, val message: String) : TaskFailedEvent()

@Serializable
data class ContainerCreationFailedEvent(val container: Container, val message: String) : TaskFailedEvent()

@Serializable
data class ContainerDidNotBecomeHealthyEvent(val container: Container, val message: String) : TaskFailedEvent()

@Serializable
data class ContainerRunFailedEvent(val container: Container, val message: String) : TaskFailedEvent()

@Serializable
data class ContainerStopFailedEvent(val container: Container, val message: String) : TaskFailedEvent()

@Serializable
data class ContainerRemovalFailedEvent(val container: Container, val message: String) : TaskFailedEvent()

@Serializable
data class TaskNetworkDeletionFailedEvent(val message: String) : TaskFailedEvent()

@Serializable
data class TemporaryFileDeletionFailedEvent(val filePath: Path, val message: String) : TaskFailedEvent()

@Serializable
data class TemporaryDirectoryDeletionFailedEvent(val directoryPath: Path, val message: String) : TaskFailedEvent()

@Serializable
object UserInterruptedExecutionEvent : TaskFailedEvent()

@Serializable
data class SetupCommandExecutionErrorEvent(val container: Container, val command: SetupCommand, val message: String) : TaskFailedEvent()

@Serializable
data class SetupCommandFailedEvent(val container: Container, val command: SetupCommand, val exitCode: Int, val output: String) : TaskFailedEvent()

@Serializable
data class CacheInitialisationFailedEvent(val message: String) : TaskFailedEvent()

fun LogMessageBuilder.data(key: String, value: TaskEvent) = this.data(key, value, TaskEvent.serializer())
fun LogMessageBuilder.data(key: String, value: Set<TaskEvent>) = this.data(key, value, SetSerializer(TaskEvent.serializer()))
