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
import batect.journeytests.testutils.output
import batect.testutils.createForGroup
import batect.testutils.on
import batect.testutils.runBeforeGroup
import batect.testutils.withPlatformSpecificLineSeparator
import ch.tutteli.atrium.api.fluent.en_GB.contains
import ch.tutteli.atrium.api.fluent.en_GB.notToEqual
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.verbs.expect
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TaskWithUnhealthyDependencyJourneyTest : Spek({
    describe("a task with an unhealthy dependency") {
        val runner by createForGroup { ApplicationRunner("task-with-unhealthy-dependency") }

        on("running that task") {
            val result by runBeforeGroup { runner.runApplication(listOf("--no-color", "the-task")) }

            it("prints an appropriate error message") {
                expect(result).output()
                    .toContain("Container http-server did not become healthy.\nThe configured health check did not indicate that the container was healthy within the timeout period.".withPlatformSpecificLineSeparator())
            }

            it("prints details of the failing health check") {
                expect(result).output().toContain("The last health check exited with code 1 and output:")
                expect(result).output().toContain("This is some normal output")
                expect(result).output().toContain("This is some error output")
            }

            it("returns a non-zero exit code") {
                expect(result).exitCode().notToEqual(0)
            }
        }
    }
})
