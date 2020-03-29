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
import batect.config.io.deserializers.tryToDeserializeWith
import batect.utils.pluralize
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.CompositeDecoder
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.serializer

@Serializable(with = PortMapping.Companion::class)
data class PortMapping(
    val local: PortRange,
    val container: PortRange
) {
    constructor(localPort: Int, containerPort: Int) : this(PortRange(localPort), PortRange(containerPort))

    override fun toString(): String {
        return "$local:$container"
    }

    @Serializer(forClass = PortMapping::class)
    companion object : KSerializer<PortMapping> {
        private const val localPortFieldName = "local"
        private const val containerPortFieldName = "container"

        override val descriptor: SerialDescriptor = SerialDescriptor("PortMapping") {
            element(localPortFieldName, PortRange.descriptor)
            element(containerPortFieldName, PortRange.descriptor)
        }

        private val localPortFieldIndex = descriptor.getElementIndex("local")
        private val containerPortFieldIndex = descriptor.getElementIndex("container")

        override fun deserialize(decoder: Decoder): PortMapping {
            if (decoder !is YamlInput) {
                throw UnsupportedOperationException("Can only deserialize from YAML source.")
            }

            return decoder.tryToDeserializeWith(descriptor) { deserializeFromObject(it) }
                ?: decoder.tryToDeserializeWith(String.serializer().descriptor) { deserializeFromString(it) }
                ?: throw ConfigurationException("Port mapping definition is invalid. It must either be an object or a literal in the form 'local:container' or 'from-to:from-to'.", decoder.getCurrentLocation().line, decoder.getCurrentLocation().column)
        }

        private fun deserializeFromString(input: YamlInput): PortMapping {
            val value = input.decodeString()

            if (value == "") {
                throw ConfigurationException("Port mapping definition cannot be empty.", input.node.location.line, input.node.location.column)
            }

            val separator = ':'
            val separatorIndex = value.indexOf(separator)

            if (separatorIndex == -1) {
                throw invalidMappingDefinitionException(value, input)
            }

            val localString = value.substring(0, separatorIndex)
            val containerString = value.substring(separatorIndex + 1)

            if (localString == "" || containerString == "") {
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

                return PortMapping(localRange, containerRange)
            } catch (e: NumberFormatException) {
                throw invalidMappingDefinitionException(value, input, e)
            } catch (e: InvalidPortRangeException) {
                throw invalidMappingDefinitionException(value, input, e)
            }
        }

        private fun invalidMappingDefinitionException(value: String, input: YamlInput, cause: Throwable? = null) =
            ConfigurationException(
                "Port mapping definition '$value' is invalid. It must be in the form 'local:container' or 'from-to:from-to' and each port must be a positive integer.",
                input.node.location.line,
                input.node.location.column,
                cause
            )

        private fun deserializeFromObject(input: YamlInput): PortMapping {
            var localRange: PortRange? = null
            var containerRange: PortRange? = null

            loop@ while (true) {
                when (val i = input.decodeElementIndex(descriptor)) {
                    CompositeDecoder.READ_DONE -> break@loop
                    localPortFieldIndex -> {
                        try {
                            localRange = input.decodeSerializableElement(descriptor, i, PortRange.serializer())
                        } catch (e: ConfigurationException) {
                            throw ConfigurationException("Field '$localPortFieldName' is invalid: ${e.message}", input.getCurrentLocation().line, input.getCurrentLocation().column, e)
                        }
                    }
                    containerPortFieldIndex -> {
                        try {
                            containerRange = input.decodeSerializableElement(descriptor, i, PortRange.serializer())
                        } catch (e: ConfigurationException) {
                            throw ConfigurationException("Field '$containerPortFieldName' is invalid: ${e.message}", input.getCurrentLocation().line, input.getCurrentLocation().column, e)
                        }
                    }
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

            return PortMapping(localRange, containerRange)
        }

        override fun serialize(encoder: Encoder, value: PortMapping) {
            val output = encoder.beginStructure(descriptor)

            output.encodeSerializableElement(descriptor, localPortFieldIndex, PortRange.serializer(), value.local)
            output.encodeSerializableElement(descriptor, containerPortFieldIndex, PortRange.serializer(), value.container)

            output.endStructure(descriptor)
        }
    }
}
