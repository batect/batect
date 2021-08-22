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

import batect.cli.options.EnumValueConverter
import batect.cli.options.OptionDefinition
import batect.cli.options.ValueOption
import java.io.InputStreamReader

class BashShellTabCompletionScriptGenerator : ShellTabCompletionScriptGenerator {
    override fun generate(options: Set<OptionDefinition>, registerAs: String): String {
        val singleUseOptionLines = options
            .filterNot { it.allowMultiple }
            .joinToString("\n") { """PLACEHOLDER_REGISTER_AS_add_single_use_option_suggestion "${it.longOption}" "${it.shortOption ?: ""}"""" }

        val multipleValueOptionNames = options
            .filter { it.allowMultiple }
            .flatMap { it.optionNames }
            .joinToString(" ")

        val optionsThatRequireValues = options
            .filter { it.acceptsValue }
            .flatMap { it.optionNames }
            .joinToString(" ")

        val enumOptionValueLines = generateEnumOptionValueLines(options)

        val classLoader = javaClass.classLoader
        classLoader.getResourceAsStream("batect/completion.bash").use { stream ->
            InputStreamReader(stream!!, Charsets.UTF_8).use {
                return it.readText()
                    .replace("PLACEHOLDER_ADD_SINGLE_USE_OPTIONS", singleUseOptionLines)
                    .replace("PLACEHOLDER_MULTIPLE_VALUE_OPTION_NAMES", multipleValueOptionNames)
                    .replace("PLACEHOLDER_OPTIONS_THAT_REQUIRE_VALUES", optionsThatRequireValues)
                    .replace("# PLACEHOLDER_ADD_ENUM_VALUES", enumOptionValueLines)
                    .replace("PLACEHOLDER_REGISTER_AS", registerAs)
            }
        }
    }

    private fun generateEnumOptionValueLines(options: Set<OptionDefinition>): String = options
        .filterIsInstance<ValueOption<*, *>>()
        .filter { it.valueConverter is EnumValueConverter<*> }
        .joinToString("\n") { generateEnumOptionValueLine(it) }

    private fun generateEnumOptionValueLine(option: ValueOption<*, *>): String {
        val valueConverter = option.valueConverter as EnumValueConverter<*>
        val values = valueConverter.possibleValues.keys.joinToString(" ")
        val optionNames = if (option.shortOption != null) "${option.longOption}|${option.shortOption}" else option.longOption

        return """$optionNames) COMPREPLY+=(${'$'}(compgen -W "$values" -- "${'$'}option_value")) ;;"""
    }

    private val OptionDefinition.optionNames: Set<String>
        get() = if (shortOption != null) setOf(shortOption, longOption) else setOf(longOption)
}
