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

object DontCleanupAfterSuccessTest : Spek({
    describe("a task with a prerequisite") {
        val runner by createForGroup { ApplicationRunner("task-with-prerequisite") }
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

        on("running that task with the '--no-cleanup-on-success' option") {
            val result by runBeforeGroup { runner.runApplication(listOf("--no-cleanup-after-success", "--no-color", "do-stuff")) }
            val commandsRegex = """For container build-env, view its output by running '(?<logsCommand>docker logs (?<id>.*))', or run a command in the container with '(.*)'\.""".toRegex()
            val cleanupRegex = """Once you have finished using the containers, clean up all temporary resources created by batect by running:$platformLineSeparator(?<command>(.|$platformLineSeparator)+)$platformLineSeparator""".toRegex()

            beforeGroup {
                val cleanupCommand = cleanupRegex.find(result.output)?.groups?.get("command")?.value

                if (cleanupCommand != null) {
                    cleanupCommands.addAll(cleanupCommand.split("\n"))
                }
            }

            it("prints the output from the main task") {
                assertThat(result.output, containsSubstring("This is some output from the main task\n"))
            }

            it("prints the output from the prerequisite task") {
                assertThat(result.output, containsSubstring("This is some output from the build task\n"))
            }

            it("returns a non-zero exit code") {
                assertThat(result.exitCode, !equalTo(0))
            }

            it("does not return the exit code from the task") {
                assertThat(result.exitCode, !equalTo(123))
            }

            it("prints a message explaining how to see the logs of the container and how to run a command in the container") {
                assertThat(result.output, contains(commandsRegex))
            }

            it("prints a message explaining how to clean up any containers left behind") {
                assertThat(result.output, contains(cleanupRegex))
            }

            it("does not delete the container") {
                val containerId = commandsRegex.find(result.output)?.groups?.get("id")?.value

                assertThat(containerId, !absent<String>())

                val inspectProcess = ProcessBuilder("docker", "inspect", containerId, "--format", "{{.State.Status}}")
                    .redirectErrorStream(true)
                    .start()

                inspectProcess.waitFor()

                assertThat(inspectProcess.exitValue(), equalTo(0))
                assertThat(InputStreamReader(inspectProcess.inputStream).readText().trim(), equalTo("exited"))
            }

            it("the command given to view the logs displays the logs from the container") {
                val logsCommand = commandsRegex.find(result.output)?.groups?.get("logsCommand")?.value

                assertThat(logsCommand, !absent<String>())

                val logsProcess = ProcessBuilder(logsCommand!!.trim().split(" "))
                    .redirectErrorStream(true)
                    .start()

                logsProcess.waitFor()

                assertThat(InputStreamReader(logsProcess.inputStream).readText().trim(), equalTo("This is some output from the main task"))
                assertThat(logsProcess.exitValue(), equalTo(0))
            }
        }
    }
})
