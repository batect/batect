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
import batect.os.ProxyEnvironmentVariablesProvider
import batect.ui.ConsoleInfo

class DockerContainerCreationRequestFactory(
    private val consoleInfo: ConsoleInfo,
    private val proxyEnvironmentVariablesProvider: ProxyEnvironmentVariablesProvider,
    private val hostEnvironmentVariables: Map<String, String>
) {
    constructor(consoleInfo: ConsoleInfo, proxyEnvironmentVariablesProvider: ProxyEnvironmentVariablesProvider)
        : this(consoleInfo, proxyEnvironmentVariablesProvider, System.getenv())

    fun create(
        container: Container,
        image: DockerImage,
        network: DockerNetwork,
        command: Iterable<String>,
        additionalEnvironmentVariables: Map<String, String>,
        propagateEnvironmentVariables: Boolean
    ): DockerContainerCreationRequest {
        return DockerContainerCreationRequest(
            image,
            network,
            command,
            container.name,
            container.name,
            environmentVariablesFor(container, additionalEnvironmentVariables, propagateEnvironmentVariables),
            container.workingDirectory,
            container.volumeMounts,
            container.portMappings,
            container.healthCheckConfig
        )
    }

    private fun environmentVariablesFor(container: Container, additionalEnvironmentVariables: Map<String, String>, propagateEnvironmentVariables: Boolean): Map<String, String> =
        terminalEnvironmentVariablesFor(consoleInfo) +
            proxyEnvironmentVariables(propagateEnvironmentVariables) +
            substituteEnvironmentVariables(container.environment + additionalEnvironmentVariables)

    private fun terminalEnvironmentVariablesFor(consoleInfo: ConsoleInfo): Map<String, String> = if (consoleInfo.terminalType == null) {
        emptyMap()
    } else {
        mapOf("TERM" to consoleInfo.terminalType)
    }

    private fun proxyEnvironmentVariables(propagateEnvironmentVariables: Boolean): Map<String, String> = if (propagateEnvironmentVariables) {
        proxyEnvironmentVariablesProvider.proxyEnvironmentVariables
    } else {
        emptyMap()
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
}
