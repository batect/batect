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

package batect.journeytests

import batect.journeytests.testutils.ApplicationRunner
import batect.journeytests.testutils.exitCode
import batect.testutils.createForGroup
import batect.testutils.on
import batect.testutils.runBeforeGroup
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveElementsAndAny
import ch.tutteli.atrium.api.fluent.en_GB.toMatch
import ch.tutteli.atrium.api.verbs.expect
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
                expect(result.output.lines()).toHaveElementsAndAny {
                    toMatch("^(Error attaching: configured logging driver does not support reading|This is some output from the task)$".toRegex())
                }
            }

            it("returns the exit code from that task") {
                expect(result).exitCode().toEqual(123)
            }
        }
    }
})
