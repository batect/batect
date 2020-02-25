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
import batect.docker.client.DockerImageBuildProgress
import batect.docker.pull.DockerImageProgress
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ImageBuildProgressEventSpec : Spek({
    describe("an 'image build progress' event") {
        val container = Container("the-container", imageSourceDoesNotMatter())

        given("it has some image pull progress information") {
            val event = ImageBuildProgressEvent(container, DockerImageBuildProgress(1, 10, "Something is happening", DockerImageProgress("downloading", 12, 20)))

            on("toString()") {
                it("returns a human-readable representation of itself") {
                    com.natpryce.hamkrest.assertion.assertThat(event.toString(), equalTo("ImageBuildProgressEvent(container: 'the-container', current step: 1, total steps: 10, message: 'Something is happening', pull progress: 'downloading: 12 B of 20 B (60%)')"))
                }
            }
        }

        given("it has no image pull progress information") {
            val event = ImageBuildProgressEvent(container, DockerImageBuildProgress(1, 10, "Something is happening", null))

            on("toString()") {
                it("returns a human-readable representation of itself") {
                    com.natpryce.hamkrest.assertion.assertThat(event.toString(), equalTo("ImageBuildProgressEvent(container: 'the-container', current step: 1, total steps: 10, message: 'Something is happening', pull progress: null)"))
                }
            }
        }
    }
})
