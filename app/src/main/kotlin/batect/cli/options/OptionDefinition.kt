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
            return specifiedMultipleTimesError()
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

        valueHasBeenSet = true
        val applicationResult = applyValue(argValue)

        return when (applicationResult) {
            is InvalidValue -> OptionParsingResult.InvalidOption("The value '$argValue' for option '$arg' is invalid: ${applicationResult.message}")
            is ValidValue -> if (useNextArgumentForValue) {
                OptionParsingResult.ReadOption(2)
            } else {
                OptionParsingResult.ReadOption(1)
            }
        }
    }

    private fun specifiedMultipleTimesError(): OptionParsingResult {
        val shortOptionHint = if (shortName != null) " (or '$shortOption')" else ""
        return OptionParsingResult.InvalidOption("Option '$longOption'$shortOptionHint cannot be specified multiple times.")
    }

    private fun nameMatches(nameFromArgument: String): Boolean {
        return nameFromArgument == longOption || nameFromArgument == shortOption
    }

    internal abstract fun applyValue(newValue: String): ValueApplicationResult

    open val descriptionForHelp: String = description
}

sealed class ValueApplicationResult
data class InvalidValue(val message: String) : ValueApplicationResult()
object ValidValue : ValueApplicationResult()
