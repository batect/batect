/*
   Copyright 2017-2019 Charles Korn.

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
import batect.config.EnvironmentVariableExpression
import batect.config.EnvironmentVariableExpressionEvaluationException
import batect.config.PortMapping
import batect.config.VolumeMount
import batect.os.Command
import batect.os.proxies.ProxyEnvironmentVariablesProvider
import batect.ui.ConsoleInfo
import batect.utils.mapToSet

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
        command: Command?,
        workingDirectory: String?,
        additionalEnvironmentVariables: Map<String, EnvironmentVariableExpression>,
        additionalVolumeMounts: Set<VolumeMount>,
        additionalPortMappings: Set<PortMapping>,
        propagateProxyEnvironmentVariables: Boolean,
        userAndGroup: UserAndGroup?,
        attachTTY: Boolean,
        allContainersInNetwork: Set<Container>
    ): DockerContainerCreationRequest {
        return DockerContainerCreationRequest(
            image,
            network,
            if (command != null) command.parsedCommand else emptyList(),
            container.name,
            container.name,
            environmentVariablesFor(container, additionalEnvironmentVariables, propagateProxyEnvironmentVariables, allContainersInNetwork),
            workingDirectory,
            container.volumeMounts + additionalVolumeMounts,
            container.portMappings + additionalPortMappings,
            container.healthCheckConfig,
            userAndGroup,
            container.privileged,
            container.enableInitProcess,
            container.capabilitiesToAdd,
            container.capabilitiesToDrop,
            attachTTY
        )
    }

    private fun environmentVariablesFor(container: Container, additionalEnvironmentVariables: Map<String, EnvironmentVariableExpression>, propagateProxyEnvironmentVariables: Boolean, allContainersInNetwork: Set<Container>): Map<String, String> =
        terminalEnvironmentVariablesFor(consoleInfo) +
            proxyEnvironmentVariables(propagateProxyEnvironmentVariables, allContainersInNetwork) +
            substituteEnvironmentVariables(container.environment + additionalEnvironmentVariables)

    private fun terminalEnvironmentVariablesFor(consoleInfo: ConsoleInfo): Map<String, String> = if (consoleInfo.terminalType == null) {
        emptyMap()
    } else {
        mapOf("TERM" to consoleInfo.terminalType)
    }

    private fun proxyEnvironmentVariables(propagateProxyEnvironmentVariables: Boolean, allContainersInNetwork: Set<Container>): Map<String, String> = if (propagateProxyEnvironmentVariables) {
        proxyEnvironmentVariablesProvider.getProxyEnvironmentVariables(allContainersInNetwork.mapToSet { it.name })
    } else {
        emptyMap()
    }

    private fun substituteEnvironmentVariables(original: Map<String, EnvironmentVariableExpression>): Map<String, String> =
        original.mapValues { (name, value) -> evaluateEnvironmentVariableValue(name, value) }

    private fun evaluateEnvironmentVariableValue(name: String, expression: EnvironmentVariableExpression): String {
        try {
            return expression.evaluate(hostEnvironmentVariables)
        } catch (e: EnvironmentVariableExpressionEvaluationException) {
            throw ContainerCreationFailedException("The value for the environment variable '$name' cannot be evaluated: ${e.message}", e)
        }
    }
}
