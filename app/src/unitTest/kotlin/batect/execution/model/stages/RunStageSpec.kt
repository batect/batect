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

package batect.execution.model.stages

import batect.config.Container
import batect.execution.model.events.RunningContainerExitedEvent
import batect.execution.model.events.TaskEvent
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RunStageSpec : Spek({
    describe("a run stage") {
        given("it has no rules") {
            val taskContainer = Container("task-container", imageSourceDoesNotMatter())
            val stage by createForEachTest { RunStage(emptySet(), taskContainer) }

            given("the main container has not exited") {
                val events = emptySet<TaskEvent>()

                given("all steps have finished") {
                    val stepsStillRunning = false

                    it("reports as complete") {
                        assertThat(stage.popNextStep(events, stepsStillRunning), equalTo(StageComplete))
                    }
                }

                given("some steps are still running") {
                    val stepsStillRunning = true

                    it("reports as incomplete") {
                        assertThat(stage.popNextStep(events, stepsStillRunning), equalTo(NoStepsReady))
                    }
                }
            }

            given("the task container has exited") {
                val events = setOf(RunningContainerExitedEvent(taskContainer, 2))

                mapOf(
                    "all steps have finished" to false,
                    "some steps are still running" to true
                ).forEach { description, stepsStillRunning ->
                    given(description) {
                        it("reports as complete") {
                            assertThat(stage.popNextStep(events, stepsStillRunning), equalTo(StageComplete))
                        }
                    }
                }
            }

            given("another container has exited") {
                val otherContainer = Container("other-container", imageSourceDoesNotMatter())
                val events = setOf(RunningContainerExitedEvent(otherContainer, 2))

                given("some steps are still running") {
                    val stepsStillRunning = true

                    it("reports as incomplete") {
                        assertThat(stage.popNextStep(events, stepsStillRunning), equalTo(NoStepsReady))
                    }
                }
            }
        }
    }
})
