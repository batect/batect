package decompose.cli

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import decompose.PrintStreamType
import java.io.PrintStream

class HelpCommandDefinition : CommandDefinition("help", "Display information about available commands and options.", aliases = setOf("--help")) {
    val showHelpForCommandName: String? by PositionalParameter("COMMAND", "Command to display help for. If no command specified, display overview of all available commands.")

    override fun createCommand(kodein: Kodein): Command = HelpCommand(showHelpForCommandName, kodein.instance(), kodein.instance(PrintStreamType.Error))
}

data class HelpCommand(val commandName: String?, val parser: CommandLineParser, val outputStream: PrintStream) : Command {
    override fun run(): Int {
        if (commandName == null) {
            printRootHelp()
        } else {
            val command = parser.getCommandDefinitionByName(commandName)

            when (command) {
                null -> outputStream.println("Invalid command '$commandName'. Run '$applicationName help' for a list of valid commands.")
                else -> printCommandHelp(command)
            }
        }

        return 1
    }

    private fun printRootHelp() {
        outputStream.println("Usage: $applicationName [COMMON OPTIONS] COMMAND [COMMAND OPTIONS]")
        outputStream.println()
        outputStream.println("Commands:")

        val alignToColumn = 4 + (parser.getAllCommandDefinitions().map { it.commandName.length }.max() ?: 0)

        parser.getAllCommandDefinitions().sortedBy { it.commandName }.forEach {
            val indentationCount = alignToColumn - it.commandName.length
            val indentation = " ".repeat(indentationCount)
            outputStream.println("  ${it.commandName}$indentation${it.description}")
        }

        outputStream.println()
        outputStream.println("For help on the options available for a command, run '$applicationName help <command>'.")
    }

    private fun printCommandHelp(commandDefinition: CommandDefinition) {
        outputStream.print("Usage: $applicationName [COMMON OPTIONS] ${commandDefinition.commandName}")

        commandDefinition.positionalParameters.forEach { outputStream.print(" [${it.name}]") }

        outputStream.println()
        outputStream.println()
        outputStream.println(commandDefinition.description)
        outputStream.println()

        if (commandDefinition.positionalParameters.isEmpty()) {
            outputStream.println("This command does not take any options.")
        } else {
            outputStream.println("Parameters:")

            commandDefinition.positionalParameters.forEach {
                outputStream.println("  ${it.name}    (optional) ${it.description}")
            }
        }
    }
}
