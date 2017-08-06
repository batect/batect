package decompose.cli

class OptionParser {
    private val options = mutableSetOf<OptionalOption>()
    private val optionNames = mutableMapOf<String, OptionalOption>()

    fun parseOptions(args: Iterable<String>): OptionsParsingResult {
        options.forEach { it.value = null }

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

        if (!arg.startsWith("--")) {
            return OptionParsingResult.NoOption
        }

        val argName = arg.drop(2).substringBefore("=")
        val option = optionNames[argName]

        if (option == null) {
            return OptionParsingResult.NoOption
        }

        if (option.value != null) {
            return OptionParsingResult.InvalidOption("Option '--$argName' cannot be specified multiple times.")
        }

        val useNextArgumentForValue = !arg.contains("=")

        val argValue = if (useNextArgumentForValue) {
            if (currentIndex == args.count() - 1) return OptionParsingResult.InvalidOption("Option '$arg' requires a value to be provided, either in the form '--$argName=<value>' or '--$argName <value>'.")
            args.elementAt(currentIndex + 1)
        } else {
            val value = arg.drop(2).substringAfter("=", "")
            if (value == "") return OptionParsingResult.InvalidOption("Option '$arg' is in an invalid format, you must provide a value after '='.")
            value
        }

        option.value = argValue

        if (useNextArgumentForValue) {
            return OptionParsingResult.ReadOption(2)
        } else {
            return OptionParsingResult.ReadOption(1)
        }
    }

    fun addOptionalOption(option: OptionalOption) {
        options.add(option)
        optionNames[option.name] = option
    }

    fun getOptions(): Set<OptionalOption> = options

    sealed class OptionParsingResult {
        data class ReadOption(val argumentsConsumed: Int) : OptionParsingResult()
        data class InvalidOption(val message: String) : OptionParsingResult()
        object NoOption : OptionParsingResult()
    }
}

interface OptionParserContainer {
    val optionParser: OptionParser
}

sealed class OptionsParsingResult
data class ReadOptions(val argumentsConsumed: Int) : OptionsParsingResult()
data class InvalidOptions(val message: String) : OptionsParsingResult()
