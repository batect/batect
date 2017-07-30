package decompose.cli

import com.github.salomonbrys.kodein.Kodein

abstract class CommandDefinition(val commandName: String, val description: String, val aliases: Set<String> = emptySet()) {
    val requiredPositionalParameters = mutableListOf<RequiredPositionalParameter>()
    val optionalPositionalParameters = mutableListOf<OptionalPositionalParameter>()

    init {
        if (commandName == "") {
            throw IllegalArgumentException("Command name must not be empty.")
        }

        if (description == "") {
            throw IllegalArgumentException("Command description must not be empty.")
        }
    }

    fun parse(args: Iterable<String>, kodein: Kodein): CommandLineParsingResult {
        if (args.count() < requiredPositionalParameters.count()) {
            val firstMissingParam = requiredPositionalParameters.drop(args.count()).first()
            val noun = if (requiredPositionalParameters.count() == 1) "parameter" else "parameters"

            return Failed("Command '$commandName' requires at least ${requiredPositionalParameters.count()} $noun. The parameter '${firstMissingParam.name}' was not supplied.")
        }

        val maxParameters = requiredPositionalParameters.count() + optionalPositionalParameters.count()

        if (args.count() > maxParameters) {
            val firstInvalid = args.drop(maxParameters).first()
            val message = when (maxParameters) {
                0 -> "does not take any parameters"
                1 -> "takes at most 1 parameter"
                else -> "takes at most $maxParameters parameters"
            }

            return Failed("Command '$commandName' $message. ('$firstInvalid' is the first extra parameter.)")
        }

        val requiredParameterValues = args.take(requiredPositionalParameters.count())
        requiredParameterValues.zip(requiredPositionalParameters).forEach { (value, param) -> param.value = value }

        val optionalParameterValues = args.drop(requiredPositionalParameters.count())
        optionalParameterValues.zip(optionalPositionalParameters).forEach { (arg, param) -> param.value = arg }
        optionalPositionalParameters.drop(optionalParameterValues.count()).forEach { it.value = null }

        return Succeeded(this.createCommand(kodein))
    }

    abstract fun createCommand(kodein: Kodein): Command

    fun getAllPositionalParameterDefinitions(): List<PositionalParameterDefinition> = requiredPositionalParameters + optionalPositionalParameters
}

interface Command {
    fun run(): Int
}
