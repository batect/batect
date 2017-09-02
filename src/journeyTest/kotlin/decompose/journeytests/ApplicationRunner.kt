package decompose.journeytests

import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class ApplicationRunner(val testName: String, val arguments: Iterable<String>) {
    val applicationPath: Path = Paths.get("build/install/decompose-kt/bin/decompose-kt").toAbsolutePath()
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
