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

import batect.cli.options.defaultvalues.DefaultValueProvider
import batect.cli.options.defaultvalues.StandardFlagOptionDefaultValueProvider
import batect.cli.options.defaultvalues.StaticDefaultValueProvider

class OptionParser {
    private val options = mutableSetOf<OptionDefinition>()
    private val optionNames = mutableMapOf<String, OptionDefinition>()

    fun parseOptions(args: Iterable<String>): OptionsParsingResult {
        var argIndex = 0

        while (argIndex < args.count()) {
            when (val optionParsingResult = parseOption(args, argIndex)) {
                is OptionParsingResult.ReadOption -> argIndex += optionParsingResult.argumentsConsumed
                is OptionParsingResult.InvalidOption -> return OptionsParsingResult.InvalidOptions(optionParsingResult.message)
                is OptionParsingResult.NoOption -> return checkDefaults(argIndex)
            }
        }

        return checkDefaults(argIndex)
    }

    private fun parseOption(args: Iterable<String>, currentIndex: Int): OptionParsingResult {
        val arg = args.elementAt(currentIndex)
        val argName = arg.substringBefore("=")
        val option = optionNames[argName]

        if (option == null) {
            if (argName.startsWith("-")) {
                return OptionParsingResult.InvalidOption("Invalid option '$argName'. Run './batect --help' for a list of valid options.")
            } else {
                return OptionParsingResult.NoOption
            }
        }

        return option.parse(args.drop(currentIndex))
    }

    private fun checkDefaults(argumentsConsumed: Int): OptionsParsingResult {
        options.forEach {
            when (val result = it.checkDefaultValue()) {
                is DefaultApplicationResult.Failed -> return OptionsParsingResult.InvalidOptions("The default value for the ${it.longOption} option is invalid: ${result.message}")
            }
        }

        return OptionsParsingResult.ReadOptions(argumentsConsumed)
    }

    fun addOption(option: OptionDefinition) {
        if (optionNames.containsKey(option.longOption)) {
            throw IllegalArgumentException("An option with the name '${option.longName}' has already been added.")
        }

        if (option.shortOption != null && optionNames.containsKey(option.shortOption)) {
            throw IllegalArgumentException("An option with the name '${option.shortName}' has already been added.")
        }

        options.add(option)
        optionNames[option.longOption] = option

        if (option.shortOption != null) {
            optionNames[option.shortOption] = option
        }
    }

    fun getOptions(): Set<OptionDefinition> = options
}

interface OptionParserContainer {
    val optionParser: OptionParser

    fun valueOption(group: OptionGroup, longName: String, description: String, shortName: Char? = null) =
        valueOption(group, longName, description, ValueConverters.string, shortName)

    fun <V> valueOption(group: OptionGroup, longName: String, description: String, valueConverter: ValueConverter<V>, shortName: Char? = null, showInHelp: Boolean = true) =
        valueOption(group, longName, description, StaticDefaultValueProvider<V?>(null), valueConverter, shortName, showInHelp)

    fun valueOption(group: OptionGroup, longName: String, description: String, defaultValue: String, shortName: Char? = null) =
        valueOption(group, longName, description, defaultValue, ValueConverters.string, shortName)

    fun <StorageType, ValueType : StorageType> valueOption(group: OptionGroup, longName: String, description: String, defaultValue: StorageType, valueConverter: ValueConverter<ValueType>, shortName: Char? = null, showInHelp: Boolean = true) =
        valueOption(group, longName, description, StaticDefaultValueProvider(defaultValue), valueConverter, shortName, showInHelp)

    fun valueOption(group: OptionGroup, longName: String, description: String, defaultValueProvider: DefaultValueProvider<String>, shortName: Char? = null) =
        valueOption(group, longName, description, defaultValueProvider, ValueConverters.string, shortName)

    fun <StorageType, ValueType : StorageType> valueOption(group: OptionGroup, longName: String, description: String, defaultValueProvider: DefaultValueProvider<StorageType>, valueConverter: ValueConverter<ValueType>, shortName: Char? = null, showInHelp: Boolean = true) =
        ValueOption(group, longName, description, defaultValueProvider, valueConverter, shortName, showInHelp)

    fun flagOption(group: OptionGroup, longName: String, description: String, shortName: Char? = null) =
        flagOption(group, longName, description, StandardFlagOptionDefaultValueProvider, shortName)

    fun flagOption(group: OptionGroup, longName: String, description: String, defaultValueProvider: DefaultValueProvider<Boolean>, shortName: Char? = null) =
        FlagOption(group, longName, description, defaultValueProvider, shortName)

    fun tristateFlagOption(group: OptionGroup, longName: String, description: String, defaultValueProvider: DefaultValueProvider<Boolean?>, shortName: Char? = null) =
        TristateFlagOption(group, longName, description, defaultValueProvider, shortName)

    fun singleValueMapOption(group: OptionGroup, longName: String, description: String, shortName: Char? = null) = SingleValueMapOption(group, longName, description, shortName)
    fun singleValueMapOption(group: OptionGroup, longName: String, description: String, valueFormatForHelp: String, shortName: Char? = null) = SingleValueMapOption(group, longName, description, shortName, valueFormatForHelp)
}

sealed class OptionsParsingResult {
    data class ReadOptions(val argumentsConsumed: Int) : OptionsParsingResult()
    data class InvalidOptions(val message: String) : OptionsParsingResult()
}

sealed class OptionParsingResult {
    data class ReadOption(val argumentsConsumed: Int) : OptionParsingResult()
    data class InvalidOption(val message: String) : OptionParsingResult()
    object NoOption : OptionParsingResult()
}
