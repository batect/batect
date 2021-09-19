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

import batect.cli.options.OptionDefinition
import java.io.InputStreamReader

// This class is tested primarily by the tests in the app/src/completionTest directory.
class FishShellTabCompletionScriptGenerator(
    private val lineGenerator: FishShellTabCompletionLineGenerator
) : ShellTabCompletionScriptGenerator {
    override fun generate(options: Set<OptionDefinition>, registerAs: String): String {
        val builder = StringBuilder()

        emitTaskNameHandler(registerAs, builder)
        emitOptions(options, registerAs, builder)

        return builder.toString()
    }

    private fun emitTaskNameHandler(registerAs: String, builder: java.lang.StringBuilder) {
        val classLoader = javaClass.classLoader
        classLoader.getResourceAsStream("batect/completion.fish").use { stream ->
            InputStreamReader(stream!!, Charsets.UTF_8).use {
                builder.append(it.readText().replace("PLACEHOLDER_REGISTER_AS", registerAs))
            }
        }
    }

    private fun emitOptions(options: Set<OptionDefinition>, registerAs: String, builder: java.lang.StringBuilder) {
        options.forEach { builder.appendLine(lineGenerator.generate(it, registerAs)) }
    }
}
