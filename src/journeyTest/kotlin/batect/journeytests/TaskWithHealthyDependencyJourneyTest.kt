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

object TaskWithHealthyDependencyJourneyTest : Spek({
    given("a task with a healthy dependency") {
        val runner = ApplicationRunner("task-with-healthy-dependency", listOf("run", "the-task"))

        on("running that task") {
            val result = runner.run()

            it("displays the output from that task") {
                assertThat(result.output, containsSubstring("Status code for request: 200"))
            }

            it("returns the exit code from that task") {
                assertThat(result.exitCode, equalTo(0))
            }

            it("cleans up all containers it creates") {
                assertThat(result.potentiallyOrphanedContainers, isEmpty)
            }
        }
    }
})
