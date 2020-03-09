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

import batect.config.Container
import batect.docker.client.DockerContainerType
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.InitialiseCachesStep
import batect.testutils.equalTo
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object InitialiseCachesStepRuleSpec : Spek({
    describe("an initialise caches step rule") {
        val container1 = Container("container-1", imageSourceDoesNotMatter())
        val container2 = Container("container-2", imageSourceDoesNotMatter())
        val rule = InitialiseCachesStepRule(DockerContainerType.Windows, setOf(container1, container2))

        on("evaluating the rule") {
            val result = rule.evaluate(emptySet())

            it("returns a 'create task network' step") {
                assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(InitialiseCachesStep(DockerContainerType.Windows, setOf(container1, container2)))))
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(rule.toString(), equalTo("InitialiseCachesStepRule(container type: Windows, all containers in task: ['container-1', 'container-2'])"))
            }
        }
    }
})
