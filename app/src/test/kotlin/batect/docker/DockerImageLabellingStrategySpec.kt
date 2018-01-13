/*
   Copyright 2017-2018 Charles Korn.

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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import batect.config.Container
import batect.testutils.imageSourceDoesNotMatter
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DockerImageLabellingStrategySpec : Spek({
    describe("a Docker image labelling strategy") {
        val imageLabellingStrategy = DockerImageLabellingStrategy()

        given("a project name and a container definition") {
            val projectName = "the-project"
            val container = Container("the-container", imageSourceDoesNotMatter())

            on("generating a label for the image") {
                val label = imageLabellingStrategy.labelImage(projectName, container)

                it("returns the expected image name") {
                    assertThat(label, equalTo("the-project-the-container:latest"))
                }
            }
        }
    }
})
