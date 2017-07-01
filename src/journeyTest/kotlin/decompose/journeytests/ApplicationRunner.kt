package decompose.journeytests

import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths

data class ApplicationRunner(val testName: String, val arguments: Iterable<String>) {
    val applicationPath = Paths.get("build/install/decompose-kt/bin/decompose-kt").toAbsolutePath()
    val testDirectory = Paths.get("src/journeyTest/resources", testName).toAbsolutePath()

    init {
        if (!Files.isExecutable(applicationPath)) {
            throw RuntimeException("The path $applicationPath does not exist or is not executable.")
        }

        if (!Files.isDirectory(testDirectory)) {
            throw RuntimeException("The directory $testDirectory does not exist or is not a directory.")
        }
    }

    fun run(): ApplicationResult {
        val commandLine = listOf(applicationPath.toString()) + arguments
        val process = ProcessBuilder(commandLine)
                .directory(testDirectory.toFile())
                .redirectErrorStream(true)
                .start()

        process.waitFor()

        val output = InputStreamReader(process.getInputStream()).readText()
        return ApplicationResult(process.exitValue(), output)
    }
}

data class ApplicationResult(val exitCode: Int, val output: String)
