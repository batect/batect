/*
   Copyright 2017-2019 Charles Korn.

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

package batect.execution.model.stages

import batect.execution.model.events.TaskNetworkDeletedEvent
import batect.execution.model.rules.TaskStepRule
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.TaskStep
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.or
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object StageSpec : Spek({
    describe("a stage") {
        fun createStageWithRules(rules: Set<TaskStepRule>): Stage = RunStage(rules)

        given("the stage has no rules") {
            val stage = createStageWithRules(emptySet())

            on("getting the next step") {
                val result = stage.popNextStep(emptySet())

                it("returns that there are no steps remaining") {
                    assertThat(result, equalTo(NoStepsRemaining))
                }
            }
        }

        given("the stage has one rule") {
            val rule by createForEachTest { mock<TaskStepRule>() }
            val stage by createForEachTest { createStageWithRules(setOf(rule)) }
            val events = setOf(TaskNetworkDeletedEvent)

            given("the rule is not ready") {
                beforeEachTest { whenever(rule.evaluate(events)).doReturn(TaskStepRuleEvaluationResult.NotReady) }

                on("getting the next step") {
                    val result by runForEachTest { stage.popNextStep(events) }

                    it("returns that there are no steps ready") {
                        assertThat(result, equalTo(NoStepsReady))
                    }
                }
            }

            given("the rule is ready") {
                val step = mock<TaskStep>()
                beforeEachTest { whenever(rule.evaluate(events)).doReturn(TaskStepRuleEvaluationResult.Ready(step)) }

                on("getting the next step") {
                    val result by runForEachTest { stage.popNextStep(events) }

                    it("returns that there is a step ready") {
                        assertThat(result, equalTo(StepReady(step)))
                    }
                }

                on("getting the next two steps") {
                    val firstResult by runForEachTest { stage.popNextStep(events) }
                    val secondResult by runForEachTest { stage.popNextStep(events) }

                    it("returns that there is a step ready for the first request") {
                        assertThat(firstResult, equalTo(StepReady(step)))
                    }

                    it("returns that there are no remaining steps for the second request") {
                        assertThat(secondResult, equalTo(NoStepsRemaining))
                    }
                }
            }
        }

        given("the stage has two rules") {
            val rule1 by createForEachTest { mock<TaskStepRule>() }
            val rule2 by createForEachTest { mock<TaskStepRule>() }
            val stage by createForEachTest { createStageWithRules(setOf(rule1, rule2)) }
            val events = setOf(TaskNetworkDeletedEvent)

            given("neither rule is ready") {
                beforeEachTest {
                    whenever(rule1.evaluate(events)).doReturn(TaskStepRuleEvaluationResult.NotReady)
                    whenever(rule2.evaluate(events)).doReturn(TaskStepRuleEvaluationResult.NotReady)
                }

                on("getting the next step") {
                    val result by runForEachTest { stage.popNextStep(events) }

                    it("returns that there are no steps ready") {
                        assertThat(result, equalTo(NoStepsReady))
                    }
                }
            }

            given("one of the rules is ready") {
                val step = mock<TaskStep>()

                beforeEachTest {
                    whenever(rule1.evaluate(events)).doReturn(TaskStepRuleEvaluationResult.Ready(step))
                    whenever(rule2.evaluate(events)).doReturn(TaskStepRuleEvaluationResult.NotReady)
                }

                on("getting the next two steps") {
                    val firstResult by runForEachTest { stage.popNextStep(events) }
                    val secondResult by runForEachTest { stage.popNextStep(events) }

                    it("returns that there is a step ready for the first request") {
                        assertThat(firstResult, equalTo(StepReady(step)))
                    }

                    it("returns that there are no steps ready for the second request") {
                        assertThat(secondResult, equalTo(NoStepsReady))
                    }
                }
            }

            given("both of the rules are ready") {
                val step1 = mock<TaskStep>()
                val step2 = mock<TaskStep>()

                beforeEachTest {
                    whenever(rule1.evaluate(events)).doReturn(TaskStepRuleEvaluationResult.Ready(step1))
                    whenever(rule2.evaluate(events)).doReturn(TaskStepRuleEvaluationResult.Ready(step2))
                }

                on("getting the next three steps") {
                    val firstResult by runForEachTest { stage.popNextStep(events) }
                    val secondResult by runForEachTest { stage.popNextStep(events) }
                    val thirdResult by runForEachTest { stage.popNextStep(events) }

                    it("returns that there is a step ready for the first request") {
                        assertThat(firstResult, equalTo<NextStepResult, NextStepResult>(StepReady(step1)) or equalTo(StepReady(step2)))
                    }

                    it("returns that there is a step ready for the second request") {
                        assertThat(secondResult, equalTo<NextStepResult, NextStepResult>(StepReady(step1)) or equalTo(StepReady(step2)))
                    }

                    it("does not return the same step for both the first and second requests") {
                        assertThat(firstResult, !equalTo(secondResult))
                    }

                    it("returns that there are no remaining steps for the third request") {
                        assertThat(thirdResult, equalTo(NoStepsRemaining))
                    }
                }
            }
        }
    }
})
