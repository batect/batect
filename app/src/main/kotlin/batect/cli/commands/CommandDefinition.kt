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

package batect.cli.commands

import batect.cli.CommandLineParsingResult
import batect.cli.options.OptionParser
import batect.cli.options.OptionParserContainer
import batect.cli.options.OptionsParsingResult
import com.github.salomonbrys.kodein.Kodein

abstract class CommandDefinition(val commandName: String, val description: String, override val optionParser: OptionParser, val aliases: Set<String> = emptySet()) : OptionParserContainer {
    val requiredPositionalParameters = mutableListOf<RequiredPositionalParameter>()
    val optionalPositionalParameters = mutableListOf<OptionalPositionalParameter>()

    constructor(commandName: String, description: String, aliases: Set<String> = emptySet()) :
            this(commandName, description, OptionParser(), aliases)

    init {
        if (commandName == "") {
            throw IllegalArgumentException("Command name must not be empty.")
        }

        if (description == "") {
            throw IllegalArgumentException("Command description must not be empty.")
        }
    }

    open fun parse(args: Iterable<String>, kodein: Kodein): CommandLineParsingResult {
        val optionParsingResult = optionParser.parseOptions(args)

        return when (optionParsingResult) {
            is OptionsParsingResult.InvalidOptions -> CommandLineParsingResult.Failed(optionParsingResult.message)
            is OptionsParsingResult.ReadOptions -> parseParameters(args.drop(optionParsingResult.argumentsConsumed), kodein)
        }
    }

    private fun parseParameters(args: Iterable<String>, kodein: Kodein): CommandLineParsingResult {
        if (args.count() < requiredPositionalParameters.count()) {
            val firstMissingParam = requiredPositionalParameters.drop(args.count()).first()
            val noun = if (requiredPositionalParameters.count() == 1) "parameter" else "parameters"

            return CommandLineParsingResult.Failed("Command '$commandName' requires at least ${requiredPositionalParameters.count()} $noun. The parameter '${firstMissingParam.name}' was not supplied.")
        }

        val maxParameters = requiredPositionalParameters.count() + optionalPositionalParameters.count()

        if (args.count() > maxParameters) {
            val firstInvalid = args.drop(maxParameters).first()
            val message = when (maxParameters) {
                0 -> "does not take any parameters"
                1 -> "takes at most 1 parameter"
                else -> "takes at most $maxParameters parameters"
            }

            return CommandLineParsingResult.Failed("Command '$commandName' $message. ('$firstInvalid' is the first extra parameter.)")
        }

        val requiredParameterValues = args.take(requiredPositionalParameters.count())
        requiredParameterValues.zip(requiredPositionalParameters).forEach { (value, param) -> param.value = value }

        val optionalParameterValues = args.drop(requiredPositionalParameters.count())
        optionalParameterValues.zip(optionalPositionalParameters).forEach { (arg, param) -> param.value = arg }
        optionalPositionalParameters.drop(optionalParameterValues.count()).forEach { it.value = null }

        return CommandLineParsingResult.Succeeded(this.createCommand(kodein))
    }

    abstract fun createCommand(kodein: Kodein): Command

    fun getAllPositionalParameterDefinitions(): List<PositionalParameterDefinition> = requiredPositionalParameters + optionalPositionalParameters
}

interface Command {
    fun run(): Int
}
