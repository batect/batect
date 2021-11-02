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
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.File

object LoggingJourneyTest : Spek({
    describe("the application") {
        val runner by createForGroup { ApplicationRunner("") }

        on("running with logging enabled") {
            val logPath by createForGroup { File.createTempFile("batect-log-journey-tests", ".log") }
            beforeGroup { logPath.deleteOnExit() }

            val result by runBeforeGroup { runner.runApplication(listOf("--log-file=$logPath", "--version")) }

            it("logs some information to the log file") {
                expect(logPath.exists()).toEqual(true)
                expect(logPath.length()).toBeGreaterThan(0L)
            }

            it("returns a zero exit code") {
                expect(result).exitCode().toEqual(0)
            }
        }
    }
})
