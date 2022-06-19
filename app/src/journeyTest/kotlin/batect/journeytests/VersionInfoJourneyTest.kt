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
import batect.testutils.on
import io.kotest.assertions.asClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object VersionInfoJourneyTest : Spek({
    describe("the application") {
        val runner = ApplicationRunner("")

        on("getting version info") {
            val result = runner.runApplication(listOf("--version"))

            it("prints some version info") {
                result.asClue { it.output shouldContain "Batect version:" }
                result.asClue { it.output shouldContain "Built:" }
                result.asClue { it.output shouldContain "JVM version:" }
                result.asClue { it.output shouldContain "OS version:" }
                result.asClue { it.output shouldContain "Docker version:" }
                result.asClue { it.output shouldContain "Git version:" }
            }

            it("returns a zero exit code") {
                result.asClue { it.exitCode shouldBe 0 }
            }
        }
    }
})
