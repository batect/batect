/*
   Copyright 2017 Charles Korn.

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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import batect.model.steps.BuildImageStep
import batect.model.steps.CreateTaskNetworkStep
import batect.config.Container
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskStartedEventSpec : Spek({
    describe("a 'task started' event") {
        val event = TaskStartedEvent

        on("being applied") {
            val container1 = Container("container-1", "/container-1-build-dir")
            val container2 = Container("container-2", "/container-2-build-dir")

            val context = mock<TaskEventContext> {
                on { allTaskContainers } doReturn setOf(container1, container2)
                on { projectName } doReturn "the-project"
            }

            event.apply(context)

            it("queues build image steps for each container in the task dependency graph") {
                verify(context).queueStep(BuildImageStep("the-project", container1))
                verify(context).queueStep(BuildImageStep("the-project", container2))
            }

            it("queues a step to create the task network") {
                verify(context).queueStep(CreateTaskNetworkStep)
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(event.toString(), equalTo("TaskStartedEvent"))
            }
        }
    }
})
