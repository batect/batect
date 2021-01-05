/*
   Copyright 2017-2021 Charles Korn.

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

import batect.config.ImagePullPolicy
import batect.config.PullImage
import batect.docker.DockerImage
import batect.docker.DownloadOperation
import batect.docker.ImagePullFailedException
import batect.docker.client.ImagesClient
import batect.docker.pull.ImagePullProgress
import batect.execution.model.events.ImagePullFailedEvent
import batect.execution.model.events.ImagePullProgressEvent
import batect.execution.model.events.ImagePulledEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.PullImageStep
import batect.primitives.CancellationContext
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.on
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PullImageStepRunnerSpec : Spek({
    describe("running a 'pull image' step") {
        val imagesClient by createForEachTest { mock<ImagesClient>() }
        val cancellationContext by createForEachTest { mock<CancellationContext>() }
        val eventSink by createForEachTest { mock<TaskEventSink>() }

        val runner by createForEachTest { PullImageStepRunner(imagesClient, cancellationContext) }

        on("when pulling the image succeeds") {
            val image = DockerImage("some-image")
            val update1 = ImagePullProgress(DownloadOperation.Downloading, 10, 20)
            val update2 = ImagePullProgress(DownloadOperation.Downloading, 15, 20)

            beforeEachTest {
                whenever(imagesClient.pull(eq("some-image"), any(), eq(cancellationContext), any())).then { invocation ->
                    @Suppress("UNCHECKED_CAST")
                    val onStatusUpdate = invocation.arguments[3] as (ImagePullProgress) -> Unit

                    onStatusUpdate(update1)
                    onStatusUpdate(update2)

                    image
                }
            }

            given("the image pull policy is set to 'if not present'") {
                val source = PullImage("some-image", ImagePullPolicy.IfNotPresent)
                val step = PullImageStep(source)

                beforeEachTest {
                    runner.run(step, eventSink)
                }

                it("emits a 'image pulled' event") {
                    verify(eventSink).postEvent(ImagePulledEvent(source, image))
                }

                it("emits a 'image pull progress' event for each update received from Docker") {
                    verify(eventSink).postEvent(ImagePullProgressEvent(source, update1))
                    verify(eventSink).postEvent(ImagePullProgressEvent(source, update2))
                }

                it("calls the Docker API with forcibly pulling the image disabled") {
                    verify(imagesClient).pull(any(), eq(false), any(), any())
                }
            }

            given("the image pull policy is set to 'always'") {
                val source = PullImage("some-image", ImagePullPolicy.Always)
                val step = PullImageStep(source)

                beforeEachTest {
                    runner.run(step, eventSink)
                }

                it("emits a 'image pulled' event") {
                    verify(eventSink).postEvent(ImagePulledEvent(source, image))
                }

                it("emits a 'image pull progress' event for each update received from Docker") {
                    verify(eventSink).postEvent(ImagePullProgressEvent(source, update1))
                    verify(eventSink).postEvent(ImagePullProgressEvent(source, update2))
                }

                it("calls the Docker API with forcibly pulling the image enabled") {
                    verify(imagesClient).pull(any(), eq(true), any(), any())
                }
            }
        }

        on("when building the image fails") {
            val source = PullImage("some-image")
            val step = PullImageStep(source)

            beforeEachTest {
                whenever(imagesClient.pull(eq("some-image"), any(), eq(cancellationContext), any())).thenThrow(ImagePullFailedException("Pulling image 'some-image' failed. Output from Docker was: Something went wrong."))

                runner.run(step, eventSink)
            }

            it("emits a 'image pull failed' event") {
                verify(eventSink).postEvent(ImagePullFailedEvent(source, "Pulling image 'some-image' failed. Output from Docker was: Something went wrong."))
            }
        }
    }
})
