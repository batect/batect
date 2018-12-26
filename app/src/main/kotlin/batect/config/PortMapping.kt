/*
   Copyright 2017-2018 Charles Korn.

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
import com.charleskorn.kaml.YamlInput
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.CompositeDecoder
import kotlinx.serialization.Decoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.internal.SerialClassDescImpl

@Serializable
data class PortMapping(
    @SerialName("local") val localPort: Int,
    @SerialName("container") val containerPort: Int
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
        fun parse(value: String): PortMapping {
            if (value == "") {
                throw InvalidPortMappingException("Port mapping definition cannot be empty.")
            }

            val separator = ':'
            val separatorIndex = value.indexOf(separator)

            if (separatorIndex == -1) {
                throw invalidMappingDefinitionException(value)
            }

            val localString = value.substring(0, separatorIndex)
            val containerString = value.substring(separatorIndex + 1)

            if (localString == "" || containerString == "") {
                throw invalidMappingDefinitionException(value)
            }

            try {
                val localPort = localString.toInt()
                val containerPort = containerString.toInt()

                return PortMapping(localPort, containerPort)
            } catch (e: NumberFormatException) {
                throw invalidMappingDefinitionException(value, e)
            } catch (e: InvalidPortMappingException) {
                throw invalidMappingDefinitionException(value, e)
            }
        }

        private fun invalidMappingDefinitionException(value: String, cause: Throwable? = null): Throwable = InvalidPortMappingException("Port mapping definition '$value' is not valid. It must be in the form 'local_port:container_port' and each port must be a positive integer.", cause)

        override val descriptor: SerialDescriptor = object : SerialClassDescImpl("PortMapping") {
            init {
                addElement("local")
                addElement("container")
            }
        }

        private val localPortFieldIndex = descriptor.getElementIndex("local")
        private val containerPortFieldIndex = descriptor.getElementIndex("container")

        override fun deserialize(input: Decoder): PortMapping = when (input) {
            is YamlInput -> {
                val inp = input.beginStructure(descriptor) as YamlInput

                when (inp.node) {
                    is YamlScalar -> deserializeFromString(inp)
                    else -> deserializeFromObject(inp)
                }.also {
                    inp.endStructure(descriptor)
                }
            }
            else -> throw UnsupportedOperationException("Can only deserialize from YAML source.")
        }

        private fun deserializeFromString(input: Decoder): PortMapping = try {
            parse(input.decodeString())
        } catch (e: InvalidPortMappingException) {
            if (input is YamlInput) {
                throw ConfigurationException(e.message ?: "", null, input.node.location.line, input.node.location.column, e)
            } else {
                throw e
            }
        }

        private fun deserializeFromObject(input: YamlInput): PortMapping {
            var localPort: Int? = null
            var containerPort: Int? = null

            loop@ while (true) {
                when (val i = input.decodeElementIndex(descriptor)) {
                    CompositeDecoder.READ_DONE -> break@loop
                    localPortFieldIndex -> localPort = input.decodeIntElement(descriptor, i)
                    containerPortFieldIndex -> containerPort = input.decodeIntElement(descriptor, i)
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            if (localPort == null) {
                throw ConfigurationException("Field '${descriptor.getElementName(localPortFieldIndex)}' is required but it is missing.", null, input.node.location.line, input.node.location.column)
            }

            if (containerPort == null) {
                throw ConfigurationException("Field '${descriptor.getElementName(containerPortFieldIndex)}' is required but it is missing.", null, input.node.location.line, input.node.location.column)
            }

            return PortMapping(localPort, containerPort)
        }
    }
}

class InvalidPortMappingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
