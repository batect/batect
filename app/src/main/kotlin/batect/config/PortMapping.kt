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
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.CompositeDecoder
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.internal.SerialClassDescImpl
import kotlinx.serialization.internal.StringDescriptor

@Serializable(with = PortMapping.Companion::class)
data class PortMapping(
    val localPort: Int,
    val containerPort: Int
) {
    init {
        if (localPort <= 0) {
            throw InvalidPortMappingException("Local port must be positive.")
        }

        if (containerPort <= 0) {
            throw InvalidPortMappingException("Container port must be positive.")
        }
    }

    override fun toString(): String {
        return "$localPort:$containerPort"
    }

    @Serializer(forClass = PortMapping::class)
    companion object : KSerializer<PortMapping> {
        private const val localPortFieldName = "local"
        private const val containerPortFieldName = "container"

        override val descriptor: SerialDescriptor = object : SerialClassDescImpl("PortMapping") {
            init {
                addElement(localPortFieldName)
                addElement(containerPortFieldName)
            }
        }

        private val localPortFieldIndex = descriptor.getElementIndex("local")
        private val containerPortFieldIndex = descriptor.getElementIndex("container")

        override fun deserialize(decoder: Decoder): PortMapping {
            if (decoder !is YamlInput) {
                throw UnsupportedOperationException("Can only deserialize from YAML source.")
            }

            return decoder.tryToDeserializeWith(descriptor) { deserializeFromObject(it) }
                ?: decoder.tryToDeserializeWith(StringDescriptor) { deserializeFromString(it) }
                ?: throw ConfigurationException("Port mapping definition is not valid. It must either be an object or a literal in the form 'local_port:container_port'.")
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
                val localPort = localString.toInt()
                val containerPort = containerString.toInt()

                return PortMapping(localPort, containerPort)
            } catch (e: NumberFormatException) {
                throw invalidMappingDefinitionException(value, input, e)
            } catch (e: InvalidPortMappingException) {
                throw invalidMappingDefinitionException(value, input, e)
            }
        }

        private fun invalidMappingDefinitionException(value: String, input: YamlInput, cause: Throwable? = null) =
            ConfigurationException(
                "Port mapping definition '$value' is not valid. It must be in the form 'local_port:container_port' and each port must be a positive integer.",
                input.node.location.line,
                input.node.location.column,
                cause
            )

        private fun deserializeFromObject(input: YamlInput): PortMapping {
            var localPort: Int? = null
            var containerPort: Int? = null

            loop@ while (true) {
                when (val i = input.decodeElementIndex(descriptor)) {
                    CompositeDecoder.READ_DONE -> break@loop
                    localPortFieldIndex -> {
                        localPort = input.decodeIntElement(descriptor, i)

                        if (localPort <= 0) {
                            throw ConfigurationException("Field '$localPortFieldName' is invalid: it must be a positive integer.", input.getCurrentLocation().line, input.getCurrentLocation().column)
                        }
                    }
                    containerPortFieldIndex -> {
                        containerPort = input.decodeIntElement(descriptor, i)

                        if (containerPort <= 0) {
                            throw ConfigurationException("Field '$containerPortFieldName' is invalid: it must be a positive integer.", input.getCurrentLocation().line, input.getCurrentLocation().column)
                        }
                    }
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            if (localPort == null) {
                throw ConfigurationException("Field '$localPortFieldName' is required but it is missing.", input.node.location.line, input.node.location.column)
            }

            if (containerPort == null) {
                throw ConfigurationException("Field '$containerPortFieldName' is required but it is missing.", input.node.location.line, input.node.location.column)
            }

            return PortMapping(localPort, containerPort)
        }

        override fun serialize(encoder: Encoder, obj: PortMapping) {
            val output = encoder.beginStructure(descriptor)

            output.encodeIntElement(descriptor, localPortFieldIndex, obj.localPort)
            output.encodeIntElement(descriptor, containerPortFieldIndex, obj.containerPort)

            output.endStructure(descriptor)
        }
    }
}

class InvalidPortMappingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
