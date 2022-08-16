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

package batect.execution

import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ContainerUsesPulledImageExceptionSpec : Spek({
    describe("an 'container uses pulled image' exception") {
        given("a container name") {
            val exception = ContainerUsesPulledImageException("container-1")

            it("provides a user-friendly message") {
                assertThat(exception.message, equalTo("The image built for container 'container-1' was requested to be tagged with --tag-image, but 'container-1' uses a pulled image."))
            }

            it("provides the message as toString()") {
                assertThat(exception.toString(), equalTo(exception.message))
            }
        }
    }
})
