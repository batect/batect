/*
    Copyright 2017-2022 Charles Korn.

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

package batect.execution

import batect.testutils.given
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object UntaggedImagesExceptionSpec : Spek({
    describe("an 'untagged images' exception") {
        given("an empty set of container names") {
            it("throws an appropriate exception") {
                assertThat({ UntaggedImagesException(emptySet()) }, throws<IllegalArgumentException>(withMessage("Cannot create a UntaggedImagesException with an empty set of container names.")))
            }
        }

        given("one container name") {
            val exception = UntaggedImagesException(setOf("container-1"))

            it("provides a user-friendly message") {
                assertThat(exception.message, equalTo("The image for container 'container-1' was requested to be tagged with --tag-image, but this container did not run as part of the task or its prerequisites."))
            }

            it("provides the message as toString()") {
                assertThat(exception.toString(), equalTo(exception.message))
            }
        }

        given("two container names") {
            val exception = UntaggedImagesException(setOf("container-1", "container-2"))

            it("provides a user-friendly message") {
                assertThat(exception.message, equalTo("The images for containers 'container-1' and 'container-2' were requested to be tagged with --tag-image, but these containers did not run as part of the task or its prerequisites."))
            }
        }

        given("three container names") {
            val exception = UntaggedImagesException(setOf("container-1", "container-2", "container-3"))

            it("provides a user-friendly message") {
                assertThat(exception.message, equalTo("The images for containers 'container-1', 'container-2' and 'container-3' were requested to be tagged with --tag-image, but these containers did not run as part of the task or its prerequisites."))
            }
        }

        given("four container names") {
            val exception = UntaggedImagesException(setOf("container-1", "container-2", "container-3", "container-4"))

            it("provides a user-friendly message") {
                assertThat(exception.message, equalTo("The images for containers 'container-1', 'container-2', 'container-3' and 'container-4' were requested to be tagged with --tag-image, but these containers did not run as part of the task or its prerequisites."))
            }
        }
    }
})
