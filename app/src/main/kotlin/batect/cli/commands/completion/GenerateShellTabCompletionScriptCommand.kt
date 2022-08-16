/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.cli.commands.completion

import batect.cli.CommandLineOptions
import batect.cli.CommandLineOptionsParser
import batect.cli.commands.Command
import batect.os.HostEnvironmentVariables
import batect.primitives.filterToSet
import batect.telemetry.AttributeValue
import batect.telemetry.TelemetryCaptor
import java.io.PrintStream

// This class is tested primarily by the tests in the app/src/completionTest directory.
class GenerateShellTabCompletionScriptCommand(
    private val commandLineOptions: CommandLineOptions,
    private val optionsParser: CommandLineOptionsParser,
    private val bashGenerator: BashShellTabCompletionScriptGenerator,
    private val fishGenerator: FishShellTabCompletionScriptGenerator,
    private val zshGenerator: ZshShellTabCompletionScriptGenerator,
    private val outputStream: PrintStream,
    private val environmentVariables: HostEnvironmentVariables,
    private val telemetryCaptor: TelemetryCaptor
) : Command {
    override fun run(): Int {
        addTelemetryEvent()
        emitCompletionScript()

        return 0
    }

    private fun addTelemetryEvent() {
        telemetryCaptor.addEvent(
            "GeneratedShellTabCompletionScript",
            mapOf(
                "shell" to AttributeValue(commandLineOptions.generateShellTabCompletionScript?.toString()),
                "proxyCompletionScriptVersion" to AttributeValue(environmentVariables["BATECT_COMPLETION_PROXY_VERSION"])
            )
        )
    }

    private fun emitCompletionScript() {
        val generator = getGeneratorForShell()
        val script = generator.generate(findOptionsToInclude(), getRegisterAs())

        outputStream.print(script)
        outputStream.flush()
    }

    private fun findOptionsToInclude() = optionsParser.optionParser.getOptions()
        .filterToSet { it.showInHelp }

    private fun getRegisterAs() =
        environmentVariables["BATECT_COMPLETION_PROXY_REGISTER_AS"]
            ?: throw IllegalArgumentException("'BATECT_COMPLETION_PROXY_REGISTER_AS' environment variable not set.")

    private fun getGeneratorForShell(): ShellTabCompletionScriptGenerator = when (commandLineOptions.generateShellTabCompletionScript) {
        Shell.Bash -> bashGenerator
        Shell.Fish -> fishGenerator
        Shell.Zsh -> zshGenerator
        else -> throw IllegalArgumentException("Can't generate completions for shell ${commandLineOptions.generateShellTabCompletionScript}")
    }
}
