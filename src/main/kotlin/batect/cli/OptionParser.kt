package batect.cli

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
}

sealed class OptionsParsingResult
data class ReadOptions(val argumentsConsumed: Int) : OptionsParsingResult()
data class InvalidOptions(val message: String) : OptionsParsingResult()

sealed class OptionParsingResult {
    data class ReadOption(val argumentsConsumed: Int) : OptionParsingResult()
    data class InvalidOption(val message: String) : OptionParsingResult()
    object NoOption : OptionParsingResult()
}
