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
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ListTasksJourneyTest : Spek({
    given("a configuration file with multiple tasks") {
        val runner = ApplicationRunner("many-tasks")

        on("listing available tasks") {
            val result = runner.runApplication(listOf("tasks"))

            it("prints a list of all available tasks") {
                assertThat(result.output, containsSubstring("""
                    |- task-1: do the first thing
                    |- task-2: do the second thing
                    |- task-3: do the third thing
                    """.trimMargin()))
            }

            it("returns a zero exit code") {
                assertThat(result.exitCode, equalTo(0))
            }
        }
    }
})
