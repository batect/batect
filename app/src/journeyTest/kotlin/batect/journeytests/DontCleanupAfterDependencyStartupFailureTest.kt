/*
   Copyright 2017 Charles Korn.

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

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.contains
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.InputStreamReader

object DontCleanupAfterDependencyStartupFailureTest : Spek({
    given("a task with an unhealthy dependency") {
        val runner = ApplicationRunner("task-with-unhealthy-dependency")
        val cleanupCommands = mutableListOf<String>()
        var containersBeforeTest = emptySet<String>()
        var networksBeforeTest = emptySet<String>()

        beforeGroup {
            containersBeforeTest = DockerUtils.getAllCreatedContainers()
            networksBeforeTest = DockerUtils.getAllNetworks()
        }

        afterGroup {
            cleanupCommands.forEach {
                val exitCode = ProcessBuilder("/usr/bin/env", "bash", "-c", it)
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
            val result = runner.runApplication(listOf("--no-cleanup-after-failure", "the-task"))
            val commandsRegex = """For container 'http-server': view its output by running '(?<logsCommand>docker logs (?<id>.*))', or run a command in the container with 'docker exec -it \2 <command>'\.""".toRegex()
            val cleanupRegex = """To clean up the containers and task network once you have finished investigating the issue, run '(?<command>docker rm --force ([a-z0-9]+\s)+&& docker network rm [a-z0-9]+)'\.""".toRegex()
            val cleanupCommand = cleanupRegex.find(result.output)?.groups?.get("command")?.value

            if (cleanupCommand != null) {
                cleanupCommands.add(cleanupCommand)
            }

            it("does not execute the task") {
                assertThat(result.output, !containsSubstring("This task should never be executed!"))
            }

            it("prints a message explaining what happened and what to do about it") {
                assertThat(result.output, containsSubstring("Dependency 'http-server' did not become healthy: The configured health check did not indicate that the container was healthy within the timeout period."))
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

                inspectProcess.waitFor()

                assertThat(inspectProcess.exitValue(), equalTo(0))
                assertThat(InputStreamReader(inspectProcess.inputStream).readText().trim(), equalTo("running"))
            }

            it("the command given to view the logs displays the logs from the container") {
                val logsCommand = commandsRegex.find(result.output)?.groups?.get("logsCommand")?.value

                assertThat(logsCommand, !absent<String>())

                val logsProcess = ProcessBuilder("/usr/bin/env", "bash", "-c", logsCommand)
                    .redirectErrorStream(true)
                    .start()

                logsProcess.waitFor()

                assertThat(InputStreamReader(logsProcess.inputStream).readText().trim(), equalTo("This is some output from the HTTP server"))
                assertThat(logsProcess.exitValue(), equalTo(0))
            }

            it("exits with a non-zero code") {
                assertThat(result.exitCode, !equalTo(0))
            }
        }
    }
})
