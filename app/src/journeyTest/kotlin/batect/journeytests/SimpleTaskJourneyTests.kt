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
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

abstract class SimpleTaskJourneyTest(private val testName: String, private val description: String) : Spek({
    describe(description) {
        val runner by createForGroup { ApplicationRunner(testName) }

        on("running that task") {
            val result by runBeforeGroup { runner.runApplication(listOf("the-task")) }

            it("prints the output from that task") {
                expect(result).output().toContain("This is some output from the task\n")
            }

            it("returns the exit code from that task") {
                expect(result).exitCode().toEqual(123)
            }
        }
    }
})

object TaskUsingDockerfileJourneyTest : SimpleTaskJourneyTest("simple-task-using-dockerfile", "a task that uses a Dockerfile with the command specified on the task in the configuration file")
object TaskUsingImageJourneyTest : SimpleTaskJourneyTest("simple-task-using-image", "a task that uses an existing image")
object ContainerWithCustomDockerfileJourneyTest : SimpleTaskJourneyTest("container-with-custom-dockerfile", "a task that uses a Dockerfile in a non-standard location")
object DependencyContainerWithSetupCommandJourneyTest : SimpleTaskJourneyTest("dependency-container-with-setup-command", "a task that uses a setup command on a dependency container")
object TaskContainerWithSetupCommandJourneyTest : SimpleTaskJourneyTest("task-container-with-setup-command", "a task that uses a setup command on the task container")
object ConfigWithIncludesJourneyTest : SimpleTaskJourneyTest("config-with-include", "a configuration file that uses includes")
