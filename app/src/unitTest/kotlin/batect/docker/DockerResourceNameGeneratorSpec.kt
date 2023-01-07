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

package batect.docker

import batect.config.Container
import batect.config.TaskSpecialisedConfiguration
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.withMessage
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.matches
import com.natpryce.hamkrest.startsWith
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DockerResourceNameGeneratorSpec : Spek({
    describe("a Docker resource name generator") {
        val config = TaskSpecialisedConfiguration("my_project")
        val generator = DockerResourceNameGenerator(config)
        val container1 = Container("container-1", imageSourceDoesNotMatter())
        val container2 = Container("container-2", imageSourceDoesNotMatter())

        val nameForContainer1 = generator.generateNameFor(container1)
        val nameForContainer2 = generator.generateNameFor(container2)
        val nameForOtherContainer = generator.generateNameFor("batect-cache-init")

        it("returns the project and container's name with a random suffix") {
            assertThat(nameForContainer1, matches("""^my_project-container-1-[a-z0-9]{6}$""".toRegex()))
        }

        it("uses the same suffix for other containers named by the same instance") {
            assertThat(nameForContainer1.substringAfter("my_project-container-1-"), equalTo(nameForContainer2.substringAfter("my_project-container-2-")))
            assertThat(nameForContainer1.substringAfter("my_project-container-1-"), equalTo(nameForOtherContainer.substringAfter("my_project-batect-cache-init-")))
        }

        describe("when a limit is provided") {
            it("returns the full name if it would be less than the limit") {
                assertThat(generator.generateNameFor("batect-cache-init", limitTo = 63), startsWith("my_project-batect-cache-init-"))
            }

            it("truncates the project name if the name would be longer than the limit") {
                assertThat(
                    generator.generateNameFor("batect-cache-init-with-a-really-really-long-name", limitTo = 63),
                    startsWith("my_proj-batect-cache-init-with-a-really-really-long-name-") and has(String::length, equalTo(63)),
                )
            }

            it("throws an exception if the name provided would cause the generated name to be too long") {
                assertThat(
                    { generator.generateNameFor("batect-cache-init-with-a-really-really-incredibly-long-name", limitTo = 63) },
                    throws<IllegalArgumentException>(withMessage("Can't generate name for 'batect-cache-init-with-a-really-really-incredibly-long-name' because it would exceed the desired limit of 63 characters.")),
                )
            }
        }
    }
})
