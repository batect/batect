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
import batect.journeytests.testutils.itCleansUpAllContainersItCreates
import batect.journeytests.testutils.itCleansUpAllNetworksItCreates
import batect.testutils.createForGroup
import batect.testutils.doesNotThrow
import batect.testutils.on
import batect.testutils.runBeforeGroup
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import jnr.ffi.Platform
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object RunAsCurrentUserJourneyTest : Spek({
    mapOf(
        "run-as-current-user" to "a task with 'run as current user' enabled",
        "run-as-current-user-with-mount" to "a task with 'run as current user' enabled that has a mount inside the home directory"
    ).forEach { (name, description) ->
        describe(description) {
            val runner by createForGroup { ApplicationRunner(name) }

            on("running that task") {
                val outputDirectory by runBeforeGroup { Paths.get("build/test-results/journey-tests/$name").toAbsolutePath() }
                val localUserName by runBeforeGroup { System.getProperty("user.name") }
                val expectedContainerUserName by runBeforeGroup { getUserNameInContainer() }
                val expectedContainerGroupName by runBeforeGroup { getGroupNameInContainer() }
                val expectedFilePath by runBeforeGroup { outputDirectory.resolve("created-file") }

                beforeGroup {
                    Files.createDirectories(outputDirectory)
                    deleteDirectoryContents(outputDirectory)
                }

                val result by runBeforeGroup { runner.runApplication(listOf("the-task")) }

                it("prints the output from that task") {
                    val expectedOutput = listOf(
                        "User: $expectedContainerUserName",
                        "Group: $expectedContainerGroupName",
                        "Home directory: /home/special-place",
                        "Home directory exists",
                        "Home directory owned by user: $expectedContainerUserName",
                        "Home directory owned by group: $expectedContainerGroupName"
                    ).joinToString("\r\n")

                    assertThat(result.output, containsSubstring(expectedOutput))
                }

                if (Platform.getNativePlatform().os != Platform.OS.WINDOWS) {
                    // On Windows, the owner of the file will be the owner of the directory the file was created in, which is not
                    // always predictable. Being the owner doesn't guarantee any permissions beyond being able to grant yourself
                    // access, so this test isn't so meaningful.

                    it("creates files as the current user, not root") {
                        assertThat(Files.getOwner(expectedFilePath).name, equalTo(localUserName))
                    }
                }

                it("creates files so that the current host user can read, edit and delete them") {
                    assertThat({
                        Files.readAllBytes(expectedFilePath)
                        Files.write(expectedFilePath, byteArrayOf(1, 2, 3))
                        Files.delete(expectedFilePath)
                    }, doesNotThrow())
                }

                it("returns the exit code from that task") {
                    assertThat(result.exitCode, equalTo(0))
                }

                itCleansUpAllContainersItCreates { result }
                itCleansUpAllNetworksItCreates { result }
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

// On Windows, all mounted directories are mounted with root as the owner and this cannot be changed.
// See https://github.com/docker/for-win/issues/63 and https://github.com/docker/for-win/issues/39.
private fun getUserNameInContainer(): String {
    if (Platform.getNativePlatform().os == Platform.OS.WINDOWS) {
        return "root"
    }

    return System.getProperty("user.name")
}

private fun getGroupNameInContainer(): String {
    if (Platform.getNativePlatform().os == Platform.OS.WINDOWS) {
        return "root"
    }

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
