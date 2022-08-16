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

import batect.testutils.equalTo
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CleanupStageSpec : Spek({
    describe("a cleanup stage") {
        val stage = CleanupStage(emptySet(), emptyList())

        it("reports as complete when no steps are still running") {
            assertThat(stage.popNextStep(emptySet(), false), equalTo(StageComplete))
        }

        it("reports as incomplete when a step is still running") {
            assertThat(stage.popNextStep(emptySet(), true), equalTo(NoStepsReady))
        }
    }
})
