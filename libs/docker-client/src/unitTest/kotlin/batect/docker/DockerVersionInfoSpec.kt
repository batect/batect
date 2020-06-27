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
import batect.Version
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DockerVersionInfoSpec : Spek({
    describe("a set of Docker version information") {
        val info = DockerVersionInfo(Version(17, 9, 1, "ce"), "serverApi", "serverMinApi", "serverCommit", "my_cool_os")

        describe("converting it to a string") {
            val result = info.toString()

            it("returns a human-readable representation of itself") {
                assertThat(result, equalTo("17.9.1-ce (API version: serverApi, minimum supported API version: serverMinApi, commit: serverCommit, operating system: 'my_cool_os')"))
            }
        }
    }
})
