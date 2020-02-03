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

package batect.execution.model.steps.runners

import batect.docker.ImagePullFailedException
import batect.docker.client.DockerImagesClient
import batect.execution.CancellationContext
import batect.execution.model.events.ImagePullFailedEvent
import batect.execution.model.events.ImagePullProgressEvent
import batect.execution.model.events.ImagePulledEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.PullImageStep

class PullImageStepRunner(
    private val imagesClient: DockerImagesClient,
    private val cancellationContext: CancellationContext
) {
    fun run(step: PullImageStep, eventSink: TaskEventSink) {
        try {
            val image = imagesClient.pull(step.source.imageName, cancellationContext) { progressUpdate ->
                eventSink.postEvent(ImagePullProgressEvent(step.source, progressUpdate))
            }

            eventSink.postEvent(ImagePulledEvent(step.source, image))
        } catch (e: ImagePullFailedException) {
            eventSink.postEvent(ImagePullFailedEvent(step.source, e.message ?: ""))
        }
    }
}
