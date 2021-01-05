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

package batect.execution.model.rules.run

import batect.config.Container
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.BuildImageStep
import batect.testutils.equalTo
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.logRepresentationOf
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object BuildImageStepRuleSpec : Spek({
    describe("a build image step rule") {
        val source = Container("the-container", imageSourceDoesNotMatter())
        val rule = BuildImageStepRule(source)

        on("evaluating the rule") {
            val result = rule.evaluate(emptySet())

            it("returns a 'build image' step") {
                assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(BuildImageStep(source))))
            }
        }

        on("attaching it to a log message") {
            it("returns a machine-readable representation of itself") {
                assertThat(
                    logRepresentationOf(rule),
                    equivalentTo(
                        """
                        |{
                        |   "type": "${rule::class.qualifiedName}",
                        |   "container": "the-container"
                        |}
                        """.trimMargin()
                    )
                )
            }
        }
    }
})
