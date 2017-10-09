package batect.journeytests

import java.io.InputStreamReader

object DockerUtils {
    fun getAllCreatedContainers(): Set<String> {
        val commandLine = listOf("docker", "network", "ls", "--format", "{{.Name}} ({{.ID}})")
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

    fun getAllNetworks(): Set<String> {
        val commandLine = listOf("docker", "ps", "--all", "--format", "{{.Names}} ({{.ID}}): {{.Image}}")
        val process = ProcessBuilder(commandLine)
            .redirectErrorStream(true)
            .start()

        val exitCode = process.waitFor()
        val output = InputStreamReader(process.inputStream).readText()

        if (exitCode != 0) {
            throw Exception("Retrieving list of networks from Docker failed with exit code $exitCode. Output from Docker was: $output")
        }

        return output.split("\n").toSet()
    }
}
