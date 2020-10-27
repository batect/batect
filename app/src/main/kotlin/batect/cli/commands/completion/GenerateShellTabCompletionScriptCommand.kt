/*
   Copyright 2017-2020 Charles Korn.

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
import batect.cli.CommandLineOptionsParser
import batect.cli.commands.Command
import batect.os.HostEnvironmentVariables
import batect.telemetry.AttributeValue
import batect.telemetry.TelemetrySessionBuilder
import java.io.InputStreamReader
import java.io.PrintStream

// This class is tested primarily by the tests in the app/src/completionTest directory.
class GenerateShellTabCompletionScriptCommand(
    private val commandLineOptions: CommandLineOptions,
    private val optionsParser: CommandLineOptionsParser,
    private val fishLineGenerator: FishShellTabCompletionLineGenerator,
    private val outputStream: PrintStream,
    private val environmentVariables: HostEnvironmentVariables,
    private val telemetrySessionBuilder: TelemetrySessionBuilder
) : Command {
    override fun run(): Int {
        if (commandLineOptions.generateShellTabCompletionScript != KnownShell.Fish) {
            throw IllegalArgumentException("Can only generate completions for Fish.")
        }

        addTelemetryEvent()
        emitCompletionScript()

        return 0
    }

    private fun addTelemetryEvent() {
        telemetrySessionBuilder.addEvent(
            "GeneratedShellTabCompletionScript",
            mapOf(
                "shell" to AttributeValue(commandLineOptions.generateShellTabCompletionScript.toString()),
                "proxyCompletionScriptVersion" to AttributeValue(environmentVariables["BATECT_COMPLETION_PROXY_VERSION"])
            )
        )
    }

    private fun emitCompletionScript() {
        val registerAs = environmentVariables["BATECT_COMPLETION_PROXY_REGISTER_AS"] ?: throw IllegalArgumentException("'BATECT_COMPLETION_PROXY_REGISTER_AS' environment variable not set.")

        emitTaskNameHandler(registerAs)
        emitOptions(registerAs)

        outputStream.flush()
    }

    private fun emitTaskNameHandler(registerAs: String) {
        val classLoader = javaClass.classLoader
        classLoader.getResourceAsStream("batect/completion.fish").use { stream ->
            InputStreamReader(stream!!, Charsets.UTF_8).use {
                outputStream.print(it.readText().replace("PLACEHOLDER_REGISTER_AS", registerAs))
            }
        }
    }

    private fun emitOptions(registerAs: String) {
        optionsParser.optionParser.getOptions()
            .filter { it.showInHelp }
            .forEach { outputStream.println(fishLineGenerator.generate(it, registerAs)) }
    }
}

enum class KnownShell {
    Fish
}
