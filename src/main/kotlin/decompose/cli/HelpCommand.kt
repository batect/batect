package decompose.cli

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import decompose.PrintStreamType
import java.io.PrintStream

class HelpCommandDefinition : CommandDefinition("help", "Print this help information and exit.", aliases = setOf("--help")) {
    val showHelpForCommandName: String? by PositionalParameter("command")

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
        outputStream.println("Usage: $applicationName [COMMON OPTIONS] ${commandDefinition.commandName}")
        outputStream.println()
        outputStream.println(commandDefinition.description)
        outputStream.println()
        outputStream.println("This command does not take any options.")
    }
}
