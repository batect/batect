package decompose.cli

import com.github.salomonbrys.kodein.Kodein

open class CommandLineParser(private val kodein: Kodein, override val optionParser: OptionParser) : OptionParserContainer {
    private val helpCommand = HelpCommandDefinition(this)

    private val commandDefinitions = mutableSetOf<CommandDefinition>()
    private val commandAliases = mutableMapOf<String, CommandDefinition>()

    constructor(kodein: Kodein) : this(kodein, OptionParser())

    init {
        addCommandDefinition(helpCommand)
    }

    fun parse(args: Iterable<String>): CommandLineParsingResult {
        val optionParsingResult = optionParser.parseOptions(args)

        when (optionParsingResult) {
            is InvalidOptions -> return Failed(optionParsingResult.message)
            is ReadOptions -> {
                val remainingArgs = args.drop(optionParsingResult.argumentsConsumed)

                if (remainingArgs.isEmpty()) {
                    return noCommand()
                } else {
                    return parseAndRunCommand(remainingArgs.first(), remainingArgs.drop(1))
                }
            }
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

    fun getCommonOptions(): Set<ValueOption> = optionParser.getOptions()

    open fun createBindings(): Kodein.Module = Kodein.Module {}
}

sealed class CommandLineParsingResult
data class Succeeded(val command: Command) : CommandLineParsingResult()
data class Failed(val error: String) : CommandLineParsingResult()

val applicationName = "decompose"
