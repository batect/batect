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

package batect.journeytests.cleanup

import batect.journeytests.testutils.ApplicationRunner
import batect.journeytests.testutils.Docker
import batect.testutils.createForGroup
import batect.testutils.on
import batect.testutils.platformLineSeparator
import batect.testutils.runBeforeGroup
import io.kotest.assertions.asClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.InputStreamReader

object DontCleanupAfterDependencyStartupFailureJourneyTest : Spek({
    describe("a task with an unhealthy dependency") {
        val runner by createForGroup { ApplicationRunner("task-with-unhealthy-dependency") }
        val cleanupCommands by createForGroup { mutableListOf<String>() }
        val containersBeforeTest by runBeforeGroup { Docker.getAllCreatedContainers() }
        val networksBeforeTest by runBeforeGroup { Docker.getAllNetworks() }

        afterGroup {
            cleanupCommands.forEach {
                val commandLine = it.trim().split(" ")

                val exitCode = ProcessBuilder(commandLine)
                    .start()
                    .waitFor()

                exitCode shouldBe 0
            }

            val containersAfterTest = Docker.getAllCreatedContainers()
            val orphanedContainers = containersAfterTest - containersBeforeTest
            orphanedContainers.shouldBeEmpty()

            val networksAfterTest = Docker.getAllNetworks()
            val orphanedNetworks = networksAfterTest - networksBeforeTest
            orphanedNetworks.shouldBeEmpty()
        }

        on("running that task with the '--no-cleanup-on-failure' option") {
            val result by runBeforeGroup { runner.runApplication(listOf("--no-cleanup-after-failure", "--no-color", "the-task")) }
            val commandsRegex = """For container http-server, view its output by running '(?<logsCommand>docker logs (?<id>.*))', or run a command in the container with 'docker start \2; docker exec -it \2 <command>'\.""".toRegex()
            val cleanupRegex = """Once you have finished investigating the issue, clean up all temporary resources created by Batect by running:$platformLineSeparator(?<command>(.|$platformLineSeparator)+)$platformLineSeparator$platformLineSeparator""".toRegex()

            beforeGroup {
                val cleanupCommand = cleanupRegex.find(result.output)?.groups?.get("command")?.value

                if (cleanupCommand != null) {
                    cleanupCommands.addAll(cleanupCommand.split("\n"))
                }
            }

            it("does not execute the task") {
                result.asClue { it.output shouldNotContain "This task should never be executed!" }
            }

            it("prints a message explaining what happened and what to do about it") {
                result.asClue {
                    it.output shouldContain "Container http-server did not become healthy.${platformLineSeparator}The configured health check did not indicate that the container was healthy within the timeout period."
                }
            }

            it("prints a message explaining how to see the logs of that dependency and how to run a command in the container") {
                result.asClue { it.output shouldContain commandsRegex }
            }

            it("prints a message explaining how to clean up any containers left behind") {
                result.asClue { it.output shouldContain cleanupRegex }
            }

            it("does not delete the container") {
                val containerId = commandsRegex.find(result.output)?.groups?.get("id")?.value

                containerId shouldNotBe null

                val inspectProcess = ProcessBuilder("docker", "inspect", containerId, "--format", "{{.State.Status}}")
                    .redirectErrorStream(true)
                    .start()

                inspectProcess.waitFor()
                inspectProcess.exitValue() shouldBe 0

                val output = InputStreamReader(inspectProcess.inputStream).readText().trim()
                output shouldBe "exited"
            }

            it("the command given to view the logs displays the logs from the container") {
                val logsCommand = commandsRegex.find(result.output)?.groups?.get("logsCommand")?.value

                logsCommand shouldNotBe null

                val logsProcess = ProcessBuilder(logsCommand!!.trim().split(" "))
                    .redirectErrorStream(true)
                    .start()

                logsProcess.waitFor()
                logsProcess.exitValue() shouldBe 0

                val output = InputStreamReader(logsProcess.inputStream).readText().trim()
                output shouldContain "^This is some output from the HTTP server$".toRegex(RegexOption.MULTILINE)
            }

            it("exits with a non-zero code") {
                result.asClue { it.exitCode shouldNotBe 0 }
            }
        }
    }
})
