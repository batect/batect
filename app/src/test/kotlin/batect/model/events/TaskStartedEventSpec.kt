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

import batect.config.BuildImage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import batect.model.steps.BuildImageStep
import batect.model.steps.CreateTaskNetworkStep
import batect.config.Container
import batect.config.PullImage
import batect.model.steps.PullImageStep
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskStartedEventSpec : Spek({
    describe("a 'task started' event") {
        val event = TaskStartedEvent

        on("being applied") {
            val container1 = Container("container-1", BuildImage("/container-1-build-dir"))
            val container2 = Container("container-2", BuildImage("/container-2-build-dir"))
            val container3 = Container("container-3", PullImage("common-image"))
            val container4 = Container("container-4", PullImage("common-image"))

            val context = mock<TaskEventContext> {
                on { allTaskContainers } doReturn setOf(container1, container2, container3, container4)
                on { projectName } doReturn "the-project"
            }

            event.apply(context)

            it("queues 'build image' steps for each container in the task dependency graph that requires a image to be built") {
                verify(context).queueStep(BuildImageStep("the-project", container1))
                verify(context).queueStep(BuildImageStep("the-project", container2))
            }

            it("does not queue 'build image' steps for containers in the task dependency graph that use an existing image") {
                verify(context, never()).queueStep(BuildImageStep("the-project", container3))
                verify(context, never()).queueStep(BuildImageStep("the-project", container4))
            }

            it("queues 'pull image' steps for each unique image required by containers in the task dependency graph") {
                verify(context, times(1)).queueStep(PullImageStep("common-image"))
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
