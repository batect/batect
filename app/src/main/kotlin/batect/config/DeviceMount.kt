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
import batect.docker.DockerDeviceMount
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.CompositeDecoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.nullable

@Serializable(with = DeviceMount.Companion::class)
data class DeviceMount(
    val localPath: String,
    val containerPath: String,
    val options: String? = null
) {
    override fun toString(): String {
        if (options == null) {
            return "$localPath:$containerPath"
        } else {
            return "$localPath:$containerPath:$options"
        }
    }

    fun toDockerMount() = DockerDeviceMount(localPath, containerPath, options)

    companion object : StringOrObjectSerializer<DeviceMount>() {
        override val serialName: String = DeviceMount::class.qualifiedName!!
        override val neitherStringNorObjectErrorMessage: String = "Device mount definition is invalid. It must either be an object or a literal in the form 'local_path:container_path' or 'local_path:container_path:options'."

        override val objectDescriptor: SerialDescriptor = SerialDescriptor("DeviceMount") {
            element("local", String.serializer().descriptor)
            element("container", String.serializer().descriptor)
            element("options", String.serializer().descriptor.nullable, isOptional = true)
        }

        private val localPathFieldIndex = objectDescriptor.getElementIndex("local")
        private val containerPathFieldIndex = objectDescriptor.getElementIndex("container")
        private val optionsFieldIndex = objectDescriptor.getElementIndex("options")

        override fun deserializeFromString(value: String, input: YamlInput): DeviceMount {
            if (value == "") {
                throw ConfigurationException("Device mount definition cannot be empty.", input.node.location.line, input.node.location.column)
            }

            val regex = """(([a-zA-Z]:\\)?[^:]+):([^:]+)(:([^:]+))?""".toRegex()
            val match = regex.matchEntire(value)

            if (match == null) {
                throw invalidMountDefinitionException(value, input)
            }

            val local = match.groupValues[1]
            val container = match.groupValues[3]
            val options = match.groupValues[5].takeIf { it.isNotEmpty() }

            return DeviceMount(local, container, options)
        }

        private fun invalidMountDefinitionException(value: String, input: YamlInput) =
            ConfigurationException(
                "Device mount definition '$value' is invalid. It must be in the form 'local_path:container_path' or 'local_path:container_path:options'.",
                input.node.location.line,
                input.node.location.column
            )

        override fun deserializeFromObject(input: YamlInput): DeviceMount {
            var localPath: String? = null
            var containerPath: String? = null
            var options: String? = null

            loop@ while (true) {
                when (val i = input.decodeElementIndex(objectDescriptor)) {
                    CompositeDecoder.READ_DONE -> break@loop
                    localPathFieldIndex -> localPath = input.decodeStringElement(objectDescriptor, i)
                    containerPathFieldIndex -> containerPath = input.decodeStringElement(objectDescriptor, i)
                    optionsFieldIndex -> options = input.decodeStringElement(objectDescriptor, i)
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            if (localPath == null) {
                throw ConfigurationException("Field '${objectDescriptor.getElementName(localPathFieldIndex)}' is required but it is missing.", input.node.location.line, input.node.location.column)
            }

            if (containerPath == null) {
                throw ConfigurationException("Field '${objectDescriptor.getElementName(containerPathFieldIndex)}' is required but it is missing.", input.node.location.line, input.node.location.column)
            }

            return DeviceMount(localPath, containerPath, options)
        }

        override fun serialize(encoder: Encoder, value: DeviceMount) {
            val output = encoder.beginStructure(objectDescriptor)

            output.encodeStringElement(objectDescriptor, localPathFieldIndex, value.localPath)
            output.encodeStringElement(objectDescriptor, containerPathFieldIndex, value.containerPath)
            output.encodeSerializableElement(objectDescriptor, optionsFieldIndex, String.serializer().nullable, value.options)

            output.endStructure(objectDescriptor)
        }
    }
}
