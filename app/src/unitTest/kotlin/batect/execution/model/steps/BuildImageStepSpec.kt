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

package batect.execution.model.steps

import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object BuildImageStepSpec : Spek({
    describe("a 'build image' step") {
        val step = BuildImageStep("/image-build-dir", mapOf("some_arg" to "some_value"), setOf("some_image_tag", "some_other_image_tag"))

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(step.toString(), equalTo("BuildImageStep(build directory: '/image-build-dir', build args: [some_arg=some_value], image tags: [some_image_tag, some_other_image_tag])"))
            }
        }
    }
})
