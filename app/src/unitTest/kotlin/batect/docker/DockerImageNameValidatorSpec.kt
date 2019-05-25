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

package batect.docker

import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DockerImageNameValidatorSpec : Spek({
    describe("a Docker image name validator") {
        val validator = DockerImageNameValidator()

        listOf(
            "a",
            "aa",
            "1",
            "a1",
            "a.b",
            "a_b",
            "a__b",
            "a-b",
            "a--b",
            "a---b"
        ).forEach { name ->
            given("the name '$name'") {
                it("reports that '$name' is a valid image name") {
                    assertThat(validator.isValidImageName(name), equalTo(true))
                }
            }
        }

        listOf(
            "",
            "A",
            ".a",
            "a.",
            "a..a",
            "_a",
            "a_",
            "a___a",
            "-a",
            "a-",
            "a#b",
            "a!b"
        ).forEach { name ->
            given("the name '$name'") {
                it("reports that '$name' is not a valid image name") {
                    assertThat(validator.isValidImageName(name), equalTo(false))
                }
            }
        }
    }
})
