package decompose.cli

import java.io.PrintStream

class HelpCommand(private val parser: CommandLineParser, private val outputStream: PrintStream) : CommandLineCommand("help", "Print this help information and exit.", aliases = setOf("--help")) {
    val showHelpForCommandName: String? by PositionalParameter("command")

    override fun run(): Int {
        if (showHelpForCommandName == null) {
            parser.printHelp(outputStream)
        } else {
            val command = parser.getCommandByName(showHelpForCommandName!!)

            when (command) {
                null -> outputStream.println("Invalid command '$showHelpForCommandName'. Run '$applicationName help' for a list of valid commands.")
                else -> command.printHelp(outputStream)
            }
        }

        return 1
    }
}
