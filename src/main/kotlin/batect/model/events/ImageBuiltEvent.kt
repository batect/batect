/*
   Copyright 2017 Charles Korn.

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

package batect.model.events

import batect.model.steps.CreateContainerStep
import batect.config.Container
import batect.docker.DockerImage

data class ImageBuiltEvent(val container: Container, val image: DockerImage) : TaskEvent() {
    override fun apply(context: TaskEventContext) {
        if (context.isAborting) {
            return
        }

        val networkCreationEvent = context.getSinglePastEventOfType<TaskNetworkCreatedEvent>()

        if (networkCreationEvent != null) {
            val command = context.commandForContainer(container)
            context.queueStep(CreateContainerStep(container, command, image, networkCreationEvent.network))
        }
    }

    override fun toString() = "${this::class.simpleName}(container: '${container.name}', image: '${image.id}')"
}
