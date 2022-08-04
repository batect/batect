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

import batect.config.Container
import batect.docker.DockerContainer
import batect.dockerclient.ContainerReference
import batect.dockerclient.DockerClient
import batect.dockerclient.DockerClientException
import batect.dockerclient.ReadyNotification
import batect.dockerclient.io.SinkTextOutput
import batect.dockerclient.io.SourceTextInput
import batect.execution.model.events.ContainerRunFailedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.RunningContainerExitedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.RunContainerStep
import batect.primitives.CancellationContext
import batect.testutils.beforeEachTestSuspend
import batect.testutils.createForEachTest
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.itSuspend
import batect.testutils.on
import batect.ui.containerio.ContainerIOStreamingOptions
import okio.Sink
import okio.Source
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RunContainerStepRunnerSpec : Spek({
    describe("running a 'run container' step") {
        val container = Container("some-container", imageSourceDoesNotMatter())
        val dockerContainer = DockerContainer("some-id", "some-name")
        val step = RunContainerStep(container, dockerContainer)

        val stdout = SinkTextOutput(mock<Sink>())
        val stdin = SourceTextInput(mock<Source>())

        val dockerClient by createForEachTest { mock<DockerClient>() }

        val ioStreamingOptions by createForEachTest {
            mock<ContainerIOStreamingOptions>() {
                on { stdoutForContainer(container) } doReturn stdout
                on { stdinForContainer(container) } doReturn stdin
            }
        }

        val cancellationContext by createForEachTest { CancellationContext() }
        val eventSink by createForEachTest { mock<TaskEventSink>() }
        val runner by createForEachTest { RunContainerStepRunner(dockerClient, ioStreamingOptions, cancellationContext) }

        on("when running the container succeeds") {
            beforeEachTestSuspend {
                whenever(dockerClient.run(any(), any(), any(), any(), any())).doAnswer { invocation ->
                    val startedNotification = invocation.getArgument<ReadyNotification>(4)

                    startedNotification.markAsReady()

                    123
                }

                runner.run(step, eventSink)
            }

            itSuspend("runs the container with the stdin and stdout provided by the I/O streaming options") {
                verify(dockerClient).run(eq(ContainerReference("some-id")), eq(stdout), eq(stdout), eq(stdin), any())
            }

            it("emits a 'container started' event") {
                verify(eventSink).postEvent(ContainerStartedEvent(container))
            }

            it("emits a 'running container exited' event") {
                verify(eventSink).postEvent(RunningContainerExitedEvent(container, 123))
            }
        }

        on("when running the container fails") {
            beforeEachTestSuspend {
                whenever(dockerClient.run(any(), any(), any(), any(), any())).doThrow(DockerClientException("Something went wrong"))

                runner.run(step, eventSink)
            }

            it("emits a 'container run failed' event") {
                verify(eventSink).postEvent(ContainerRunFailedEvent(container, "Something went wrong"))
            }
        }
    }
})
