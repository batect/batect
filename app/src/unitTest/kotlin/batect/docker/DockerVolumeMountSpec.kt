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

import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DockerVolumeMountSpec : Spek({
    describe("a Docker volume mount") {
        given("a volume mount with no options") {
            val mount = DockerVolumeMount("/local", "/container")

            it("converts to a string with no options included") {
                assertThat(mount.toString(), equalTo("/local:/container"))
            }
        }

        given("a volume mount with options") {
            val mount = DockerVolumeMount("/local", "/container", "some-options")

            it("converts to a string with the options included") {
                assertThat(mount.toString(), equalTo("/local:/container:some-options"))
            }
        }
    }
})
