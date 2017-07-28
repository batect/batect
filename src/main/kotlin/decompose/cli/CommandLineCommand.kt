package decompose.cli

import java.io.PrintStream

open class CommandLineCommand(val commandName: String, val description: String, val aliases: Set<String> = emptySet()) {
    val positionalParameters = mutableListOf<PositionalParameter>()
    var positionalParameterValues = emptyMap<PositionalParameter, String?>()

    open fun run(): Int = 0

    fun parse(args: Iterable<String>, errorOutputStream: PrintStream): CommandLineParsingResult {
        if (args.count() > positionalParameters.count()) {
            errorOutputStream.println("Command '$commandName' takes at most ${positionalParameters.count()} argument(s).")
            return Failed
        }

        positionalParameterValues = positionalParameters.associate { it to null } +
                args.zip(positionalParameters).map { (arg, param) -> param to arg }

        return Succeeded(this)
    }

    fun printHelp(outputStream: PrintStream) {
        outputStream.println("Usage: $applicationName [COMMON OPTIONS] $commandName")
        outputStream.println()
        outputStream.println(description)
        outputStream.println()
        outputStream.println("This command does not take any options.")
    }
}
