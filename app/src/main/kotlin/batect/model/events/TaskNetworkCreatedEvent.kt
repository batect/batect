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
import batect.model.steps.DeleteTaskNetworkStep

data class TaskNetworkCreatedEvent(val network: DockerNetwork) : TaskEvent() {
    override fun apply(context: TaskEventContext, logger: Logger) {
        if (context.isAborting) {
            logger.info {
                message("Task is aborting, deleting network that was just created.")
                data("event", this@TaskNetworkCreatedEvent.toString())
            }

            context.queueStep(DeleteTaskNetworkStep(network))
            return
        }

        context.getPastEventsOfType<ImageBuiltEvent>()
            .forEach { createContainer(it.container, it.image, context) }

        context.getPastEventsOfType<ImagePulledEvent>()
            .forEach { createContainersForImage(it.image, context) }
    }

    private fun createContainersForImage(image: DockerImage, context: TaskEventContext) {
        context.allTaskContainers
            .filter { it.imageSource == PullImage(image.id) }
            .forEach { createContainer(it, image, context) }
    }

    private fun createContainer(container: Container, image: DockerImage, context: TaskEventContext) {
        context.queueStep(CreateContainerStep(container, image, network, context))
    }

    override fun toString() = "${this::class.simpleName}(network ID: '${network.id}')"
}
