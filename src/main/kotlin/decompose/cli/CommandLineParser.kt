package decompose.cli

import java.io.PrintStream

class CommandLineParser(private val errorOutputStream: PrintStream) {
    private val helpCommand = HelpCommand(this, errorOutputStream)

    private val commands = mutableSetOf<CommandLineCommand>()
    private val commandAliases = mutableMapOf<String, CommandLineCommand>()

    init {
        addCommand(helpCommand)
    }

    fun parse(args: Iterable<String>) = parse(args, errorOutputStream)

    private fun parse(args: Iterable<String>, errorOutputStream: PrintStream): CommandLineParsingResult {
        if (args.count() == 0) {
            printNoCommand(errorOutputStream)
            return Failed
        }

        val command: CommandLineCommand? = commandAliases.get(args.first())

        if (command == null) {
            printInvalidArg(args.first(), errorOutputStream)
            return Failed
        }

        return command.parse(args.drop(1), errorOutputStream)
    }

    fun printHelp(outputStream: PrintStream) {
        outputStream.println("Usage: $applicationName [COMMON OPTIONS] COMMAND [COMMAND OPTIONS]")
        outputStream.println()
        outputStream.println("Commands:")

        val alignToColumn = 4 + (commands.map { it.commandName.length }.max() ?: 0)

        commands.sortedBy { it.commandName }.forEach {
            val indentationCount = alignToColumn - it.commandName.length
            val indentation = " ".repeat(indentationCount)
            outputStream.println("  ${it.commandName}$indentation${it.description}")
        }

        outputStream.println()
        outputStream.println("For help on the options available for a command, run '$applicationName help <command>'.")
    }

    private fun printNoCommand(errorOutputStream: PrintStream) {
        errorOutputStream.println("No command specified. Run '$applicationName help' for a list of valid commands.")
    }

    protected fun printInvalidArg(arg: String, errorOutputStream: PrintStream) {
        val guessedType = if (arg.startsWith("-")) "option" else "command"

        errorOutputStream.println("Invalid $guessedType '$arg'. Run '$applicationName help' for a list of valid ${guessedType}s.")
    }

    fun addCommand(command: CommandLineCommand) {
        val aliases = command.aliases + command.commandName
        val duplicates = commandAliases.keys.intersect(aliases)

        if (duplicates.isNotEmpty()) {
            throw IllegalArgumentException("A command with the name or alias '${duplicates.first()}' is already registered.")
        }

        commands.add(command)
        aliases.forEach { commandAliases.put(it, command) }
    }

    fun getCommandByName(name: String): CommandLineCommand? = commandAliases[name]
}

sealed class CommandLineParsingResult
data class Succeeded(val command: CommandLineCommand) : CommandLineParsingResult()
object Failed : CommandLineParsingResult()

val applicationName = "decompose"
