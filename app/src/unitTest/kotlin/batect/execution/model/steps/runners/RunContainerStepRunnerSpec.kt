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

import batect.config.Container
import batect.docker.DockerContainer
import batect.docker.DockerContainerRunResult
import batect.docker.DockerException
import batect.docker.client.DockerContainersClient
import batect.execution.CancellationContext
import batect.execution.model.events.ContainerRunFailedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.RunningContainerExitedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.RunContainerStep
import batect.os.Dimensions
import batect.testutils.createForEachTest
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.ui.containerio.ContainerIOStreamingOptions
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import okio.Sink
import okio.Source
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RunContainerStepRunnerSpec : Spek({
    describe("running a 'run container' step") {
        val container = Container("some-container", imageSourceDoesNotMatter())
        val dockerContainer = DockerContainer("some-id")
        val step = RunContainerStep(container, dockerContainer)

        val stdout = mock<Sink>()
        val stdin = mock<Source>()
        val useTTY = true
        val frameDimensions = Dimensions(20, 30)

        val containersClient by createForEachTest { mock<DockerContainersClient>() }

        val ioStreamingOptions by createForEachTest {
            mock<ContainerIOStreamingOptions>() {
                on { stdoutForContainer(container) } doReturn stdout
                on { stdinForContainer(container) } doReturn stdin
                on { this.frameDimensions } doReturn frameDimensions
                on { useTTYForContainer(container) } doReturn useTTY
            }
        }

        val cancellationContext by createForEachTest { mock<CancellationContext>() }
        val eventSink by createForEachTest { mock<TaskEventSink>() }
        val runner by createForEachTest { RunContainerStepRunner(containersClient, ioStreamingOptions, cancellationContext) }

        on("when running the container succeeds") {
            beforeEachTest {
                whenever(containersClient.run(any(), any(), any(), any(), any(), any(), any())).doAnswer { invocation ->
                    @Suppress("UNCHECKED_CAST")
                    val onStartedHandler = invocation.arguments[6] as () -> Unit

                    onStartedHandler()

                    DockerContainerRunResult(123)
                }

                runner.run(step, eventSink)
            }

            it("runs the container with the stdin and stdout provided by the I/O streaming options") {
                verify(containersClient).run(eq(dockerContainer), eq(stdout), eq(stdin), eq(useTTY), eq(cancellationContext), eq(frameDimensions), any())
            }

            it("emits a 'container started' event") {
                verify(eventSink).postEvent(ContainerStartedEvent(container))
            }

            it("emits a 'running container exited' event") {
                verify(eventSink).postEvent(RunningContainerExitedEvent(container, 123))
            }
        }

        on("when running the container fails") {
            beforeEachTest {
                whenever(containersClient.run(any(), any(), any(), any(), any(), any(), any())).doThrow(DockerException("Something went wrong"))

                runner.run(step, eventSink)
            }

            it("emits a 'container run failed' event") {
                verify(eventSink).postEvent(ContainerRunFailedEvent(container, "Something went wrong"))
            }
        }
    }
})
