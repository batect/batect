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

import batect.config.Container
import batect.docker.DockerContainer
import batect.docker.api.ContainerStopFailedException
import batect.docker.client.ContainersClient
import batect.execution.model.events.ContainerStopFailedEvent
import batect.execution.model.events.ContainerStoppedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.StopContainerStep
import batect.testutils.createForEachTest
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object StopContainerStepRunnerSpec : Spek({
    describe("running a 'stop container' step") {
        val container = Container("some-container", imageSourceDoesNotMatter())
        val dockerContainer = DockerContainer("some-id")
        val step = StopContainerStep(container, dockerContainer)

        val containersClient by createForEachTest { mock<ContainersClient>() }
        val eventSink by createForEachTest { mock<TaskEventSink>() }
        val runner by createForEachTest { StopContainerStepRunner(containersClient) }

        on("when stopping the container succeeds") {
            beforeEachTest { runner.run(step, eventSink) }

            it("stops the container") {
                verify(containersClient).stop(dockerContainer)
            }

            it("emits a 'container stopped' event") {
                verify(eventSink).postEvent(ContainerStoppedEvent(container))
            }
        }

        on("when stopping the container fails") {
            beforeEachTest {
                whenever(containersClient.stop(dockerContainer)).thenThrow(ContainerStopFailedException("some-id", "Something went wrong"))

                runner.run(step, eventSink)
            }

            it("emits a 'container stop failed' event") {
                verify(eventSink).postEvent(ContainerStopFailedEvent(container, "Something went wrong"))
            }
        }
    }
})
