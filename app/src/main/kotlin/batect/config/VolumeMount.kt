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
import kotlinx.serialization.Optional
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.internal.SerialClassDescImpl

@Serializable
data class VolumeMount(
    @SerialName("local") val localPath: String,
    @SerialName("container") val containerPath: String,
    @Optional val options: String? = null
) {
    override fun toString(): String {
        if (options == null) {
            return "$localPath:$containerPath"
        } else {
            return "$localPath:$containerPath:$options"
        }
    }

    @Serializer(forClass = VolumeMount::class)
    companion object : KSerializer<VolumeMount> {
        fun parse(value: String): VolumeMount {
            if (value == "") {
                throw InvalidVolumeMountException("Volume mount definition cannot be empty.")
            }

            val parts = value.split(':')

            if (parts.size < 2 || parts.size > 3) {
                throw invalidMountDefinitionException(value)
            }

            val local = parts[0]
            val container = parts[1]
            val options = parts.getOrNull(2)

            if (local == "" || container == "" || options == "") {
                throw invalidMountDefinitionException(value)
            }

            return VolumeMount(local, container, options)
        }

        fun invalidMountDefinitionException(value: String): Throwable = InvalidVolumeMountException("Volume mount definition '$value' is not valid. It must be in the form 'local_path:container_path' or 'local_path:container_path:options'.")

        override val descriptor: SerialDescriptor = object : SerialClassDescImpl("VolumeMount") {
            init {
                addElement("local")
                addElement("container")
                addElement("options", isOptional = true)
            }
        }

        private val localPathFieldIndex = descriptor.getElementIndex("local")
        private val containerPathFieldIndex = descriptor.getElementIndex("container")
        private val optionsFieldIndex = descriptor.getElementIndex("options")

        override fun deserialize(input: Decoder): VolumeMount = when (input) {
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

        private fun deserializeFromString(input: Decoder): VolumeMount = try {
            VolumeMount.parse(input.decodeString())
        } catch (e: InvalidVolumeMountException) {
            if (input is YamlInput) {
                throw ConfigurationException(e.message ?: "", null, input.node.location.line, input.node.location.column, e)
            } else {
                throw e
            }
        }

        private fun deserializeFromObject(input: YamlInput): VolumeMount {
            var localPath: String? = null
            var containerPath: String? = null
            var options: String? = null

            loop@ while (true) {
                when (val i = input.decodeElementIndex(descriptor)) {
                    CompositeDecoder.READ_DONE -> break@loop
                    localPathFieldIndex -> localPath = input.decodeStringElement(descriptor, i)
                    containerPathFieldIndex -> containerPath = input.decodeStringElement(descriptor, i)
                    optionsFieldIndex -> options = input.decodeStringElement(descriptor, i)
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            if (localPath == null) {
                throw ConfigurationException("Field '${descriptor.getElementName(localPathFieldIndex)}' is required but it is missing.", null, input.node.location.line, input.node.location.column)
            }

            if (containerPath == null) {
                throw ConfigurationException("Field '${descriptor.getElementName(containerPathFieldIndex)}' is required but it is missing.", null, input.node.location.line, input.node.location.column)
            }

            return VolumeMount(localPath, containerPath, options)
        }
    }
}

class InvalidVolumeMountException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
