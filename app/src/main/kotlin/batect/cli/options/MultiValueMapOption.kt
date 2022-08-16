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

package batect.cli.options

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class MultiValueMapOption(
    group: OptionGroup,
    longName: String,
    description: String,
    shortName: Char? = null,
    override val valueFormatForHelp: String = "<name>=<value>"
) : OptionDefinition(group, longName, description, true, shortName, true), ReadOnlyProperty<OptionParserContainer, Map<String, Set<String>>> {
    private val values: MutableMap<String, Set<String>> = mutableMapOf()

    override var valueSource: OptionValueSource = OptionValueSource.Default
        private set

    override fun parseValue(args: Iterable<String>): OptionParsingResult {
        val arg = args.first()
        val argName = arg.substringBefore("=")
        val useNextArgumentForValue = !arg.contains("=")

        val argValue = if (useNextArgumentForValue) {
            if (args.count() == 1) return OptionParsingResult.InvalidOption("Option '$arg' requires a value to be provided, either in the form '$argName=<key>=<value>' or '$argName <key>=<value>'.")
            args.elementAt(1)
        } else {
            val value = arg.drop(2).substringAfter("=", "")
            if (value == "") return OptionParsingResult.InvalidOption("Option '$arg' is in an invalid format, you must provide a value after '='.")
            value
        }

        val match = parsingRegex.matchEntire(argValue)

        if (match == null) {
            return OptionParsingResult.InvalidOption("Option '$argName' requires a value to be provided, either in the form '$argName=<key>=<value>' or '$argName <key>=<value>'.")
        }

        val key = match.groupValues[1]
        val value = match.groupValues[2]

        values[key] = values.getOrDefault(key, emptySet()) + value
        valueSource = OptionValueSource.CommandLine

        return if (useNextArgumentForValue) {
            OptionParsingResult.ReadOption(2)
        } else {
            OptionParsingResult.ReadOption(1)
        }
    }

    operator fun provideDelegate(thisRef: OptionParserContainer, property: KProperty<*>): MultiValueMapOption {
        thisRef.optionParser.addOption(this)
        return this
    }

    override fun getValue(thisRef: OptionParserContainer, property: KProperty<*>): Map<String, Set<String>> = values
    override fun checkDefaultValue(): DefaultApplicationResult = DefaultApplicationResult.Succeeded
    override val descriptionForHelp: String = "${super.descriptionForHelp} Can be given multiple times."

    companion object {
        private val parsingRegex = """(.+)=(.+)""".toRegex()
    }
}
