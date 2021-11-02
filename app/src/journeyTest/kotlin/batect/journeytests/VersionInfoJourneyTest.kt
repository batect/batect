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
import batect.testutils.on
import ch.tutteli.atrium.api.fluent.en_GB.contains
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object VersionInfoJourneyTest : Spek({
    describe("the application") {
        val runner = ApplicationRunner("")

        on("getting version info") {
            val result = runner.runApplication(listOf("--version"))

            it("prints some version info") {
                expect(result).output().toContain("Batect version:")
                expect(result).output().toContain("Built:")
                expect(result).output().toContain("JVM version:")
                expect(result).output().toContain("OS version:")
                expect(result).output().toContain("Docker version:")
                expect(result).output().toContain("Git version:")
            }

            it("returns a zero exit code") {
                expect(result).exitCode().toEqual(0)
            }
        }
    }
})
