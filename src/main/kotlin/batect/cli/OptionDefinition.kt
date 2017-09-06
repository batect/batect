package batect.cli

abstract class OptionDefinition(val longName: String,
                                val description: String,
                                val shortName: Char? = null) {
    private var valueHasBeenSet: Boolean = false

    val longOption = "--$longName"
    val shortOption = if (shortName != null) "-$shortName" else null

    init {
        if (longName == "") {
            throw IllegalArgumentException("Option long name must not be empty.")
        }

        if (longName.startsWith("-")) {
            throw IllegalArgumentException("Option long name must not start with a dash.")
        }

        if (longName.length < 2) {
            throw IllegalArgumentException("Option long name must be at least two characters long.")
        }

        if (description == "") {
            throw IllegalArgumentException("Option description must not be empty.")
        }

        if (shortName != null && !shortName.isLetterOrDigit()) {
            throw IllegalArgumentException("Option short name must be alphanumeric.")
        }
    }

    fun parse(args: Iterable<String>): OptionParsingResult {
        if (args.none()) {
            throw IllegalArgumentException("List of arguments cannot be empty.")
        }

        if (valueHasBeenSet) {
            val shortOptionHint = if (shortName != null) " (or '$shortOption')" else ""

            return OptionParsingResult.InvalidOption("Option '$longOption'$shortOptionHint cannot be specified multiple times.")
        }

        val arg = args.first()
        val argName = arg.substringBefore("=")

        if (!nameMatches(argName)) {
            throw IllegalArgumentException("Next argument in list of arguments is not for this option.")
        }

        val useNextArgumentForValue = !arg.contains("=")

        val argValue = if (useNextArgumentForValue) {
            if (args.count() == 1) return OptionParsingResult.InvalidOption("Option '$arg' requires a value to be provided, either in the form '$argName=<value>' or '$argName <value>'.")
            args.elementAt(1)
        } else {
            val value = arg.drop(2).substringAfter("=", "")
            if (value == "") return OptionParsingResult.InvalidOption("Option '$arg' is in an invalid format, you must provide a value after '='.")
            value
        }

        applyValue(argValue)
        valueHasBeenSet = true

        if (useNextArgumentForValue) {
            return OptionParsingResult.ReadOption(2)
        } else {
            return OptionParsingResult.ReadOption(1)
        }
    }

    private fun nameMatches(nameFromArgument: String): Boolean {
        return nameFromArgument == longOption || nameFromArgument == shortOption
    }

    internal abstract fun applyValue(newValue: String)
}
