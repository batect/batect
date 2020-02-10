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

import batect.config.PullImage
import batect.docker.pull.DockerImageProgress
import batect.testutils.on
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ImagePullProgressEventSpec : Spek({
    describe("an 'image pull progress' event") {
        val source = PullImage("some-image")
        val event = ImagePullProgressEvent(source, DockerImageProgress("Doing stuff", 10, 30))

        on("toString()") {
            it("returns a human-readable representation of itself") {
                com.natpryce.hamkrest.assertion.assertThat(event.toString(), equalTo("ImagePullProgressEvent(source: $source, current operation: 'Doing stuff', completed bytes: 10, total bytes: 30)"))
            }
        }
    }
})
