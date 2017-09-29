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

package batect.cli

import batect.cli.commands.Command
import batect.cli.commands.CommandDefinition
import batect.cli.commands.HelpCommandDefinition
import batect.cli.options.OptionDefinition
import batect.cli.options.OptionParser
import batect.cli.options.OptionParserContainer
import batect.cli.options.OptionsParsingResult
import com.github.salomonbrys.kodein.Kodein

open class CommandLineParser(
        private val kodein: Kodein,
        val applicationName: String,
        val helpBlurb: String,
        override val optionParser: OptionParser
) : OptionParserContainer {
    private val helpCommand = HelpCommandDefinition(this)

    private val commandDefinitions = mutableSetOf<CommandDefinition>()
    private val commandAliases = mutableMapOf<String, CommandDefinition>()

    constructor(kodein: Kodein, applicationName: String, helpBlurb: String)
        : this(kodein, applicationName, helpBlurb, OptionParser())

    init {
        addCommandDefinition(helpCommand)
    }

    fun parse(args: Iterable<String>, initializationAfterCommonOptionsParsed: (Kodein) -> Unit): CommandLineParsingResult {
        val optionParsingResult = optionParser.parseOptions(args)

        when (optionParsingResult) {
            is OptionsParsingResult.InvalidOptions -> return Failed(optionParsingResult.message)
            is OptionsParsingResult.ReadOptions -> {
                val remainingArgs = args.drop(optionParsingResult.argumentsConsumed)

                if (remainingArgs.isEmpty()) {
                    return noCommand()
                } else {
                    return parseAndRunCommand(remainingArgs.first(), remainingArgs.drop(1), initializationAfterCommonOptionsParsed)
                }
            }
        }
    }

    private fun parseAndRunCommand(name: String, remainingArgs: Iterable<String>, initializationAfterCommonOptionsParsed: (Kodein) -> Unit): CommandLineParsingResult {
        val command = commandAliases[name]

        if (command == null) {
            return invalidArg(name)
        }

        val extendedKodein = Kodein {
            extend(kodein)
            import(createBindings())
        }

        initializationAfterCommonOptionsParsed(extendedKodein)

        return command.parse(remainingArgs, extendedKodein)
    }

    private fun noCommand(): CommandLineParsingResult = Failed("No command specified. Run '$applicationName help' for a list of valid commands.")

    private fun invalidArg(arg: String): CommandLineParsingResult {
        val guessedType = if (arg.startsWith("-")) "option" else "command"

        return Failed("Invalid $guessedType '$arg'. Run '$applicationName help' for a list of valid ${guessedType}s.")
    }

    fun addCommandDefinition(command: CommandDefinition) {
        val aliases = command.aliases + command.commandName
        val duplicates = commandAliases.keys.intersect(aliases)

        if (duplicates.isNotEmpty()) {
            throw IllegalArgumentException("A command with the name or alias '${duplicates.first()}' is already registered.")
        }

        commandDefinitions.add(command)
        aliases.forEach { commandAliases.put(it, command) }
    }

    fun getAllCommandDefinitions(): Set<CommandDefinition> = commandDefinitions
    fun getCommandDefinitionByName(name: String): CommandDefinition? = commandAliases[name]

    fun getCommonOptions(): Set<OptionDefinition> = optionParser.getOptions()

    open fun createBindings(): Kodein.Module = Kodein.Module {}
}

sealed class CommandLineParsingResult
data class Succeeded(val command: Command) : CommandLineParsingResult()
data class Failed(val error: String) : CommandLineParsingResult()
