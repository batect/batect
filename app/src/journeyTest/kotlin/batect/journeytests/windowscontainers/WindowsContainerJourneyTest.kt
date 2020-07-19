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

package batect.journeytests.windowscontainers

import batect.journeytests.testutils.ApplicationRunner
import batect.journeytests.testutils.DockerUtils
import batect.journeytests.testutils.exitCode
import batect.journeytests.testutils.output
import batect.testutils.createForGroup
import batect.testutils.on
import batect.testutils.runBeforeGroup
import ch.tutteli.atrium.api.fluent.en_GB.contains
import ch.tutteli.atrium.api.fluent.en_GB.isEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toBe
import ch.tutteli.atrium.api.verbs.assert
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object WindowsContainerJourneyTest : Spek({
    describe("when running a Windows container") {
        val runner by createForGroup { ApplicationRunner("windows-container") }

        on("running a task") {
            val containersBeforeTest by runBeforeGroup { DockerUtils.getAllCreatedContainers() }
            val networksBeforeTest by runBeforeGroup { DockerUtils.getAllNetworks() }
            val result by runBeforeGroup { runner.runApplication(listOf("the-task")) }

            it("prints the output from that task") {
                assert(result).output().contains("This is some output from the task")
            }

            it("returns the exit code from that task") {
                assert(result).exitCode().toBe(123)
            }

            it("cleans up all containers it creates") {
                val containersAfterTest = DockerUtils.getAllCreatedContainers()
                val potentiallyOrphanedContainers = containersAfterTest - containersBeforeTest

                assert(potentiallyOrphanedContainers).isEmpty()
            }

            it("cleans up all networks it creates") {
                val networksAfterTest = DockerUtils.getAllNetworks()
                val potentiallyOrphanedNetworks = networksAfterTest - networksBeforeTest

                assert(potentiallyOrphanedNetworks).isEmpty()
            }
        }
    }
})
