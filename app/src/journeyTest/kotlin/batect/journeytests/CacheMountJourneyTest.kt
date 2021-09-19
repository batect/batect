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
import batect.journeytests.testutils.Docker
import batect.journeytests.testutils.exitCode
import batect.journeytests.testutils.output
import batect.os.deleteDirectoryContents
import batect.testutils.createForGroup
import batect.testutils.on
import batect.testutils.runBeforeGroup
import ch.tutteli.atrium.api.fluent.en_GB.contains
import ch.tutteli.atrium.api.fluent.en_GB.toBe
import ch.tutteli.atrium.api.verbs.assert
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CacheMountJourneyTest : Spek({
    describe("running a container with a cache mounted") {
        val runner by createForGroup { ApplicationRunner("cache-mount") }

        beforeGroup {
            Docker.deleteCache("batect-cache-mount-journey-test-cache")
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
                        assert(firstResult).output().contains("File created in task does not exist, creating it\n")
                    }

                    it("should have access to the file in the cache in the second run") {
                        assert(secondResult).output().contains("File created in task exists\n")
                    }

                    it("should succeed on the first run") {
                        assert(firstResult).exitCode().toBe(0)
                    }

                    it("should succeed on the second run") {
                        assert(secondResult).exitCode().toBe(0)
                    }
                }
            }
        }
    }
})
