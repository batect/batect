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

package batect.journeytests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

// What's the point of this test?
// We assume that the unit tests are enough to ensure that the upgrade process works correctly.
// (If that changes in the future, we could start batect with the wrapper, point it at a fake download
// server and test that the upgrade works correctly.)
// This test just ensures that Kodein is configured correctly for the upgrade command, which can't be (easily)
// checked with a unit test.
object UpgradeJourneyTest : Spek({
    given("the application") {
        on("running an upgrade for the application") {
            val runner = ApplicationRunner("")
            val result = runner.runApplication(listOf("--upgrade"))

            it("prints a message indicating that the upgrade can only be performed if the wrapper script is used") {
                assertThat(result.output, containsSubstring("batect was started without using the wrapper script and so cannot upgrade it."))
            }

            it("returns a non-zero exit code") {
                assertThat(result.exitCode, !equalTo(0))
            }
        }
    }
})
