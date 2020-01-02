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
import batect.execution.model.steps.CreateTaskNetworkStep
import batect.testutils.equalTo
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CreateTaskNetworkStepRuleSpec : Spek({
    describe("a create task network step rule") {
        val rule = CreateTaskNetworkStepRule

        on("evaluating the rule") {
            val result = rule.evaluate(emptySet())

            it("returns a 'create task network' step") {
                assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(CreateTaskNetworkStep)))
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(rule.toString(), equalTo("CreateTaskNetworkStepRule"))
            }
        }
    }
})
