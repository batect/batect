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

package batect.config

import batect.config.io.ConfigurationException
import batect.config.io.deserializers.StringOrObjectSerializer
import batect.docker.DockerPortMapping
import batect.utils.pluralize
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.CompositeDecoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer

@Serializable(with = PortMapping.Companion::class)
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

    companion object : StringOrObjectSerializer<PortMapping>() {
        const val defaultProtocol = "tcp"

        override val neitherStringNorObjectErrorMessage: String = "Port mapping definition is invalid. It must either be an object or a literal in the form 'local:container', 'local:container/protocol', 'from-to:from-to' or 'from-to:from-to/protocol'."
        override val serialName: String = PortMapping::class.qualifiedName!!

        private const val localPortFieldName = "local"
        private const val containerPortFieldName = "container"
        private const val protocolFieldName = "protocol"

        override val objectDescriptor: SerialDescriptor = SerialDescriptor(serialName) {
            element(localPortFieldName, PortRange.descriptor)
            element(containerPortFieldName, PortRange.descriptor)
            element(protocolFieldName, String.serializer().descriptor)
        }

        private val localPortFieldIndex = objectDescriptor.getElementIndex(localPortFieldName)
        private val containerPortFieldIndex = objectDescriptor.getElementIndex(containerPortFieldName)
        private val protocolFieldIndex = objectDescriptor.getElementIndex(protocolFieldName)

        override fun deserializeFromString(value: String, input: YamlInput): PortMapping {
            if (value == "") {
                throw ConfigurationException("Port mapping definition cannot be empty.", input.node.location.line, input.node.location.column)
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
                defaultProtocol
            }

            if (localString == "" || containerString == "" || protocol == "") {
                throw invalidMappingDefinitionException(value, input)
            }

            try {
                val localRange = PortRange.parse(localString)
                val containerRange = PortRange.parse(containerString)

                if (localRange.size != containerRange.size) {
                    throw ConfigurationException(
                        "Port mapping definition '$value' is invalid. The local port range has ${pluralize(localRange.size, "port")} and the container port range has ${pluralize(containerRange.size, "port")}, but the ranges must be the same size.",
                        input.node.location.line,
                        input.node.location.column
                    )
                }

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
                input.node.location.line,
                input.node.location.column,
                cause
            )

        override fun deserializeFromObject(input: YamlInput): PortMapping {
            var localRange: PortRange? = null
            var containerRange: PortRange? = null
            var protocol = "tcp"

            loop@ while (true) {
                when (val i = input.decodeElementIndex(objectDescriptor)) {
                    CompositeDecoder.READ_DONE -> break@loop
                    localPortFieldIndex -> {
                        try {
                            localRange = input.decodeSerializableElement(objectDescriptor, i, PortRange.serializer())
                        } catch (e: ConfigurationException) {
                            throw ConfigurationException("Field '$localPortFieldName' is invalid: ${e.message}", input.getCurrentLocation().line, input.getCurrentLocation().column, e)
                        }
                    }
                    containerPortFieldIndex -> {
                        try {
                            containerRange = input.decodeSerializableElement(objectDescriptor, i, PortRange.serializer())
                        } catch (e: ConfigurationException) {
                            throw ConfigurationException("Field '$containerPortFieldName' is invalid: ${e.message}", input.getCurrentLocation().line, input.getCurrentLocation().column, e)
                        }
                    }
                    protocolFieldIndex -> protocol = input.decodeStringElement(objectDescriptor, i)
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            if (localRange == null) {
                throw ConfigurationException("Field '$localPortFieldName' is required but it is missing.", input.node.location.line, input.node.location.column)
            }

            if (containerRange == null) {
                throw ConfigurationException("Field '$containerPortFieldName' is required but it is missing.", input.node.location.line, input.node.location.column)
            }

            if (localRange.size != containerRange.size) {
                throw ConfigurationException(
                    "Port mapping definition is invalid. The local port range has ${pluralize(localRange.size, "port")} and the container port range has ${pluralize(containerRange.size, "port")}, but the ranges must be the same size.",
                    input.node.location.line,
                    input.node.location.column
                )
            }

            return PortMapping(localRange, containerRange, protocol)
        }

        override fun serialize(encoder: Encoder, value: PortMapping) {
            val output = encoder.beginStructure(objectDescriptor)

            output.encodeSerializableElement(objectDescriptor, localPortFieldIndex, PortRange.serializer(), value.local)
            output.encodeSerializableElement(objectDescriptor, containerPortFieldIndex, PortRange.serializer(), value.container)
            output.encodeSerializableElement(objectDescriptor, protocolFieldIndex, String.serializer(), value.protocol)

            output.endStructure(objectDescriptor)
        }
    }
}
