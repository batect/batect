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

abstract class OptionDefinition(
    val group: OptionGroup,
    val longName: String,
    val description: String,
    val acceptsValue: Boolean,
    val shortName: Char? = null,
    val allowMultiple: Boolean = false,
    val showInHelp: Boolean = true
) {
    private var alreadySeen: Boolean = false
    abstract val valueSource: OptionValueSource

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

        if (alreadySeen && !allowMultiple) {
            return specifiedMultipleTimesError()
        }

        val arg = args.first()
        val argName = arg.substringBefore("=")

        if (!nameMatches(argName)) {
            throw IllegalArgumentException("Next argument in list of arguments is not for this option.")
        }

        alreadySeen = true

        return parseValue(args)
    }

    private fun specifiedMultipleTimesError(): OptionParsingResult {
        val shortOptionHint = if (shortName != null) " (or '$shortOption')" else ""
        return OptionParsingResult.InvalidOption("Option '$longOption'$shortOptionHint cannot be specified multiple times.")
    }

    private fun nameMatches(nameFromArgument: String): Boolean {
        return nameFromArgument == longOption || nameFromArgument == shortOption
    }

    internal abstract fun parseValue(args: Iterable<String>): OptionParsingResult
    internal abstract fun checkDefaultValue(): DefaultApplicationResult

    open val descriptionForHelp: String = description
    open val valueFormatForHelp: String = "value"
}

sealed class DefaultApplicationResult {
    object Succeeded : DefaultApplicationResult()
    data class Failed(val message: String) : DefaultApplicationResult()
}
