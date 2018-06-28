/*
   Copyright 2017-2018 Charles Korn.

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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object RunAsCurrentUserJourneyTest : Spek({
    mapOf(
        "run-as-current-user" to "a task with 'run as current user' enabled",
        "run-as-current-user-with-mount" to "a task with 'run as current user' enabled that has a mount inside the home directory"
    ).forEach { name, description ->
        given(description) {
            val runner = ApplicationRunner(name)

            on("running that task") {
                val outputDirectory = Paths.get("build/test-results/journey-tests/$name").toAbsolutePath()
                Files.createDirectories(outputDirectory)
                deleteDirectoryContents(outputDirectory)

                val result = runner.runApplication(listOf("the-task"))
                val userName = System.getProperty("user.name")
                val groupName = getGroupName()

                it("prints the output from that task") {
                    val expectedOutput = listOf(
                        "User: $userName",
                        "Group: $groupName",
                        "Home directory: /home/special-place",
                        "Home directory exists",
                        "Home directory owned by user: $userName",
                        "Home directory owned by group: $groupName"
                    ).joinToString("\r\n")

                    assertThat(result.output, containsSubstring(expectedOutput))
                }

                it("creates files as the current user, not root") {
                    val expectedFilePath = outputDirectory.resolve("created-file")
                    val owner = Files.getOwner(expectedFilePath)
                    assertThat(owner.name, equalTo(userName))
                }

                it("returns the exit code from that task") {
                    assertThat(result.exitCode, equalTo(0))
                }

                it("cleans up all containers it creates") {
                    assertThat(result.potentiallyOrphanedContainers, isEmpty)
                }
            }
        }
    }
})

private fun deleteDirectoryContents(directory: Path) {
    Files.newDirectoryStream(directory).use { stream ->
        stream.forEach { path ->
            if (Files.isDirectory(path)) {
                deleteDirectoryContents(path)
            }

            Files.delete(path)
        }
    }
}

private fun getGroupName(): String {
    val commandLine = listOf("id", "-gn")
    val process = ProcessBuilder(commandLine)
        .redirectErrorStream(true)
        .start()

    val exitCode = process.waitFor()
    val output = InputStreamReader(process.inputStream).readText()

    if (exitCode != 0) {
        throw Exception("Retrieving user's primary group name failed with exit code $exitCode. Output from command was: $output")
    }

    return output.trim()
}
