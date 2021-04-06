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

package batect.journeytests.testutils

import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.toList

data class ApplicationRunner(val testName: String) {
    val testDirectory: Path = Paths.get("src/journeyTest/resources", testName).toAbsolutePath()

    init {
        if (!Files.isDirectory(testDirectory)) {
            throw RuntimeException("The directory $testDirectory does not exist or is not a directory.")
        }
    }

    fun runApplication(arguments: Iterable<String>, environment: Map<String, String> = emptyMap(), afterStart: (Process) -> Unit = {}): ApplicationResult {
        val commandLine = commandLineForApplication(arguments)

        return runCommandLine(commandLine, environment, afterStart)
    }

    fun runCommandLine(commandLine: List<String>, environment: Map<String, String> = emptyMap(), afterStart: (Process) -> Unit = {}): ApplicationResult {
        val builder = ProcessBuilder(commandLine)
            .directory(testDirectory.toFile())
            .redirectErrorStream(true)

        builder.environment().putAll(environment)

        val process = builder.start()
        afterStart(process)

        val output = InputStreamReader(process.inputStream).readText()
        process.waitFor()

        return ApplicationResult(process.exitValue(), output)
    }

    fun commandLineForApplication(arguments: Iterable<String>): List<String> {
        val applicationDirectory: Path = Paths.get("build/install/app-shadow/lib").toAbsolutePath()

        val applicationPath = Files.list(applicationDirectory).toList()
            .single { it.fileName.toString().startsWith("batect-") && it.fileName.toString().endsWith(".jar") }
            .toAbsolutePath()

        val java9OrLaterOptions = if (runningOnJava9OrLater) listOf("--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens", "java.base/java.io=ALL-UNNAMED") else emptyList()

        return listOf("java") + java9OrLaterOptions + "-jar" + applicationPath.toString() + arguments
    }

    private val runningOnJava9OrLater: Boolean
        get() = !System.getProperty("java.version").startsWith("1.")
}

data class ApplicationResult(
    val exitCode: Int,
    val output: String
)
