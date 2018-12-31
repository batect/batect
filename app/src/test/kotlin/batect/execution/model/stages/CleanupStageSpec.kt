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

import batect.execution.model.rules.cleanup.CleanupTaskStepRule
import batect.execution.model.rules.cleanup.ManualCleanupSortOrder
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it

object CleanupStageSpec : Spek({
    describe("a cleanup stage") {
        given("it has no rules") {
            val stage = CleanupStage(emptySet())

            it("has no manual cleanup commands") {
                assertThat(stage.manualCleanupCommands, isEmpty)
            }
        }

        given("it has a single rule") {
            given("that rule has no cleanup command") {
                val rule = mock<CleanupTaskStepRule> {
                    on { manualCleanupInstruction } doReturn null as String?
                    on { manualCleanupSortOrder } doThrow UnsupportedOperationException("This rule has no manual cleanup instruction.")
                }

                val stage = CleanupStage(setOf(rule))

                it("has no manual cleanup commands") {
                    assertThat(stage.manualCleanupCommands, isEmpty)
                }
            }

            given("that rule has a cleanup command") {
                val rule = mock<CleanupTaskStepRule> {
                    on { manualCleanupInstruction } doReturn "do-cleanup"
                }

                val stage = CleanupStage(setOf(rule))

                it("has the cleanup command from the rule") {
                    assertThat(stage.manualCleanupCommands, equalTo(listOf("do-cleanup")))
                }
            }
        }

        given("it has multiple rules") {
            val ruleWithNoCommand = mock<CleanupTaskStepRule> {
                on { manualCleanupInstruction } doReturn null as String?
                on { manualCleanupSortOrder } doThrow UnsupportedOperationException("This rule has no manual cleanup instruction.")
            }

            val lateCleanupRule = mock<CleanupTaskStepRule> {
                on { manualCleanupInstruction } doReturn "do-this-second"
                on { manualCleanupSortOrder } doReturn ManualCleanupSortOrder.DeleteTaskNetwork
            }

            val earlyCleanupRule = mock<CleanupTaskStepRule> {
                on { manualCleanupInstruction } doReturn "do-this-first"
                on { manualCleanupSortOrder } doReturn ManualCleanupSortOrder.RemoveContainers
            }

            val stage = CleanupStage(setOf(
                ruleWithNoCommand,
                lateCleanupRule,
                earlyCleanupRule
            ))

            it("has the cleanup commands from the rules that have commands, sorted in execution order") {
                assertThat(stage.manualCleanupCommands, equalTo(listOf("do-this-first", "do-this-second")))
            }
        }
    }
})
