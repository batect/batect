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
import batect.testutils.withPlatformSpecificLineSeparator
import io.kotest.assertions.asClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object NonStandardConfigurationFileNameJourneyTest : Spek({
    describe("a configuration file with a non-standard name") {
        on("listing available tasks") {
            val runner by createForGroup { ApplicationRunner("non-standard-name") }
            val result by runBeforeGroup { runner.runApplication(listOf("-f", "another-name.yml", "--list-tasks")) }

            it("prints a list of all available tasks") {
                result.asClue {
                    it.output shouldContain
                        """
                        |- task-1
                        |- task-2
                        |- task-3
                        """.trimMargin().withPlatformSpecificLineSeparator()
                }
            }

            it("returns a zero exit code") {
                result.asClue { it.exitCode shouldBe 0 }
            }
        }

        on("running a task") {
            val runner by createForGroup { ApplicationRunner("non-standard-name") }
            val result by runBeforeGroup { runner.runApplication(listOf("-f", "another-name.yml", "task-1")) }

            it("prints the output of the task ") {
                result.asClue { it.output shouldContain "This is some output from task 1\n" }
            }

            it("returns the exit code from the task") {
                result.asClue { it.exitCode shouldBe 123 }
            }
        }
    }
})
