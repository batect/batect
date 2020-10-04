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
import batect.config.io.deserializers.StringOrObjectSerializer
import batect.os.PathResolutionContext
import batect.os.PathResolutionContextSerializer
import batect.os.PathResolutionResult
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = VolumeMount.Companion::class)
sealed class VolumeMount(
    open val containerPath: String,
    open val options: String? = null
) {
    protected abstract fun serialize(output: CompositeEncoder)

    @OptIn(ExperimentalSerializationApi::class)
    companion object : StringOrObjectSerializer<VolumeMount>() {
        override val serialName: String = VolumeMount::class.qualifiedName!!
        override val neitherStringNorObjectErrorMessage: String = "Volume mount definition is invalid. It must either be an object or a literal in the form 'local_path:container_path' or 'local_path:container_path:options'."

        override val objectDescriptor = buildClassSerialDescriptor(serialName) {
            element("local", Expression.serializer().descriptor, isOptional = true)
            element("container", String.serializer().descriptor)
            element("options", String.serializer().descriptor, isOptional = true)
            element("name", String.serializer().descriptor, isOptional = true)
            element("type", String.serializer().descriptor, isOptional = true)
        }

        private val localPathFieldIndex = objectDescriptor.getElementIndex("local")
        private val containerPathFieldIndex = objectDescriptor.getElementIndex("container")
        private val optionsFieldIndex = objectDescriptor.getElementIndex("options")
        private val nameFieldIndex = objectDescriptor.getElementIndex("name")
        private val typeFieldIndex = objectDescriptor.getElementIndex("type")

        override fun deserializeFromString(value: String, input: YamlInput): VolumeMount {
            if (value == "") {
                throw ConfigurationException("Volume mount definition cannot be empty.", input.node)
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

            return LocalMount(resolvedLocal, input.pathResolutionContext, container, options)
        }

        private fun invalidMountDefinitionException(value: String, input: YamlInput) =
            ConfigurationException(
                "Volume mount definition '$value' is invalid. It must be in the form 'local_path:container_path' or 'local_path:container_path:options'.",
                input.node
            )

        override fun deserializeFromObject(input: YamlInput): VolumeMount {
            var localPath: Expression? = null
            var containerPath: String? = null
            var options: String? = null
            var name: String? = null
            var type: VolumeMountType = VolumeMountType.Local

            loop@ while (true) {
                when (val i = input.decodeElementIndex(objectDescriptor)) {
                    CompositeDecoder.DECODE_DONE -> break@loop
                    localPathFieldIndex -> localPath = input.decodeSerializableElement(objectDescriptor, i, Expression.serializer())
                    containerPathFieldIndex -> containerPath = input.decodeStringElement(objectDescriptor, i)
                    optionsFieldIndex -> options = input.decodeStringElement(objectDescriptor, i)
                    nameFieldIndex -> name = input.decodeStringElement(objectDescriptor, i)
                    typeFieldIndex -> type = input.decodeSerializableElement(objectDescriptor, i, VolumeMountType.serializer())
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            if (containerPath == null) {
                throw ConfigurationException("Field '${objectDescriptor.getElementName(containerPathFieldIndex)}' is required but it is missing.", input.node)
            }

            return when (type) {
                VolumeMountType.Local -> {
                    if (localPath == null) {
                        throw ConfigurationException("Field '${objectDescriptor.getElementName(localPathFieldIndex)}' is required for local path mounts but it is missing.", input.node)
                    }

                    if (name != null) {
                        throw ConfigurationException("Field '${objectDescriptor.getElementName(nameFieldIndex)}' is not permitted for local path mounts.", input.node)
                    }

                    LocalMount(localPath, input.pathResolutionContext, containerPath, options)
                }

                VolumeMountType.Cache -> {
                    if (name == null) {
                        throw ConfigurationException("Field '${objectDescriptor.getElementName(nameFieldIndex)}' is required for cache mounts but it is missing.", input.node)
                    }

                    if (localPath != null) {
                        throw ConfigurationException("Field '${objectDescriptor.getElementName(localPathFieldIndex)}' is not permitted for cache mounts.", input.node)
                    }

                    CacheMount(name, containerPath, options)
                }
            }
        }

        override fun serialize(encoder: Encoder, value: VolumeMount) {
            val output = encoder.beginStructure(objectDescriptor)

            output.encodeStringElement(objectDescriptor, containerPathFieldIndex, value.containerPath)
            output.encodeSerializableElement(objectDescriptor, optionsFieldIndex, String.serializer().nullable, value.options)

            val type = when (value) {
                is LocalMount -> "local"
                is CacheMount -> "cache"
            }

            output.encodeStringElement(objectDescriptor, typeFieldIndex, type)
            value.serialize(output)

            output.endStructure(objectDescriptor)
        }

        private val YamlInput.pathResolutionContext: PathResolutionContext
            get() {
                val deserializer = this.serializersModule.getContextual(PathResolutionResult::class)!! as PathDeserializer
                val resolver = deserializer.pathResolver

                return resolver.context
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
    val pathResolutionContext: PathResolutionContext,
    override val containerPath: String,
    override val options: String? = null
) : VolumeMount(containerPath, options) {
    private val descriptor: SerialDescriptor = buildClassSerialDescriptor("VolumeMount") {
        element("local", Expression.serializer().descriptor)
        element("pathResolutionContext", PathResolutionContextSerializer.descriptor)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(output: CompositeEncoder) {
        output.encodeSerializableElement(descriptor, descriptor.getElementIndex("local"), Expression.serializer(), localPath)
        output.encodeSerializableElement(descriptor, descriptor.getElementIndex("pathResolutionContext"), PathResolutionContextSerializer, pathResolutionContext)
    }
}

data class CacheMount(
    val name: String,
    override val containerPath: String,
    override val options: String? = null
) : VolumeMount(containerPath, options) {
    private val descriptor: SerialDescriptor = buildClassSerialDescriptor("VolumeMount") {
        element("name", Expression.serializer().descriptor)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(output: CompositeEncoder) {
        output.encodeStringElement(descriptor, descriptor.getElementIndex("name"), name)
    }
}
