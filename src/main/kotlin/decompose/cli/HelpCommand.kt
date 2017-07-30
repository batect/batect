package decompose.cli

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import decompose.PrintStreamType
import java.io.PrintStream

class HelpCommandDefinition : CommandDefinition("help", "Display information about available commands and options.", aliases = setOf("--help")) {
    val showHelpForCommandName: String? by OptionalPositionalParameter("COMMAND", "Command to display help for. If no command specified, display overview of all available commands.")

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
        printInColumns(parser.getAllCommandDefinitions().sortedBy { it.commandName }.associate { "  " + it.commandName to it.description })
        outputStream.println()
        outputStream.println("For help on the options available for a command, run '$applicationName help <command>'.")
    }

    private fun printCommandHelp(commandDefinition: CommandDefinition) {
        val positionalParameterDefinitions = commandDefinition.getAllPositionalParameterDefinitions()

        printCommandHelpHeader(commandDefinition, positionalParameterDefinitions)

        outputStream.println()
        outputStream.println(commandDefinition.description)
        outputStream.println()

        if (positionalParameterDefinitions.isEmpty()) {
            outputStream.println("This command does not take any options.")
        } else {
            printCommandParameterInfo(positionalParameterDefinitions)
        }
    }

    private fun printCommandHelpHeader(commandDefinition: CommandDefinition, positionalParameterDefinitions: List<PositionalParameterDefinition>) {
        outputStream.print("Usage: $applicationName [COMMON OPTIONS] ${commandDefinition.commandName}")

        positionalParameterDefinitions.forEach {
            val formattedName = if (it.isOptional) "[${it.name}]" else it.name
            outputStream.print(" $formattedName")
        }

        outputStream.println()
    }

    private fun printCommandParameterInfo(positionalParameterDefinitions: List<PositionalParameterDefinition>) {
        outputStream.println("Parameters:")
        printInColumns(positionalParameterDefinitions.associate { "  " + it.name to descriptionForPositionalParameter(it) })
    }

    private fun descriptionForPositionalParameter(param: PositionalParameterDefinition): String {
        if (param.isOptional) {
            return "(optional) " + param.description
        } else {
            return param.description
        }
    }

    private fun printInColumns(items: Map<String, String>) {
        val alignToColumn = 4 + (items.keys.map { it.length }.max() ?: 0)

        items.forEach { firstColumn, secondColumn ->
            val indentationCount = alignToColumn - firstColumn.length
            val indentation = " ".repeat(indentationCount)
            outputStream.println("$firstColumn$indentation$secondColumn")
        }
    }
}
