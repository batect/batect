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
import ch.tutteli.atrium.api.fluent.en_GB.contains
import ch.tutteli.atrium.api.fluent.en_GB.notToEqual
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.verbs.expect
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

// What's the point of this test?
// We assume that the unit tests are enough to ensure that the upgrade process works correctly.
// (If that changes in the future, we could start Batect with the wrapper, point it at a fake download
// server and test that the upgrade works correctly.)
// This test just ensures that Kodein is configured correctly for the upgrade command, which can't be (easily)
// checked with a unit test.
object UpgradeJourneyTest : Spek({
    describe("the application") {
        on("running an upgrade for the application") {
            val runner by createForGroup { ApplicationRunner("") }
            val result by runBeforeGroup { runner.runApplication(listOf("--upgrade")) }

            it("prints a message indicating that the upgrade can only be performed if the wrapper script is used") {
                expect(result).output().toContain("Batect was started without using the wrapper script and so cannot upgrade it.")
            }

            it("returns a non-zero exit code") {
                expect(result).exitCode().notToEqual(0)
            }
        }
    }
})
