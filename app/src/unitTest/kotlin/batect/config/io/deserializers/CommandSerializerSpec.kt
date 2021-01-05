/*
   Copyright 2017-2021 Charles Korn.

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

package batect.config.io.deserializers

import batect.os.Command
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.utils.Json
import com.natpryce.hamkrest.assertion.assertThat
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CommandSerializerSpec : Spek({
    describe("converting a command to JSON for logging") {
        given("a command") {
            val command = Command.parse("the-command --the-arg")

            val json by createForEachTest { Json.forLogging.encodeToString(CommandSerializer, command) }

            it("represents the command as an array") {
                assertThat(json, equivalentTo("""["the-command", "--the-arg"]"""))
            }
        }
    }
})
