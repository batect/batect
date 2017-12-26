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
import batect.os.CommandParser
import batect.os.InvalidCommandLineException
import batect.ui.ConsoleInfo

class DockerContainerCreationCommandGenerator(private val commandParser: CommandParser, private val hostEnvironmentVariables: Map<String, String>) {
    constructor(commandParser: CommandParser) : this(commandParser, System.getenv())

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
            emptyMap()
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

    private fun commandArguments(command: String?): Iterable<String> {
        try {
            return commandParser.parse(command)
        } catch (e: InvalidCommandLineException) {
            throw ContainerCreationFailedException(e.message, e)
        }
    }
}
