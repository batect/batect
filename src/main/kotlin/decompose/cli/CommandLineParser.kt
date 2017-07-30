package decompose.cli

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance

class CommandLineParser(private val kodein: Kodein) {
    private val helpCommand = HelpCommandDefinition()

    private val commandDefinitions = mutableSetOf<CommandDefinition>()
    private val commandAliases = mutableMapOf<String, CommandDefinition>()

    init {
        addCommandDefinition(helpCommand)
    }

    fun parse(args: Iterable<String>): CommandLineParsingResult {
        if (args.count() == 0) {
            return noCommand()
        }

        val command: CommandDefinition = commandAliases[args.first()] ?: return invalidArg(args.first())

        val extendedKodein = Kodein(allowSilentOverride = true) {
            extend(kodein)

            bind<CommandLineParser>() with instance(this@CommandLineParser)
        }

        return command.parse(args.drop(1), extendedKodein)
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
}

sealed class CommandLineParsingResult
data class Succeeded(val command: Command) : CommandLineParsingResult()
data class Failed(val error: String) : CommandLineParsingResult()

val applicationName = "decompose"
