/*
   Copyright 2017 Charles Korn.

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
import com.natpryce.hamkrest.isEmpty
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerWithMountJourneyTests : Spek({
    mapOf(
            "container-with-mount" to "a simple task with a volume mount",
            "container-with-cached-mount" to "a simple task with a cached volume mount"
    ).forEach { testName, description ->
        given(description) {
            val runner = ApplicationRunner(testName)

            on("running that task") {
                val result = runner.runApplication(listOf("the-task"))

                it("prints the output from that task") {
                    assertThat(result.output, containsSubstring("This is some output from the script"))
                }

                it("returns the exit code from that task") {
                    assertThat(result.exitCode, equalTo(123))
                }

                it("cleans up all containers it creates") {
                    assertThat(result.potentiallyOrphanedContainers, isEmpty)
                }
            }
        }
    }
})
