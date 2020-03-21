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
import batect.journeytests.testutils.DockerUtils
import batect.journeytests.testutils.deleteDirectoryContents
import batect.journeytests.testutils.itCleansUpAllContainersItCreates
import batect.journeytests.testutils.itCleansUpAllNetworksItCreates
import batect.testutils.createForGroup
import batect.testutils.on
import batect.testutils.runBeforeGroup
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CacheMountJourneyTest : Spek({
    describe("running a container with a cache mounted") {
        val runner by createForGroup { ApplicationRunner("cache-mount") }

        beforeGroup {
            DockerUtils.deleteCache("batect-cache-mount-journey-test-cache")
            deleteDirectoryContents(runner.testDirectory.resolve(".batect").resolve("caches").resolve("batect-cache-mount-journey-test-cache"))
        }

        mapOf(
            "running the task with caches set to use volume mounts" to "--cache-type=volume",
            "running the task with caches set to use directory mounts" to "--cache-type=directory"
        ).forEach { (description, arg) ->
            describe(description) {
                on("running the task twice") {
                    val firstResult by runBeforeGroup { runner.runApplication(listOf(arg, "the-task")) }
                    val secondResult by runBeforeGroup { runner.runApplication(listOf(arg, "the-task")) }

                    it("should not have access to the file in the cache in the first run and create it") {
                        assertThat(firstResult.output, containsSubstring("File created in task does not exist, creating it\n"))
                    }

                    it("should have access to the file in the cache in the second run") {
                        assertThat(secondResult.output, containsSubstring("File created in task exists\n"))
                    }

                    it("should not have access to the file from the image in the first run") {
                        assertThat(firstResult.output, containsSubstring("File created in image does not exist\n"))
                    }

                    it("should not have access to the file from the image in the second run") {
                        assertThat(secondResult.output, containsSubstring("File created in image does not exist\n"))
                    }

                    it("should succeed on the first run") {
                        assertThat(firstResult.exitCode, equalTo(0))
                    }

                    it("should succeed on the second run") {
                        assertThat(secondResult.exitCode, equalTo(0))
                    }

                    itCleansUpAllContainersItCreates { firstResult }
                    itCleansUpAllNetworksItCreates { firstResult }
                    itCleansUpAllContainersItCreates { secondResult }
                    itCleansUpAllNetworksItCreates { secondResult }
                }
            }
        }
    }
})
