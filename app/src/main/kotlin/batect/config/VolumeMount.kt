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
import batect.config.io.deserializers.PathDeserializer
import batect.config.io.deserializers.tryToDeserializeWith
import batect.os.PathResolutionResult
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.CompositeDecoder
import kotlinx.serialization.CompositeEncoder
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import java.nio.file.Path

@Serializable(with = VolumeMount.Companion::class)
sealed class VolumeMount(
    open val containerPath: String,
    open val options: String? = null
) {
    protected abstract fun serialize(output: CompositeEncoder)

    @Serializer(forClass = VolumeMount::class)
    companion object : KSerializer<VolumeMount> {
        override val descriptor: SerialDescriptor = SerialDescriptor("VolumeMount") {
            element("local", Expression.serializer().descriptor, isOptional = true)
            element("container", String.serializer().descriptor)
            element("options", String.serializer().descriptor, isOptional = true)
            element("name", String.serializer().descriptor, isOptional = true)
            element("type", String.serializer().descriptor, isOptional = true)
        }

        private val localPathFieldIndex = descriptor.getElementIndex("local")
        private val relativeToFieldIndex = descriptor.getElementIndex("relativeTo")
        private val containerPathFieldIndex = descriptor.getElementIndex("container")
        private val optionsFieldIndex = descriptor.getElementIndex("options")
        private val nameFieldIndex = descriptor.getElementIndex("name")
        private val typeFieldIndex = descriptor.getElementIndex("type")

        override fun deserialize(decoder: Decoder): VolumeMount {
            if (decoder !is YamlInput) {
                throw UnsupportedOperationException("Can only deserialize from YAML source.")
            }

            return decoder.tryToDeserializeWith(descriptor) { deserializeFromObject(it) }
                ?: decoder.tryToDeserializeWith(String.serializer().descriptor) { deserializeFromString(it) }
                ?: throw ConfigurationException("Volume mount definition is invalid. It must either be an object or a literal in the form 'local_path:container_path' or 'local_path:container_path:options'.")
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

            return LocalMount(resolvedLocal, input.configFileDirectory, container, options)
        }

        private fun invalidMountDefinitionException(value: String, input: YamlInput) =
            ConfigurationException(
                "Volume mount definition '$value' is invalid. It must be in the form 'local_path:container_path' or 'local_path:container_path:options'.",
                input.node.location.line,
                input.node.location.column
            )

        private fun deserializeFromObject(input: YamlInput): VolumeMount {
            var localPath: Expression? = null
            var containerPath: String? = null
            var options: String? = null
            var name: String? = null
            var type: VolumeMountType = VolumeMountType.Local

            loop@ while (true) {
                when (val i = input.decodeElementIndex(descriptor)) {
                    CompositeDecoder.READ_DONE -> break@loop
                    localPathFieldIndex -> localPath = input.decodeSerializableElement(descriptor, i, Expression.serializer())
                    containerPathFieldIndex -> containerPath = input.decodeStringElement(descriptor, i)
                    optionsFieldIndex -> options = input.decodeStringElement(descriptor, i)
                    nameFieldIndex -> name = input.decodeStringElement(descriptor, i)
                    typeFieldIndex -> type = input.decodeSerializableElement(descriptor, i, VolumeMountType.serializer())
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            if (containerPath == null) {
                throw ConfigurationException("Field '${descriptor.getElementName(containerPathFieldIndex)}' is required but it is missing.", input.node.location.line, input.node.location.column)
            }

            return when (type) {
                VolumeMountType.Local -> {
                    if (localPath == null) {
                        throw ConfigurationException("Field '${descriptor.getElementName(localPathFieldIndex)}' is required for local path mounts but it is missing.", input.node.location.line, input.node.location.column)
                    }

                    if (name != null) {
                        throw ConfigurationException("Field '${descriptor.getElementName(nameFieldIndex)}' is not permitted for local path mounts.", input.node.location.line, input.node.location.column)
                    }

                    LocalMount(localPath, input.configFileDirectory, containerPath, options)
                }

                VolumeMountType.Cache -> {
                    if (name == null) {
                        throw ConfigurationException("Field '${descriptor.getElementName(nameFieldIndex)}' is required for cache mounts but it is missing.", input.node.location.line, input.node.location.column)
                    }

                    if (localPath != null) {
                        throw ConfigurationException("Field '${descriptor.getElementName(localPathFieldIndex)}' is not permitted for cache mounts.", input.node.location.line, input.node.location.column)
                    }

                    CacheMount(name, containerPath, options)
                }
            }
        }

        override fun serialize(encoder: Encoder, value: VolumeMount) {
            val output = encoder.beginStructure(descriptor)

            output.encodeStringElement(descriptor, containerPathFieldIndex, value.containerPath)
            output.encodeSerializableElement(descriptor, optionsFieldIndex, String.serializer().nullable, value.options)

            val type = when (value) {
                is LocalMount -> "local"
                is CacheMount -> "cache"
            }

            output.encodeStringElement(descriptor, typeFieldIndex, type)
            value.serialize(output)

            output.endStructure(descriptor)
        }

        private val YamlInput.configFileDirectory: Path
            get() {
                val deserializer = this.context.getContextual(PathResolutionResult::class)!! as PathDeserializer
                val resolver = deserializer.pathResolver

                return resolver.relativeTo
            }

        @Serializable
        private enum class VolumeMountType {
            @SerialName("local") Local,
            @SerialName("cache") Cache
        }
    }
}

data class LocalMount(
    val localPath: Expression,
    val relativeTo: Path,
    override val containerPath: String,
    override val options: String? = null
) : VolumeMount(containerPath, options) {
    private val descriptor: SerialDescriptor = SerialDescriptor("VolumeMount") {
        element("local", Expression.serializer().descriptor)
        element("relativeTo", String.serializer().descriptor)
    }

    private val localPathFieldIndex = descriptor.getElementIndex("local")
    private val relativeToFieldIndex = descriptor.getElementIndex("relativeTo")

    protected override fun serialize(output: CompositeEncoder) {
        output.encodeSerializableElement(descriptor, localPathFieldIndex, Expression.serializer(), localPath)
        output.encodeStringElement(descriptor, relativeToFieldIndex, relativeTo.toString())
    }
}

data class CacheMount(
    val name: String,
    override val containerPath: String,
    override val options: String? = null
) : VolumeMount(containerPath, options) {
    private val descriptor: SerialDescriptor = SerialDescriptor("VolumeMount") {
        element("name", Expression.serializer().descriptor)
    }

    private val nameFieldIndex = descriptor.getElementIndex("name")

    protected override fun serialize(output: CompositeEncoder) {
        output.encodeStringElement(descriptor, nameFieldIndex, name)
    }
}
