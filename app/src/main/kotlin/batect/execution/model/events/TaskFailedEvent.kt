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

package batect.execution.model.events

import batect.config.BuildImage
import batect.config.Container
import batect.config.PullImage
import java.nio.file.Path

sealed class TaskFailedEvent : TaskEvent()

data class ExecutionFailedEvent(val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(message: '$message')"
}

data class TaskNetworkCreationFailedEvent(val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(message: '$message')"
}

data class ImageBuildFailedEvent(val source: BuildImage, val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(source: $source, message: '$message')"
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
