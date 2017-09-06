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

import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class ApplicationRunner(val testName: String, val arguments: Iterable<String>) {
    val applicationPath: Path = Paths.get("build/install/batect/bin/batect").toAbsolutePath()
    val testDirectory: Path = Paths.get("src/journeyTest/resources", testName).toAbsolutePath()

    init {
        if (!Files.isExecutable(applicationPath)) {
            throw RuntimeException("The path $applicationPath does not exist or is not executable.")
        }

        if (!Files.isDirectory(testDirectory)) {
            throw RuntimeException("The directory $testDirectory does not exist or is not a directory.")
        }
    }

    fun run(): ApplicationResult {
        val containersBefore = getContainers()
        val commandLine = listOf(applicationPath.toString()) + arguments
        val process = ProcessBuilder(commandLine)
                .directory(testDirectory.toFile())
                .redirectErrorStream(true)
                .start()

        process.waitFor()
        val output = InputStreamReader(process.inputStream).readText()

        val containersAfter = getContainers()
        val potentiallyOrphanedContainers = containersAfter - containersBefore

        return ApplicationResult(process.exitValue(), output, potentiallyOrphanedContainers)
    }

    fun getContainers(): Set<String> {
        val commandLine = listOf("docker", "ps", "--all", "--format", "{{.Names}} ({{.ID}}): {{.Image}}")
        val process = ProcessBuilder(commandLine)
                .redirectErrorStream(true)
                .start()

        val exitCode = process.waitFor()
        val output = InputStreamReader(process.inputStream).readText()

        if (exitCode != 0) {
            throw Exception("Retrieving list of containers from Docker failed with exit code $exitCode. Output from Docker was: $output")
        }

        return output.split("\n").toSet()
    }
}

data class ApplicationResult(val exitCode: Int, val output: String, val potentiallyOrphanedContainers: Set<String>)
