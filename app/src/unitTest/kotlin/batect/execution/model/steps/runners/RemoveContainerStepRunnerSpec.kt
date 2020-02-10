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
import batect.docker.api.ContainerRemovalFailedException
import batect.docker.client.DockerContainersClient
import batect.execution.model.events.ContainerRemovalFailedEvent
import batect.execution.model.events.ContainerRemovedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.RemoveContainerStep
import batect.testutils.createForEachTest
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RemoveContainerStepRunnerSpec : Spek({
    describe("running a 'remove container' step") {
        val container = Container("some-container", imageSourceDoesNotMatter())
        val dockerContainer = DockerContainer("some-id")
        val step = RemoveContainerStep(container, dockerContainer)

        val containersClient by createForEachTest { mock<DockerContainersClient>() }
        val eventSink by createForEachTest { mock<TaskEventSink>() }
        val runner by createForEachTest { RemoveContainerStepRunner(containersClient) }

        on("when removing the container succeeds") {
            beforeEachTest { runner.run(step, eventSink) }

            it("removes the container") {
                verify(containersClient).remove(dockerContainer)
            }

            it("emits a 'container removed' event") {
                verify(eventSink).postEvent(ContainerRemovedEvent(container))
            }
        }

        on("when removing the container fails") {
            beforeEachTest {
                whenever(containersClient.remove(dockerContainer)).thenThrow(ContainerRemovalFailedException("some-id", "Something went wrong"))

                runner.run(step, eventSink)
            }

            it("emits a 'container removal failed' event") {
                verify(eventSink).postEvent(ContainerRemovalFailedEvent(container, "Something went wrong"))
            }
        }
    }
})
