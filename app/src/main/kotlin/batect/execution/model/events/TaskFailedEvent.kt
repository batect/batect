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

package batect.execution.model.events

import batect.config.Container
import java.nio.file.Path

sealed class TaskFailedEvent : TaskEvent()

data class TaskNetworkCreationFailedEvent(val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(message: '$message')"
}

data class ImageBuildFailedEvent(val container: Container, val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}', message: '$message')"
}

data class ImagePullFailedEvent(val imageName: String, val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(message: '$message')"
}

data class ContainerCreationFailedEvent(val container: Container, val message: String) : TaskFailedEvent() {
    override fun toString() = "${this::class.simpleName}(container: '${container.name}', message: '$message')"
}

data class ContainerStartFailedEvent(val container: Container, val message: String) : TaskFailedEvent() {
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
