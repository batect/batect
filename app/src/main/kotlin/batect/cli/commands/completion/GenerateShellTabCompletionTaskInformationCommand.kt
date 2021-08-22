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

package batect.cli.commands.completion

import batect.cli.CommandLineOptions
import batect.cli.commands.Command
import batect.config.Task
import batect.config.includes.SilentGitRepositoryCacheNotificationListener
import batect.config.io.ConfigurationLoadResult
import batect.config.io.ConfigurationLoader
import batect.os.HostEnvironmentVariables
import batect.telemetry.AttributeValue
import batect.telemetry.TelemetrySessionBuilder
import okio.ByteString.Companion.toByteString
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class GenerateShellTabCompletionTaskInformationCommand(
    private val commandLineOptions: CommandLineOptions,
    private val outputStream: PrintStream,
    private val configurationLoader: ConfigurationLoader,
    private val telemetrySessionBuilder: TelemetrySessionBuilder,
    private val hostEnvironmentVariables: HostEnvironmentVariables
) : Command {
    override fun run(): Int {
        val loadResult = configurationLoader.loadConfig(commandLineOptions.configurationFileName, SilentGitRepositoryCacheNotificationListener)

        recordTelemetryEvent()
        generate(loadResult)

        return 0
    }

    private fun recordTelemetryEvent() {
        telemetrySessionBuilder.addEvent(
            "GeneratedShellTabCompletionTaskInformation",
            mapOf(
                "shell" to AttributeValue(commandLineOptions.generateShellTabCompletionTaskInformation.toString()),
                "proxyCompletionScriptVersion" to AttributeValue(hostEnvironmentVariables["BATECT_COMPLETION_PROXY_VERSION"])
            )
        )
    }

    private fun generate(loadResult: ConfigurationLoadResult) {
        outputStream.println("### FILES ###")
        loadResult.pathsLoaded.sorted().forEach { printPathLine(it) }

        outputStream.println("### TASKS ###")
        loadResult.configuration.tasks
            .sortedBy { it.name }
            .map { formatTask(it) }
            .forEach { outputStream.println(it) }

        outputStream.flush()
    }

    private fun formatTask(task: Task) = when (commandLineOptions.generateShellTabCompletionTaskInformation!!) {
        Shell.Zsh -> task.name.replace(":", "\\:") + formatTaskDescription(task.description, ":")
        Shell.Fish -> task.name + formatTaskDescription(task.description, "\t")
        Shell.Bash -> task.name
    }

    private fun formatTaskDescription(description: String, separator: String): String {
        if (description.isBlank()) {
            return ""
        }

        val cleanDescription = description
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace("\t", " ")

        return "$separator$cleanDescription"
    }

    // Why this format? It matches the format used by sha256sum / shasum, which means we can check all files in one go in the completion script.
    private fun printPathLine(path: Path) {
        val digestInstance = MessageDigest.getInstance("SHA-256")
        val data = Files.readAllBytes(path)
        val digest = digestInstance.digest(data).toByteString().hex()

        outputStream.print(digest)
        outputStream.print("  ")
        outputStream.println(path)
    }
}
