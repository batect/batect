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
import batect.journeytests.testutils.DockerUtils
import batect.testutils.createForGroup
import batect.testutils.on
import batect.testutils.platformLineSeparator
import batect.testutils.runBeforeGroup
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.contains
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.InputStreamReader

object DontCleanupAfterDependencyStartupFailureTest : Spek({
    describe("a task with an unhealthy dependency") {
        val runner by createForGroup { ApplicationRunner("task-with-unhealthy-dependency") }
        val cleanupCommands by createForGroup { mutableListOf<String>() }
        val containersBeforeTest by runBeforeGroup { DockerUtils.getAllCreatedContainers() }
        val networksBeforeTest by runBeforeGroup { DockerUtils.getAllNetworks() }

        afterGroup {
            cleanupCommands.forEach {
                val commandLine = it.trim().split(" ")

                val exitCode = ProcessBuilder(commandLine)
                    .start()
                    .waitFor()

                assertThat(exitCode, equalTo(0))
            }

            val containersAfterTest = DockerUtils.getAllCreatedContainers()
            val orphanedContainers = containersAfterTest - containersBeforeTest
            assertThat(orphanedContainers, isEmpty)

            val networksAfterTest = DockerUtils.getAllNetworks()
            val orphanedNetworks = networksAfterTest - networksBeforeTest
            assertThat(orphanedNetworks, isEmpty)
        }

        on("running that task with the '--no-cleanup-on-failure' option") {
            val result by runBeforeGroup { runner.runApplication(listOf("--no-cleanup-after-failure", "--no-color", "the-task")) }
            val commandsRegex = """For container http-server, view its output by running '(?<logsCommand>docker logs (?<id>.*))', or run a command in the container with 'docker exec -it \2 <command>'\.""".toRegex()
            val cleanupRegex = """Once you have finished investigating the issue, clean up all temporary resources created by batect by running:$platformLineSeparator(?<command>(.|$platformLineSeparator)+)$platformLineSeparator$platformLineSeparator""".toRegex()

            beforeGroup {
                val cleanupCommand = cleanupRegex.find(result.output)?.groups?.get("command")?.value

                if (cleanupCommand != null) {
                    cleanupCommands.addAll(cleanupCommand.split("\n"))
                }
            }

            it("does not execute the task") {
                assertThat(result.output, !containsSubstring("This task should never be executed!"))
            }

            it("prints a message explaining what happened and what to do about it") {
                assertThat(result.output, containsSubstring("Container http-server did not become healthy.${platformLineSeparator}The configured health check did not indicate that the container was healthy within the timeout period."))
            }

            it("prints a message explaining how to see the logs of that dependency and how to run a command in the container") {
                assertThat(result.output, contains(commandsRegex))
            }

            it("prints a message explaining how to clean up any containers left behind") {
                assertThat(result.output, contains(cleanupRegex))
            }

            it("does not stop the container") {
                val containerId = commandsRegex.find(result.output)?.groups?.get("id")?.value

                assertThat(containerId, !absent<String>())

                val inspectProcess = ProcessBuilder("docker", "inspect", containerId, "--format", "{{.State.Status}}")
                    .redirectErrorStream(true)
                    .start()

                val output = InputStreamReader(inspectProcess.inputStream).readText().trim()
                inspectProcess.waitFor()

                assertThat(inspectProcess.exitValue(), equalTo(0))
                assertThat(output, equalTo("running"))
            }

            it("the command given to view the logs displays the logs from the container") {
                val logsCommand = commandsRegex.find(result.output)?.groups?.get("logsCommand")?.value

                assertThat(logsCommand, !absent<String>())

                val logsProcess = ProcessBuilder(logsCommand!!.trim().split(" "))
                    .redirectErrorStream(true)
                    .start()

                val output = InputStreamReader(logsProcess.inputStream).readText().trim()
                logsProcess.waitFor()

                assertThat(output, equalTo("This is some output from the HTTP server"))
                assertThat(logsProcess.exitValue(), equalTo(0))
            }

            it("exits with a non-zero code") {
                assertThat(result.exitCode, !equalTo(0))
            }
        }
    }
})
