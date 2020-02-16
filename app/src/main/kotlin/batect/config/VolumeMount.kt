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
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.internal.nullable

@Serializable(with = VolumeMount.Companion::class)
data class VolumeMount(
    val localPath: VariableExpression,
    val containerPath: String,
    val options: String? = null
) {
    @Serializer(forClass = VolumeMount::class)
    companion object : KSerializer<VolumeMount> {
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

        override fun deserialize(decoder: Decoder): VolumeMount {
            if (decoder !is YamlInput) {
                throw UnsupportedOperationException("Can only deserialize from YAML source.")
            }

            return decoder.tryToDeserializeWith(descriptor) { deserializeFromObject(it) }
                ?: decoder.tryToDeserializeWith(StringDescriptor) { deserializeFromString(it) }
                ?: throw ConfigurationException("Volume mount definition is not valid. It must either be an object or a literal in the form 'local_path:container_path' or 'local_path:container_path:options'.")
        }

        private fun deserializeFromString(input: YamlInput): VolumeMount {
            val value = input.decodeString()

            if (value == "") {
                throw ConfigurationException("Volume mount definition cannot be empty.", input.node.location.line, input.node.location.column)
            }

            val regex = """(([a-zA-Z]:\\)?[^:]+):([^:]+)(:([^:]+))?""".toRegex()
            val match = regex.matchEntire(value)

            if (match == null) {
                throw invalidMountDefinitionException(value, input)
            }

            val local = match.groupValues[1]
            val container = match.groupValues[3]
            val options = match.groupValues[5].takeIf { it.isNotEmpty() }

            val resolvedLocal = LiteralValue(local)

            return VolumeMount(resolvedLocal, container, options)
        }

        private fun invalidMountDefinitionException(value: String, input: YamlInput) =
            ConfigurationException(
                "Volume mount definition '$value' is not valid. It must be in the form 'local_path:container_path' or 'local_path:container_path:options'.",
                input.node.location.line,
                input.node.location.column
            )

        private fun deserializeFromObject(input: YamlInput): VolumeMount {
            var localPath: VariableExpression? = null
            var containerPath: String? = null
            var options: String? = null

            loop@ while (true) {
                when (val i = input.decodeElementIndex(descriptor)) {
                    CompositeDecoder.READ_DONE -> break@loop
                    localPathFieldIndex -> localPath = input.decodeSerializableElement(descriptor, i, VariableExpression.serializer())
                    containerPathFieldIndex -> containerPath = input.decodeStringElement(descriptor, i)
                    optionsFieldIndex -> options = input.decodeStringElement(descriptor, i)
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            if (localPath == null) {
                throw ConfigurationException("Field '${descriptor.getElementName(localPathFieldIndex)}' is required but it is missing.", input.node.location.line, input.node.location.column)
            }

            if (containerPath == null) {
                throw ConfigurationException("Field '${descriptor.getElementName(containerPathFieldIndex)}' is required but it is missing.", input.node.location.line, input.node.location.column)
            }

            return VolumeMount(localPath, containerPath, options)
        }

        override fun serialize(encoder: Encoder, obj: VolumeMount) {
            val output = encoder.beginStructure(descriptor)

            output.encodeSerializableElement(descriptor, localPathFieldIndex, VariableExpression.serializer(), obj.localPath)
            output.encodeStringElement(descriptor, containerPathFieldIndex, obj.containerPath)
            output.encodeSerializableElement(descriptor, optionsFieldIndex, StringSerializer.nullable, obj.options)

            output.endStructure(descriptor)
        }
    }
}
