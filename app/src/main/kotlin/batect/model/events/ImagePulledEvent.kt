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

package batect.model.events

import batect.config.Container
import batect.config.PullImage
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.logging.Logger
import batect.model.steps.CreateContainerStep

data class ImagePulledEvent(val image: DockerImage) : TaskEvent() {
    override fun apply(context: TaskEventContext, logger: Logger) {
        if (context.isAborting) {
            logger.info {
                message("Task is aborting, not queuing any further work.")
                data("event", this@ImagePulledEvent.toString())
            }

            return
        }

        val networkCreationEvent = context.getSinglePastEventOfType<TaskNetworkCreatedEvent>()

        if (networkCreationEvent != null) {
            context.allTaskContainers
                .filter { it.imageSource == PullImage(image.id) }
                .forEach { createContainer(it, networkCreationEvent.network, context) }
        } else {
            logger.info {
                message("Task network hasn't been created yet, not queuing create container step.")
                data("event", this@ImagePulledEvent.toString())
            }
        }
    }

    private fun createContainer(container: Container, network: DockerNetwork, context: TaskEventContext) {
        context.queueStep(CreateContainerStep(container, image, network, context))
    }

    override fun toString() = "${this::class.simpleName}(image: '${image.id}')"
}
