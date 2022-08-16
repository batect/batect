/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.execution.model.steps.runners

import batect.config.ImagePullPolicy
import batect.config.PullImage
import batect.docker.AggregatedImagePullProgress
import batect.docker.DownloadOperation
import batect.dockerclient.DockerClient
import batect.dockerclient.ImagePullFailedException
import batect.dockerclient.ImagePullProgressDetail
import batect.dockerclient.ImagePullProgressReceiver
import batect.dockerclient.ImagePullProgressUpdate
import batect.dockerclient.ImageReference
import batect.dockerclient.ImageRetrievalFailedException
import batect.execution.model.events.ImagePullFailedEvent
import batect.execution.model.events.ImagePullProgressEvent
import batect.execution.model.events.ImagePulledEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.PullImageStep
import batect.primitives.CancellationContext
import batect.testutils.beforeEachTestSuspend
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.given
import batect.testutils.itSuspend
import batect.testutils.on
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PullImageStepRunnerSpec : Spek({
    describe("running a 'pull image' step") {
        val dockerClient by createForEachTest { mock<DockerClient>() }
        val cancellationContext by createForEachTest { mock<CancellationContext>() }
        val eventSink by createForEachTest { mock<TaskEventSink>() }
        val logger by createLoggerForEachTest()

        val runner by createForEachTest { PullImageStepRunner(dockerClient, cancellationContext, logger) }

        on("when pulling the image succeeds") {
            val image = ImageReference("some-image")
            val update1 = AggregatedImagePullProgress(DownloadOperation.Downloading, 10, 20)
            val update2 = AggregatedImagePullProgress(DownloadOperation.Downloading, 15, 20)

            beforeEachTestSuspend {
                whenever(dockerClient.pullImage(eq("some-image"), any())).then { invocation ->
                    val onStatusUpdate = invocation.getArgument<ImagePullProgressReceiver>(1)

                    onStatusUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(10, 20), "abc123"))
                    onStatusUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(15, 20), "abc123"))

                    image
                }
            }

            given("the image pull policy is set to 'if not present'") {
                val source = PullImage("some-image", ImagePullPolicy.IfNotPresent)
                val step = PullImageStep(source)

                given("the image is not already present") {
                    beforeEachTestSuspend {
                        whenever(dockerClient.getImage(any())).doReturn(null)

                        runner.run(step, eventSink)
                    }

                    it("emits a 'image pulled' event") {
                        verify(eventSink).postEvent(ImagePulledEvent(source, image))
                    }

                    it("emits a 'image pull progress' event for each update received from Docker") {
                        verify(eventSink).postEvent(ImagePullProgressEvent(source, update1))
                        verify(eventSink).postEvent(ImagePullProgressEvent(source, update2))
                    }
                }

                given("the image is already present") {
                    beforeEachTestSuspend {
                        whenever(dockerClient.getImage(any())).doReturn(image)

                        runner.run(step, eventSink)
                    }

                    it("emits a 'image pulled' event") {
                        verify(eventSink).postEvent(ImagePulledEvent(source, image))
                    }

                    itSuspend("does not pull the image again") {
                        verify(dockerClient, never()).pullImage(any(), any())
                    }
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

                itSuspend("does not check if the image already exists") {
                    verify(dockerClient, never()).getImage(any())
                }
            }
        }

        on("when pulling the image fails") {
            val source = PullImage("some-image", ImagePullPolicy.Always)
            val step = PullImageStep(source)

            beforeEachTestSuspend {
                whenever(dockerClient.pullImage(eq("some-image"), any())).thenThrow(ImagePullFailedException("Pulling image 'some-image' failed. Output from Docker was: Something went wrong."))

                runner.run(step, eventSink)
            }

            it("emits a 'image pull failed' event") {
                verify(eventSink).postEvent(ImagePullFailedEvent(source, "Pulling image 'some-image' failed. Output from Docker was: Something went wrong."))
            }
        }

        on("when checking if the image has already been pulled fails") {
            val source = PullImage("some-image")
            val step = PullImageStep(source)

            beforeEachTestSuspend {
                whenever(dockerClient.getImage(eq("some-image"))).thenThrow(ImageRetrievalFailedException("Something went wrong"))

                runner.run(step, eventSink)
            }

            it("emits a 'image pull failed' event") {
                verify(eventSink).postEvent(ImagePullFailedEvent(source, "Something went wrong"))
            }
        }
    }
})
