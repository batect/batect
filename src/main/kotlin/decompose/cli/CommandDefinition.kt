package decompose.cli

import com.github.salomonbrys.kodein.Kodein

abstract class CommandDefinition(val commandName: String, val description: String, val aliases: Set<String> = emptySet()) {
    val positionalParameters = mutableListOf<PositionalParameter>()
    var positionalParameterValues = emptyMap<PositionalParameter, String?>()

    fun parse(args: Iterable<String>, kodein: Kodein): CommandLineParsingResult {
        if (args.count() > positionalParameters.count()) {
            return Failed("Command '$commandName' takes at most ${positionalParameters.count()} argument(s).")
        }

        positionalParameterValues = positionalParameters.associate { it to null } +
                args.zip(positionalParameters).map { (arg, param) -> param to arg }

        return Succeeded(this.createCommand(kodein))
    }

    abstract fun createCommand(kodein: Kodein): Command
}

interface Command {
    fun run(): Int
}
