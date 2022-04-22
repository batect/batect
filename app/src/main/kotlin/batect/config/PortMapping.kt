/*
    Copyright 2017-2022 Charles Korn.

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

package batect.config

import batect.config.io.ConfigurationException
import batect.config.io.deserializers.SimpleStringOrObjectSerializer
import batect.docker.DockerPortMapping
import batect.utils.pluralize
import com.charleskorn.kaml.YamlInput
import com.charleskorn.kaml.YamlPath
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.SetSerializer

@Serializable
data class PortMapping(
    val local: PortRange,
    val container: PortRange,
    val protocol: String = defaultProtocol
) {
    constructor(localPort: Int, containerPort: Int, protocol: String = defaultProtocol) : this(PortRange(localPort), PortRange(containerPort), protocol)

    override fun toString(): String {
        return "$local:$container"
    }

    fun toDockerPortMapping() = DockerPortMapping(local.toDockerPortRange(), container.toDockerPortRange(), protocol)

    companion object {
        const val defaultProtocol = "tcp"
    }
}

object PortMappingConfigSerializer : SimpleStringOrObjectSerializer<PortMapping>(PortMapping.serializer()) {
    override val neitherStringNorObjectErrorMessage: String = "Port mapping definition is invalid. It must either be an object or a literal in the form 'local:container', 'local:container/protocol', 'from-to:from-to' or 'from-to:from-to/protocol'."

    override fun deserializeFromString(value: String, input: YamlInput): PortMapping {
        if (value == "") {
            throw ConfigurationException("Port mapping definition cannot be empty.", input.node)
        }

        val portSeparator = ':'
        val portSeparatorIndex = value.indexOf(portSeparator)

        if (portSeparatorIndex == -1) {
            throw invalidMappingDefinitionException(value, input)
        }

        val protocolSeparator = '/'
        val protocolSeparatorIndex = value.indexOf(protocolSeparator, portSeparatorIndex)

        val localString = value.substring(0, portSeparatorIndex)

        val containerString = if (protocolSeparatorIndex != -1) {
            value.substring(portSeparatorIndex + 1, protocolSeparatorIndex)
        } else {
            value.substring(portSeparatorIndex + 1)
        }

        val protocol = if (protocolSeparatorIndex != -1) {
            value.substring(protocolSeparatorIndex + 1)
        } else {
            PortMapping.defaultProtocol
        }

        if (localString == "" || containerString == "" || protocol == "") {
            throw invalidMappingDefinitionException(value, input)
        }

        try {
            val localRange = PortRange.parse(localString)
            val containerRange = PortRange.parse(containerString)

            return PortMapping(localRange, containerRange, protocol)
        } catch (e: NumberFormatException) {
            throw invalidMappingDefinitionException(value, input, e)
        } catch (e: InvalidPortRangeException) {
            throw invalidMappingDefinitionException(value, input, e)
        }
    }

    private fun invalidMappingDefinitionException(value: String, input: YamlInput, cause: Throwable? = null) =
        ConfigurationException(
            "Port mapping definition '$value' is invalid. It must be in the form 'local:container', 'local:container/protocol', 'from-to:from-to' or 'from-to:from-to/protocol' and each port must be a positive integer.",
            input.node,
            cause
        )

    override fun validateDeserializedObject(value: PortMapping, path: YamlPath) {
        if (value.local.size != value.container.size) {
            throw ConfigurationException(
                "Port mapping definition is invalid. The local port range has ${pluralize(value.local.size, "port")} and the container port range has ${pluralize(value.container.size, "port")}, but the ranges must be the same size.",
                path
            )
        }
    }
}

object PortMappingConfigSetSerializer : KSerializer<Set<PortMapping>> by SetSerializer(PortMappingConfigSerializer)
