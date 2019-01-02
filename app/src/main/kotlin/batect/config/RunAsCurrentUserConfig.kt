/*
   Copyright 2017-2019 Charles Korn.

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
import kotlinx.serialization.CompositeDecoder
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.internal.SerialClassDescImpl

@Serializable
sealed class RunAsCurrentUserConfig() {
    @Serializer(forClass = RunAsCurrentUserConfig::class)
    companion object : KSerializer<RunAsCurrentUserConfig> {
        override val descriptor: SerialDescriptor = object : SerialClassDescImpl("RunAsCurrentUserConfig") {
            init {
                addElement("enabled", isOptional = true)
                addElement("home_directory", isOptional = true)
            }
        }

        private val enabledFieldIndex = descriptor.getElementIndex("enabled")
        private val homeDirectoryFieldIndex = descriptor.getElementIndex("home_directory")

        override fun deserialize(input: Decoder): RunAsCurrentUserConfig = when (input) {
            is YamlInput -> {
                val inp = input.beginStructure(descriptor) as YamlInput

                deserializeFromObject(inp).also {
                    inp.endStructure(descriptor)
                }
            }
            else -> throw UnsupportedOperationException("Can only deserialize from YAML source.")
        }

        private fun deserializeFromObject(input: YamlInput): RunAsCurrentUserConfig {
            var enabled = false
            var homeDirectory = null as String?

            loop@ while (true) {
                when (val i = input.decodeElementIndex(descriptor)) {
                    CompositeDecoder.READ_DONE -> break@loop
                    enabledFieldIndex -> enabled = input.decodeBooleanElement(descriptor, i)
                    homeDirectoryFieldIndex -> homeDirectory = input.decodeStringElement(descriptor, i)
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            if (enabled) {
                if (homeDirectory == null) {
                    throw ConfigurationException("Running as the current user has been enabled, but a home directory for that user has not been provided.", input.node.location.line, input.node.location.column)
                }

                return RunAsCurrentUser(homeDirectory)
            } else {
                if (homeDirectory != null) {
                    throw ConfigurationException("Running as the current user has not been enabled, but a home directory for that user has been provided.", input.node.location.line, input.node.location.column)
                }

                return RunAsDefaultContainerUser
            }
        }

        override fun serialize(output: Encoder, obj: RunAsCurrentUserConfig) = throw UnsupportedOperationException()
    }

    object RunAsDefaultContainerUser : RunAsCurrentUserConfig()
    data class RunAsCurrentUser(val homeDirectory: String) : RunAsCurrentUserConfig()
}
