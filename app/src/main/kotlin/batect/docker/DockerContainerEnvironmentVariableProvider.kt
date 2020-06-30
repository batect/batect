/*
   Copyright 2017-2020 Charles Korn.

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
import batect.config.Expression
import batect.config.ExpressionEvaluationContext
import batect.config.ExpressionEvaluationException
import batect.execution.ContainerDependencyGraph
import batect.execution.ContainerRuntimeConfiguration
import batect.mapToSet
import batect.proxies.ProxyEnvironmentVariablesProvider

class DockerContainerEnvironmentVariableProvider(
    private val proxyEnvironmentVariablesProvider: ProxyEnvironmentVariablesProvider,
    private val expressionEvaluationContext: ExpressionEvaluationContext,
    private val containerDependencyGraph: ContainerDependencyGraph
) {
    private val allContainersInNetwork = containerDependencyGraph.allContainers

    fun environmentVariablesFor(
        container: Container,
        config: ContainerRuntimeConfiguration,
        propagateProxyEnvironmentVariables: Boolean,
        terminalType: String?
    ): Map<String, String> =
        terminalEnvironmentVariablesFor(terminalType) +
            proxyEnvironmentVariables(propagateProxyEnvironmentVariables) +
            substituteEnvironmentVariables(container.environment + config.additionalEnvironmentVariables)

    private fun terminalEnvironmentVariablesFor(terminalType: String?): Map<String, String> = if (terminalType == null) {
        emptyMap()
    } else {
        mapOf("TERM" to terminalType)
    }

    private fun proxyEnvironmentVariables(propagateProxyEnvironmentVariables: Boolean): Map<String, String> = if (propagateProxyEnvironmentVariables) {
        proxyEnvironmentVariablesProvider.getProxyEnvironmentVariables(allContainersInNetwork.mapToSet { it.name })
    } else {
        emptyMap()
    }

    private fun substituteEnvironmentVariables(original: Map<String, Expression>): Map<String, String> =
        original.mapValues { (name, value) -> evaluateEnvironmentVariableValue(name, value) }

    private fun evaluateEnvironmentVariableValue(name: String, expression: Expression): String {
        try {
            return expression.evaluate(expressionEvaluationContext)
        } catch (e: ExpressionEvaluationException) {
            throw ContainerCreationFailedException("The value for the environment variable '$name' cannot be evaluated: ${e.message}", e)
        }
    }
}
