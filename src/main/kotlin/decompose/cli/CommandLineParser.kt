package decompose.cli

import com.github.salomonbrys.kodein.Kodein

open class CommandLineParser(private val kodein: Kodein) {
    private val helpCommand = HelpCommandDefinition(this)

    private val commandDefinitions = mutableSetOf<CommandDefinition>()
    private val commandAliases = mutableMapOf<String, CommandDefinition>()

    private val options = mutableSetOf<OptionalOption>()
    private val optionNames = mutableMapOf<String, OptionalOption>()

    init {
        addCommandDefinition(helpCommand)
    }

    fun parse(args: Iterable<String>): CommandLineParsingResult {
        options.forEach { it.value = null }

        var argIndex = 0

        while (argIndex < args.count()) {
            val arg = args.elementAt(argIndex)
            val optionParsingResult = parseOption(args, argIndex)

            when (optionParsingResult) {
                is ReadOption -> argIndex += optionParsingResult.argumentsConsumed
                is InvalidOption -> return Failed(optionParsingResult.message)
                is NoOption -> return parseAndRunCommand(arg, args.drop(argIndex + 1))
            }
        }

        return noCommand()
    }

    private fun parseOption(args: Iterable<String>, currentIndex: Int): OptionParsingResult {
        val arg = args.elementAt(currentIndex)

        if (!arg.startsWith("--")) {
            return NoOption
        }

        val argName = arg.drop(2).substringBefore("=")
        val option = optionNames[argName]

        if (option == null) {
            return NoOption
        }

        if (option.value != null) {
            return InvalidOption("Option '--$argName' cannot be specified multiple times.")
        }

        val useNextArgumentForValue = !arg.contains("=")

        val argValue = if (useNextArgumentForValue) {
            if (currentIndex == args.count() - 1) return InvalidOption("Option '$arg' requires a value to be provided, either in the form '--$argName=<value>' or '--$argName <value>'.")
            args.elementAt(currentIndex + 1)
        } else {
            val value = arg.drop(2).substringAfter("=", "")
            if (value == "") return InvalidOption("Option '$arg' is in an invalid format, you must provide a value after '='.")
            value
        }

        option.value = argValue

        if (useNextArgumentForValue) {
            return ReadOption(2)
        } else {
            return ReadOption(1)
        }
    }

    private fun parseAndRunCommand(name: String, remainingArgs: Iterable<String>): CommandLineParsingResult {
        val command = commandAliases[name]

        if (command == null) {
            return invalidArg(name)
        }

        val extendedKodein = Kodein {
            extend(kodein)
            import(createBindings())
        }

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

    fun addOptionalOption(option: OptionalOption) {
        options.add(option)
        optionNames[option.name] = option
    }

    fun getAllCommonOptions(): Set<OptionalOption> = options

    open fun createBindings(): Kodein.Module = Kodein.Module {}
}

sealed class CommandLineParsingResult
data class Succeeded(val command: Command) : CommandLineParsingResult()
data class Failed(val error: String) : CommandLineParsingResult()

sealed class OptionParsingResult
data class ReadOption(val argumentsConsumed: Int) : OptionParsingResult()
data class InvalidOption(val message: String) : OptionParsingResult()
object NoOption : OptionParsingResult()

val applicationName = "decompose"
