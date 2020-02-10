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

package batect.docker

import batect.config.Container
import batect.testutils.imageSourceDoesNotMatter
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.matches
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DockerContainerNameGeneratorSpec : Spek({
    describe("a Docker container name generator") {
        val generator = DockerContainerNameGenerator()
        val container1 = Container("container-1", imageSourceDoesNotMatter())
        val container2 = Container("container-2", imageSourceDoesNotMatter())

        val nameForContainer1 = generator.generateNameFor(container1)
        val nameForContainer2 = generator.generateNameFor(container2)

        it("returns the container's name with a random suffix") {
            assertThat(nameForContainer1, matches("""^container\-1\-[a-z0-9]{6}$""".toRegex()))
        }

        it("uses the same suffix for other containers named by the same instance") {
            assertThat(nameForContainer1.substringAfter("container-1-"), equalTo(nameForContainer2.substringAfter("container-2-")))
        }
    }
})
