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

package batect.execution.model.events

import batect.config.BuildImage
import batect.docker.DockerImage
import batect.testutils.on
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths

object ImageBuiltEventSpec : Spek({
    describe("an 'image built' event") {
        val image = DockerImage("image-1")
        val source = BuildImage(Paths.get("/some-build-dir"))
        val event = ImageBuiltEvent(source, image)

        on("toString()") {
            it("returns a human-readable representation of itself") {
                com.natpryce.hamkrest.assertion.assertThat(event.toString(), equalTo("ImageBuiltEvent(source: $source, image: 'image-1')"))
            }
        }
    }
})
