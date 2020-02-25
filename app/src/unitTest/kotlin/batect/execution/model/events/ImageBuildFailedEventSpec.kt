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

package batect.execution.model.events

import batect.config.Container
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ImageBuildFailedEventSpec : Spek({
    describe("a 'image build failed' event") {
        val container = Container("the-container", imageSourceDoesNotMatter())
        val event = ImageBuildFailedEvent(container, "Something went wrong")

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(event.toString(), equalTo("ImageBuildFailedEvent(container: 'the-container', message: 'Something went wrong')"))
            }
        }
    }
})
