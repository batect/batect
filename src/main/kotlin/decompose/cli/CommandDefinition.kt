package decompose.cli

import com.github.salomonbrys.kodein.Kodein

abstract class CommandDefinition(val commandName: String, val description: String, val aliases: Set<String> = emptySet()) {
    val optionalPositionalParameters = mutableListOf<OptionalPositionalParameter>()

    fun parse(args: Iterable<String>, kodein: Kodein): CommandLineParsingResult {
        if (args.count() > optionalPositionalParameters.count()) {
            return Failed("Command '$commandName' takes at most ${optionalPositionalParameters.count()} parameter(s).")
        }

        args.zip(optionalPositionalParameters).map { (arg, param) -> param.value = arg }
        optionalPositionalParameters.drop(args.count()).forEach { it.value = null }

        return Succeeded(this.createCommand(kodein))
    }

    abstract fun createCommand(kodein: Kodein): Command

    fun getAllPositionalParameterDefinitions(): List<PositionalParameterDefinition> = optionalPositionalParameters
}

interface Command {
    fun run(): Int
}
