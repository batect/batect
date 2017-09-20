/*
   Copyright 2017 Charles Korn.

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

package batect.model.events

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import batect.config.Container
import batect.testutils.imageSourceDoesNotMatter
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerRemovalFailedEventSpec : Spek({
    describe("a 'container removal failed' event") {
        val container = Container("some-container", imageSourceDoesNotMatter())
        val event = ContainerRemovalFailedEvent(container, "Something went wrong")

        on("getting the message to display") {
            it("returns a description of the failure") {
                assertThat(event.messageToDisplay, equalTo("the container 'some-container' couldn't be removed: Something went wrong"))
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(event.toString(), equalTo("ContainerRemovalFailedEvent(container: 'some-container', message: 'Something went wrong')"))
            }
        }
    }
})
