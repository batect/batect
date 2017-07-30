package decompose.cli

import java.io.PrintStream

class HelpCommandDefinition(private val parser: CommandLineParser, private val outputStream: PrintStream) : CommandDefinition("help", "Print this help information and exit.", aliases = setOf("--help")) {
    val showHelpForCommandName: String? by PositionalParameter("command")

    override fun createCommand(): Command = HelpCommand(showHelpForCommandName, parser, outputStream)
}

class HelpCommand(val commandName: String?, val parser: CommandLineParser, val outputStream: PrintStream) : Command {
    override fun run(): Int {
        if (commandName == null) {
            parser.printHelp(outputStream)
        } else {
            val command = parser.getCommandByName(commandName)

            when (command) {
                null -> outputStream.println("Invalid command '$commandName'. Run '$applicationName help' for a list of valid commands.")
                else -> command.printHelp(outputStream)
            }
        }

        return 1
    }
}
