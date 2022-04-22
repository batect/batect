/*
    Copyright 2017-2022 Charles Korn.

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
import batect.journeytests.testutils.Docker
import batect.journeytests.testutils.exitCode
import batect.journeytests.testutils.output
import batect.testutils.createForGroup
import batect.testutils.on
import batect.testutils.runBeforeGroup
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RunAsCurrentUserWithCacheJourneyTest : Spek({
    describe("a task with 'run as current user' enabled that uses cache mounts") {
        beforeGroup {
            Docker.deleteCache("run-as-current-user-with-cache-test-normal-cache")
            Docker.deleteCache("run-as-current-user-with-cache-test-nested-cache")
            Docker.deleteCache("run-as-current-user-with-cache-test-deeply-nested-cache")
        }

        val runner by createForGroup { ApplicationRunner("run-as-current-user-with-cache") }

        on("running that task") {
            val result by runBeforeGroup { runner.runApplication(listOf("the-task")) }

            it("prints the output from that task") {
                val expectedOutput = listOf(
                    "/cache exists",
                    "/cache/created-file created",
                    "/home/special-place/cache exists",
                    "/home/special-place/cache/created-file created",
                    "/home/special-place/subdir/cache exists",
                    "/home/special-place/subdir/cache/created-file created"
                ).joinToString("\n")

                expect(result).output().toContain(expectedOutput)
            }

            it("returns the exit code from that task") {
                expect(result).exitCode().toEqual(0)
            }
        }
    }
})
