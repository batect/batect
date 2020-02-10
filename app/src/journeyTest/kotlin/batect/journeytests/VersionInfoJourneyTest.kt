/*
   Copyright 2017-2020 Charles Korn.

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
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object VersionInfoJourneyTest : Spek({
    describe("the application") {
        val runner = ApplicationRunner("")

        on("getting version info") {
            val result = runner.runApplication(listOf("--version"))

            it("prints some version info") {
                assertThat(result.output, containsSubstring("batect version:"))
                assertThat(result.output, containsSubstring("Built:"))
                assertThat(result.output, containsSubstring("JVM version:"))
                assertThat(result.output, containsSubstring("OS version:"))
                assertThat(result.output, containsSubstring("Docker version:"))
            }

            it("returns a zero exit code") {
                assertThat(result.exitCode, equalTo(0))
            }
        }
    }
})
