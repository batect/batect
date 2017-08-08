package decompose.cli

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import decompose.PrintStreamType
import java.io.PrintStream

class HelpCommandDefinition(val parser: CommandLineParser) : CommandDefinition("help", "Display information about available commands and options.", aliases = setOf("--help")) {
    val showHelpForCommandName: String? by OptionalPositionalParameter("COMMAND", "Command to display help for. If no command specified, display overview of all available commands.")

    override fun createCommand(kodein: Kodein): Command = HelpCommand(showHelpForCommandName, parser, kodein.instance(PrintStreamType.Error))
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
        val commands = parser.getAllCommandDefinitions().sortedBy { it.commandName }.associate { it.commandName to it.description }
        val options = parser.getCommonOptions().sortedBy { it.name }.associate { nameFor(it) to descriptionFor(it) }
        val alignToColumn = (commands.keys + options.keys).map { it.length }.max() ?: 0

        outputStream.print("Usage: $applicationName ")

        if (options.isNotEmpty()) {
            outputStream.print("[COMMON OPTIONS] ")
        }

        outputStream.println("COMMAND [COMMAND OPTIONS]")
        outputStream.println()

        outputStream.println("Commands:")
        printInColumns(commands, alignToColumn)
        outputStream.println()

        if (options.isNotEmpty()) {
            outputStream.println("Common options:")
            printInColumns(options, alignToColumn)
            outputStream.println()
        }

        outputStream.println("For help on the options available for a command, run '$applicationName help <command>'.")
        outputStream.println()
    }

    private fun nameFor(option: OptionDefinition): String {
        val longNamePart = "${option.longOption}=value"

        return when {
            option.shortName == null -> "    $longNamePart"
            else -> "${option.shortOption}, $longNamePart"
        }
    }

    private fun descriptionFor(option: OptionDefinition): String {
        return when (option) {
            is ValueOptionWithDefault -> "${option.description} (defaults to '${option.defaultValue}' if not set)"
            else -> option.description
        }
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

        outputStream.println()

        if (parser.getCommonOptions().isNotEmpty()) {
            outputStream.println("For help on the common options available for all commands, run '$applicationName help'.")
            outputStream.println()
        }
    }

    private fun printCommandHelpHeader(commandDefinition: CommandDefinition, positionalParameterDefinitions: List<PositionalParameterDefinition>) {
        outputStream.print("Usage: $applicationName ")

        if (parser.getCommonOptions().isNotEmpty()) {
            outputStream.print("[COMMON OPTIONS] ")
        }

        outputStream.print(commandDefinition.commandName)

        positionalParameterDefinitions.forEach {
            val formattedName = if (it.isOptional) "[${it.name}]" else it.name
            outputStream.print(" $formattedName")
        }

        outputStream.println()
    }

    private fun printCommandParameterInfo(positionalParameterDefinitions: List<PositionalParameterDefinition>) {
        outputStream.println("Parameters:")
        printInColumns(positionalParameterDefinitions.associate { it.name to descriptionForPositionalParameter(it) })
    }

    private fun descriptionForPositionalParameter(param: PositionalParameterDefinition): String {
        if (param.isOptional) {
            return "(optional) " + param.description
        } else {
            return param.description
        }
    }

    private fun printInColumns(items: Map<String, String>, alignToColumn: Int = items.keys.map { it.length }.max() ?: 0) {
        items.forEach { firstColumn, secondColumn ->
            val indentationCount = 4 + alignToColumn - firstColumn.length
            val indentation = " ".repeat(indentationCount)
            outputStream.println("  $firstColumn$indentation$secondColumn")
        }
    }
}
