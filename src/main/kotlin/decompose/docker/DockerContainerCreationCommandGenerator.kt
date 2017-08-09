package decompose.docker

import decompose.config.Container

class DockerContainerCreationCommandGenerator {
    fun createCommandLine(container: Container, command: String?, image: DockerImage, network: DockerNetwork): Iterable<String> =
            listOf("docker", "create", "--rm", "-it",
                    "--network", network.id,
                    "--hostname", container.name,
                    "--network-alias", container.name) +
                    environmentVariableArguments(container) +
                    workingDirectoryArguments(container) +
                    volumeMountArguments(container) +
                    portMappingArguments(container) +
                    image.id +
                    commandArguments(command)

    private fun environmentVariableArguments(container: Container): Iterable<String> = container.environment.flatMap { (key, value) -> listOf("--env", "$key=$value") }
    private fun volumeMountArguments(container: Container): Iterable<String> = container.volumeMounts.flatMap { listOf("--volume", it.toString()) }
    private fun portMappingArguments(container: Container): Iterable<String> = container.portMappings.flatMap { listOf("--publish", it.toString()) }

    private fun workingDirectoryArguments(container: Container): Iterable<String> = when (container.workingDirectory) {
        null -> emptyList()
        else -> listOf("--workdir", container.workingDirectory)
    }

    private val backslash: Char = '\\'
    private val singleQuote: Char = '\''
    private val doubleQuote: Char = '"'

    private fun commandArguments(command: String?): Iterable<String> {
        if (command == null) {
            return emptyList()
        }

        val arguments = arrayListOf<String>()
        val currentArgument = StringBuilder()
        var currentMode = CommandParsingState.Normal
        var currentIndex = 0

        fun handleBackslash() {
            currentIndex++

            if (currentIndex > command.length - 1) {
                throw danglingBackslash(command)
            }

            currentArgument.append(command[currentIndex])
        }

        while (currentIndex < command.length) {
            val char = command[currentIndex]

            when (currentMode) {
                CommandParsingState.Normal -> when {
                    char == backslash -> handleBackslash()
                    char.isWhitespace() -> {
                        if (currentArgument.isNotBlank()) {
                            arguments += currentArgument.toString()
                        }

                        currentArgument.setLength(0)
                    }
                    char == singleQuote -> currentMode = CommandParsingState.SingleQuote
                    char == doubleQuote -> currentMode = CommandParsingState.DoubleQuote
                    else -> currentArgument.append(char)
                }
                CommandParsingState.SingleQuote -> when (char) {
                    singleQuote -> currentMode = CommandParsingState.Normal
                    else -> currentArgument.append(char)
                }
                CommandParsingState.DoubleQuote -> when (char) {
                    doubleQuote -> currentMode = CommandParsingState.Normal
                    backslash -> handleBackslash()
                    else -> currentArgument.append(char)
                }
            }

            currentIndex++
        }

        when (currentMode) {
            CommandParsingState.DoubleQuote -> throw unbalancedDoubleQuote(command)
            CommandParsingState.SingleQuote -> throw unbalancedSingleQuote(command)
            CommandParsingState.Normal -> {
                if (currentArgument.isNotEmpty()) {
                    arguments.add(currentArgument.toString())
                }

                return arguments
            }
        }
    }

    private fun invalidCommandLine(command: String, message: String): Throwable = ContainerCreationFailedException("Command line `$command` is invalid: $message")
    private fun unbalancedDoubleQuote(command: String): Throwable = invalidCommandLine(command, "it contains an unbalanced double quote")
    private fun unbalancedSingleQuote(command: String): Throwable = invalidCommandLine(command, "it contains an unbalanced single quote")
    private fun danglingBackslash(command: String): Throwable = invalidCommandLine(command, """it ends with a backslash (backslashes always escape the following character, for a literal backslash, use '\\')""")

    private enum class CommandParsingState {
        Normal,
        SingleQuote,
        DoubleQuote
    }
}
