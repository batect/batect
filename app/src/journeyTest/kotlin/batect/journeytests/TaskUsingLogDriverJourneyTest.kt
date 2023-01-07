/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.journeytests

import batect.journeytests.testutils.ApplicationRunner
import batect.testutils.createForGroup
import batect.testutils.on
import batect.testutils.runBeforeGroup
import io.kotest.assertions.asClue
import io.kotest.matchers.collections.shouldContainAnyOf
import io.kotest.matchers.shouldBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TaskUsingLogDriverJourneyTest : Spek({
    describe("a task with a log driver that does not support streaming output") {
        val runner by createForGroup { ApplicationRunner("task-using-log-driver") }

        on("running that task") {
            val result by runBeforeGroup { runner.runApplication(listOf("the-task")) }

            it("prints the output from that task") {
                // On Docker 20.10+, retrieving output from a container using a log driver succeeds.
                // On versions prior to 20.10, retrieving output from a container using a log driver fails.
                result.output.lines() shouldContainAnyOf setOf(
                    "Error attaching: configured logging driver does not support reading",
                    "This is some output from the task",
                )
            }

            it("returns the exit code from that task") {
                result.asClue { it.exitCode shouldBe 123 }
            }
        }
    }
})
