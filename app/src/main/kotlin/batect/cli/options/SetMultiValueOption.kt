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

package batect.cli.options

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class SetMultiValueOption(
    group: OptionGroup,
    longName: String,
    description: String,
    shortName: Char? = null,
    override val valueFormatForHelp: String = "<name>=<value1,value2,value3>"
) : OptionDefinition(group, longName, description, true, shortName, true), ReadOnlyProperty<OptionParserContainer, List<String>> {
    private val values: MutableList<String> = mutableListOf()
    override var valueSource: OptionValueSource = OptionValueSource.Default
        private set

    override fun parseValue(args: Iterable<String>): OptionParsingResult {
        val arg = args.first()
        val argName = arg.substringBefore("=")
        val useNextArgumentForValue = !arg.contains("=")

        val argValue = if (useNextArgumentForValue) {
            if (args.count() == 1) return OptionParsingResult.InvalidOption("Option '$arg' requires a value to be provided, either in the form '$argName=<value1, value2, value3, ...>' or '$argName <value1, value2, value3, ...>'.")
            args.elementAt(1)
        } else {
            val value = arg.drop(2).substringAfter("=", "")
            if (value == "") return OptionParsingResult.InvalidOption("Option '$arg' is in an invalid format, you must provide a value after '='.")
            value
        }

        val match = argValue.split(",")

        if (match.isEmpty()) {
            return OptionParsingResult.InvalidOption("Option '$argName' requires a value to be provided, either in the form '$argName=<value1, value2, value3 ...>' or '$argName <value1, value2, value3 ...>'.")
        }

        if (match.distinct().size != match.size) {
            return OptionParsingResult.InvalidOption("Option '$argName' does not allow duplicate values in the list.")
        }

        match.forEach { values.add(it.trim()) }
        valueSource = OptionValueSource.CommandLine

        return if (useNextArgumentForValue) {
            OptionParsingResult.ReadOption(2)
        } else {
            OptionParsingResult.ReadOption(1)
        }
    }

    operator fun provideDelegate(thisRef: OptionParserContainer, property: KProperty<*>): SetMultiValueOption {
        thisRef.optionParser.addOption(this)
        return this
    }

    override fun getValue(thisRef: OptionParserContainer, property: KProperty<*>): List<String> = values
    override fun checkDefaultValue(): DefaultApplicationResult = DefaultApplicationResult.Succeeded
    override val descriptionForHelp: String = "${super.descriptionForHelp} Can be provided multiple values."

}
