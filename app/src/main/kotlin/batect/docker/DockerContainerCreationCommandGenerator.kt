/*
   Copyright 2017 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.docker

import batect.config.Container
import batect.ui.ConsoleInfo

class DockerContainerCreationCommandGenerator(private val hostEnvironmentVariables: Map<String, String>) {
    constructor() : this(System.getenv())

    fun createCommandLine(
        container: Container,
        command: String?,
        additionalEnvironmentVariables: Map<String, String>,
        image: DockerImage,
        network: DockerNetwork,
        consoleInfo: ConsoleInfo
    ): Iterable<String> {
        return listOf("docker", "create", "-it",
            "--network", network.id,
            "--hostname", container.name,
            "--network-alias", container.name) +
            environmentVariableArguments(container, consoleInfo, additionalEnvironmentVariables) +
            workingDirectoryArguments(container) +
            volumeMountArguments(container) +
            portMappingArguments(container) +
            healthCheckArguments(container) +
            image.id +
            commandArguments(command)
    }

    private fun environmentVariableArguments(container: Container, consoleInfo: ConsoleInfo, additionalEnvironmentVariables: Map<String, String>): Iterable<String> {
        return environmentVariablesFor(container, consoleInfo, additionalEnvironmentVariables)
            .flatMap { (key, value) -> listOf("--env", "$key=$value") }
    }

    private fun environmentVariablesFor(container: Container, consoleInfo: ConsoleInfo, additionalEnvironmentVariables: Map<String, String>): Map<String, String> {
        val termEnvironment = if (consoleInfo.terminalType == null) {
            emptyMap<String, String>()
        } else {
            mapOf("TERM" to consoleInfo.terminalType)
        }

        return termEnvironment + substituteEnvironmentVariables(container.environment + additionalEnvironmentVariables)
    }

    private fun substituteEnvironmentVariables(original: Map<String, String>): Map<String, String> =
        original.mapValues { (name, value) -> substituteEnvironmentVariable(name, value) }

    private fun substituteEnvironmentVariable(name: String, originalValue: String): String {
        if (originalValue.startsWith('$')) {
            val variableName = originalValue.drop(1)
            val valueFromHost = hostEnvironmentVariables.get(variableName)

            if (valueFromHost == null) {
                throw ContainerCreationFailedException("The environment variable '$name' refers to host environment variable '$variableName', but it is not set.")
            }

            return valueFromHost
        } else {
            return originalValue
        }
    }

    private fun volumeMountArguments(container: Container): Iterable<String> = container.volumeMounts.flatMap { listOf("--volume", it.toString()) }
    private fun portMappingArguments(container: Container): Iterable<String> = container.portMappings.flatMap { listOf("--publish", it.toString()) }

    private fun workingDirectoryArguments(container: Container): Iterable<String> = when (container.workingDirectory) {
        null -> emptyList()
        else -> listOf("--workdir", container.workingDirectory)
    }

    private fun healthCheckArguments(container: Container): Iterable<String> {
        return healthCheckIntervalArguments(container) +
            healthCheckRetriesArguments(container) +
            healthCheckStartPeriodArguments(container)
    }

    private fun healthCheckIntervalArguments(container: Container): Iterable<String> = when (container.healthCheckConfig.interval) {
        null -> emptyList()
        else -> listOf("--health-interval", container.healthCheckConfig.interval)
    }

    private fun healthCheckRetriesArguments(container: Container): Iterable<String> = when (container.healthCheckConfig.retries) {
        null -> emptyList()
        else -> listOf("--health-retries", container.healthCheckConfig.retries.toString())
    }

    private fun healthCheckStartPeriodArguments(container: Container): Iterable<String> = when (container.healthCheckConfig.startPeriod) {
        null -> emptyList()
        else -> listOf("--health-start-period", container.healthCheckConfig.startPeriod)
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
