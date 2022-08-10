/*
    Copyright 2017-2022 Charles Korn.

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

package batect.execution.model.steps.runners

import batect.config.PullImage
import batect.docker.DockerImage
import batect.docker.ImagePullProgressAggregator
import batect.dockerclient.DockerClient
import batect.dockerclient.DockerClientException
import batect.dockerclient.ImageReference
import batect.execution.model.events.ImagePullFailedEvent
import batect.execution.model.events.ImagePullProgressEvent
import batect.execution.model.events.ImagePulledEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.PullImageStep
import batect.logging.Logger
import batect.primitives.CancellationContext
import batect.primitives.runBlocking

class PullImageStepRunner(
    private val dockerClient: DockerClient,
    private val cancellationContext: CancellationContext,
    private val logger: Logger
) {
    fun run(step: PullImageStep, eventSink: TaskEventSink) {
        try {
            val image = cancellationContext.runBlocking {
                pullImage(step.source, eventSink)
            }

            eventSink.postEvent(ImagePulledEvent(step.source, DockerImage(image.id)))
        } catch (e: DockerClientException) {
            logger.error {
                message("Pulling image failed.")
                exception(e)
                data("imageName", step.source.imageName)
            }

            eventSink.postEvent(ImagePullFailedEvent(step.source, e.message ?: ""))
        }
    }

    private suspend fun pullImage(source: PullImage, eventSink: TaskEventSink): ImageReference {
        if (!source.imagePullPolicy.forciblyPull) {
            val existingImage = dockerClient.getImage(source.imageName)

            if (existingImage != null) {
                return existingImage
            }
        }

        val reporter = ImagePullProgressAggregator()

        return dockerClient.pullImage(source.imageName) { event ->
            val progressUpdate = reporter.processProgressUpdate(event)

            if (progressUpdate != null) {
                eventSink.postEvent(ImagePullProgressEvent(source, progressUpdate))
            }
        }
    }
}
