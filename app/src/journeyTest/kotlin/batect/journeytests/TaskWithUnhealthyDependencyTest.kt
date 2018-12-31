/*
   Copyright 2017-2019 Charles Korn.

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
import batect.journeytests.testutils.itCleansUpAllContainersItCreates
import batect.journeytests.testutils.itCleansUpAllNetworksItCreates
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskWithUnhealthyDependencyTest : Spek({
    given("a task with an unhealthy dependency") {
        val runner = ApplicationRunner("task-with-unhealthy-dependency")

        on("running that task") {
            val result = runner.runApplication(listOf("--no-color", "the-task"))

            it("prints an appropriate error message") {
                assertThat(result.output, containsSubstring("Container http-server did not become healthy.\nThe configured health check did not indicate that the container was healthy within the timeout period."))
            }

            it("prints details of the failing health check") {
                assertThat(result.output, containsSubstring("The last health check exited with code 1 and output:"))
                assertThat(result.output, containsSubstring("This is some normal output"))
                assertThat(result.output, containsSubstring("This is some error output"))
            }

            it("returns a non-zero exit code") {
                assertThat(result.exitCode, !equalTo(0))
            }

            itCleansUpAllContainersItCreates(result)
            itCleansUpAllNetworksItCreates(result)
        }
    }
})
