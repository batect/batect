/*
   Copyright 2017 Charles Korn.

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

class OptionParser {
    private val options = mutableSetOf<OptionDefinition>()
    private val optionNames = mutableMapOf<String, OptionDefinition>()

    fun parseOptions(args: Iterable<String>): OptionsParsingResult {
        var argIndex = 0

        while (argIndex < args.count()) {
            val optionParsingResult = parseOption(args, argIndex)

            when (optionParsingResult) {
                is OptionParsingResult.ReadOption -> argIndex += optionParsingResult.argumentsConsumed
                is OptionParsingResult.InvalidOption -> return InvalidOptions(optionParsingResult.message)
                is OptionParsingResult.NoOption -> return ReadOptions(argIndex)
            }
        }

        return ReadOptions(argIndex)
    }

    private fun parseOption(args: Iterable<String>, currentIndex: Int): OptionParsingResult {
        val arg = args.elementAt(currentIndex)
        val argName = arg.substringBefore("=")
        val option = optionNames[argName]

        if (option == null) {
            return OptionParsingResult.NoOption
        }

        return option.parse(args.drop(currentIndex))
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

    fun valueOption(longName: String, description: String, shortName: Char? = null)
            = valueOption(longName, description, ValueConverters::string, shortName)

    fun <V> valueOption(longName: String, description: String, valueConverter: (String) -> ValueConversionResult<V>, shortName: Char? = null)
            = ValueOption(longName, description, StaticDefaultValueProvider<V?>(null), valueConverter, shortName)

    fun valueOption(longName: String, description: String, defaultValue: String, shortName: Char? = null)
            = valueOption(longName, description, defaultValue, ValueConverters::string, shortName)

    fun <V> valueOption(longName: String, description: String, defaultValue: V, valueConverter: (String) -> ValueConversionResult<V>, shortName: Char? = null)
            = ValueOption(longName, description, StaticDefaultValueProvider(defaultValue), valueConverter, shortName)

    fun valueOption(longName: String, description: String, defaultValueProvider: DefaultValueProvider<Int>, valueConverter: (String) -> ValueConversionResult<Int>, shortName: Char? = null)
            = ValueOption(longName, description, defaultValueProvider, valueConverter, shortName)

    fun flagOption(longName: String, description: String, shortName: Char? = null)
        = FlagOption(longName, description, shortName)
}

sealed class OptionsParsingResult
data class ReadOptions(val argumentsConsumed: Int) : OptionsParsingResult()
data class InvalidOptions(val message: String) : OptionsParsingResult()

sealed class OptionParsingResult {
    data class ReadOption(val argumentsConsumed: Int) : OptionParsingResult()
    data class InvalidOption(val message: String) : OptionParsingResult()
    object NoOption : OptionParsingResult()
}
