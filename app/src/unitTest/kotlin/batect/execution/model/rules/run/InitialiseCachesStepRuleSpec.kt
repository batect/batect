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

package batect.execution.model.rules.run

import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.InitialiseCachesStep
import batect.testutils.equalTo
import batect.testutils.logRepresentationOf
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object InitialiseCachesStepRuleSpec : Spek({
    describe("an initialise caches step rule") {
        val rule = InitialiseCachesStepRule

        on("evaluating the rule") {
            val result = rule.evaluate(emptySet())

            it("returns a 'initialise caches step' step") {
                assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(InitialiseCachesStep)))
            }
        }

        on("attaching it to a log message") {
            it("returns a machine-readable representation of itself") {
                assertThat(logRepresentationOf(rule), equivalentTo("""
                    |{
                    |   "type": "${rule::class.qualifiedName}"
                    |}
                """.trimMargin()))
            }
        }
    }
})
