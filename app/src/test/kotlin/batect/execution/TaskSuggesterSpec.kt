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

package batect.execution

import batect.config.Configuration
import batect.config.ContainerMap
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it

object TaskSuggesterSpec : Spek({
    describe("a task suggester") {
        val taskRunConfiguration = TaskRunConfiguration("some-container")
        val task1 = Task("task1", taskRunConfiguration)
        val task2 = Task("task1234", taskRunConfiguration)
        val task3 = Task("wacky-things", taskRunConfiguration)
        val config = Configuration("the-project", TaskMap(task1, task2, task3), ContainerMap())
        val suggester = TaskSuggester()

        given("there are no close matches to the original task name") {
            val result = suggester.suggestCorrections(config, "completely-different-task-name")

            it("returns an empty list") {
                assertThat(result, isEmpty)
            }
        }

        given("there is one close match to the original task name") {
            val result = suggester.suggestCorrections(config, "task-1")

            it("returns that close match") {
                assertThat(result, equalTo(listOf("task1")))
            }
        }

        given("there are multiple close matches to the original task name") {
            val result = suggester.suggestCorrections(config, "task2")

            it("returns those matches, with the closest match first") {
                assertThat(result, equalTo(listOf("task1", "task1234")))
            }
        }
    }
})
