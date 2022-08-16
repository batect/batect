/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.journeytests

import batect.journeytests.testutils.ApplicationRunner
import batect.journeytests.testutils.Docker
import batect.testutils.createForGroup
import batect.testutils.on
import batect.testutils.runBeforeGroup
import io.kotest.assertions.asClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TagImageJourneyTest : Spek({
    describe("tagging a image built during a task") {
        val imageTag = "${TagImageJourneyTest::class.simpleName!!.lowercase()}-test-image:abcd1234"
        val runner by createForGroup { ApplicationRunner("simple-task-using-dockerfile") }

        on("running that task") {
            beforeGroup { Docker.removeImageIfExists(imageTag) }

            val result by runBeforeGroup { runner.runApplication(listOf("--tag-image", "build-env=$imageTag", "the-task")) }

            it("prints the output from that task") {
                result.asClue { it.output shouldContain "This is some output from the task\n" }
            }

            it("returns the exit code from that task") {
                result.asClue { it.exitCode shouldBe 123 }
            }

            it("tags the image built during execution") {
                Docker.getAllImages() shouldContain imageTag
            }
        }
    }
})
