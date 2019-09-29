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

package batect.execution

import batect.config.EnvironmentVariableExpression
import batect.config.PortMapping
import batect.os.Command

data class ContainerRuntimeConfiguration(
    val command: Command?,
    val entrypoint: Command?,
    val workingDirectory: String?,
    val additionalEnvironmentVariables: Map<String, EnvironmentVariableExpression>,
    val additionalPortMappings: Set<PortMapping>
) {
    override fun toString(): String {
        return "${this::class.simpleName}(command: ${command?.parsedCommand ?: "null"}, " +
            "entrypoint: ${entrypoint?.parsedCommand ?: "null"}, " +
            "working directory: ${workingDirectory ?: "null"}, " +
            "additional environment variables: [${additionalEnvironmentVariables.map { "${it.key}=${it.value}" }.joinToString(", ")}], " +
            "additional port mappings: $additionalPortMappings)"
    }

    companion object {
        fun withCommand(command: Command?) = ContainerRuntimeConfiguration(command, null, null, emptyMap(), emptySet())
    }
}
