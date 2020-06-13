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
import java.nio.file.Path

sealed class TaskEvent(val isInformationalEvent: Boolean = false)

object CachesInitialisedEvent : TaskEvent() {
    override fun toString() = this::class.simpleName!!
}

data class ContainerBecameHealthyEvent(val container: Container) : TaskEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}')"
}

data class ContainerBecameReadyEvent(val container: Container) : TaskEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}')"
}

data class ContainerCreatedEvent(val container: Container, val dockerContainer: DockerContainer) : TaskEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}', Docker container ID: '${dockerContainer.id}')"
}

data class ContainerRemovedEvent(val container: Container) : TaskEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}')"
}

data class ContainerStartedEvent(val container: Container) : TaskEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}')"
}

data class ContainerStoppedEvent(val container: Container) : TaskEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}')"
}

data class ImageBuildProgressEvent(val container: Container, val buildProgress: DockerImageBuildProgress) : TaskEvent(isInformationalEvent = true) {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}', current step: ${buildProgress.currentStep}, total steps: ${buildProgress.totalSteps}, message: '${buildProgress.message}', pull progress: ${formatPullProgress()})"

    private fun formatPullProgress() = if (buildProgress.progress == null) {
        "null"
    } else {
        "'" + buildProgress.progress.toStringForDisplay() + "'"
    }
}

data class ImageBuiltEvent(val container: Container, val image: DockerImage) : TaskEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}', image: '${image.id}')"
}

data class ImagePulledEvent(val source: PullImage, val image: DockerImage) : TaskEvent() {
    override fun toString() = "${this::class.simpleName}(source: $source, image: '${image.id}')"
}

data class ImagePullProgressEvent(val source: PullImage, val progress: DockerImageProgress) : TaskEvent(isInformationalEvent = true) {
    override fun toString() = "${this::class.simpleName}(source: $source, current operation: '${progress.currentOperation}', completed bytes: ${progress.completedBytes}, total bytes: ${progress.totalBytes})"
}

data class RunningContainerExitedEvent(val container: Container, val exitCode: Long) : TaskEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}', exit code: $exitCode)"
}

data class RunningSetupCommandEvent(val container: Container, val command: SetupCommand, val commandIndex: Int) : TaskEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}', command: $command, command index: $commandIndex)"
}

data class SetupCommandsCompletedEvent(val container: Container) : TaskEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}')"
}

data class StepStartingEvent(val step: TaskStep) : TaskEvent(true) {
    override fun toString() = "${this::class.simpleName}(step: $step)"
}

data class TaskNetworkCreatedEvent(val network: DockerNetwork) : TaskEvent() {
    override fun toString() = "${this::class.simpleName}(network ID: '${network.id}')"
}

object TaskNetworkDeletedEvent : TaskEvent() {
    override fun toString() = this::class.simpleName!!
}

data class TemporaryDirectoryCreatedEvent(val container: Container, val directoryPath: Path) : TaskEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}', directory path: '$directoryPath')"
}

data class TemporaryDirectoryDeletedEvent(val directoryPath: Path) : TaskEvent() {
    override fun toString() = "${this::class.simpleName}(directory path: '$directoryPath')"
}

data class TemporaryFileCreatedEvent(val container: Container, val filePath: Path) : TaskEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}', file path: '$filePath')"
}

data class TemporaryFileDeletedEvent(val filePath: Path) : TaskEvent() {
    override fun toString() = "${this::class.simpleName}(file path: '$filePath')"
}

sealed class TaskFailedEvent : TaskEvent()

data class ExecutionFailedEvent(val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(message: '$message')"
}

data class TaskNetworkCreationFailedEvent(val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(message: '$message')"
}

data class ImageBuildFailedEvent(val container: Container, val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}', message: '$message')"
}

data class ImagePullFailedEvent(val source: PullImage, val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(source: $source, message: '$message')"
}

data class ContainerCreationFailedEvent(val container: Container, val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}', message: '$message')"
}

data class ContainerDidNotBecomeHealthyEvent(val container: Container, val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}', message: '$message')"
}

data class ContainerRunFailedEvent(val container: Container, val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}', message: '$message')"
}

data class ContainerStopFailedEvent(val container: Container, val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}', message: '$message')"
}

data class ContainerRemovalFailedEvent(val container: Container, val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}', message: '$message')"
}

data class TaskNetworkDeletionFailedEvent(val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(message: '$message')"
}

data class TemporaryFileDeletionFailedEvent(val filePath: Path, val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(file path: '$filePath', message: '$message')"
}

data class TemporaryDirectoryDeletionFailedEvent(val directoryPath: Path, val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(directory path: '$directoryPath', message: '$message')"
}

object UserInterruptedExecutionEvent : TaskFailedEvent() {
    override fun toString() = this::class.simpleName!!
}

data class SetupCommandExecutionErrorEvent(val container: Container, val command: SetupCommand, val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}', command: $command, message: '$message')"
}

data class SetupCommandFailedEvent(val container: Container, val command: SetupCommand, val exitCode: Int, val output: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}', command: $command, exit code: $exitCode, output: '$output')"
}

data class CacheInitialisationFailedEvent(val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(message: '$message')"
}
