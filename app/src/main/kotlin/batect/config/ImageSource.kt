/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.config

import batect.config.io.ConfigurationException
import batect.os.PathResolutionContext
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

sealed class ImageSource {
    abstract val imagePullPolicy: ImagePullPolicy
}

data class BuildImage(
    val buildDirectory: Expression,
    val pathResolutionContext: PathResolutionContext,
    val buildArgs: Map<String, Expression> = emptyMap(),
    val dockerfilePath: String = "Dockerfile",
    override val imagePullPolicy: ImagePullPolicy = ImagePullPolicy.IfNotPresent,
    val targetStage: String? = null,
    val sshAgents: Set<SSHAgent> = emptySet(),
    val secrets: Map<String, BuildSecret> = emptyMap()
) : ImageSource()

@Serializable
data class SSHAgent(
    val id: String,
    val paths: Set<Expression> = emptySet()
)

@Serializable(with = BuildSecret.Companion::class)
sealed interface BuildSecret {
    @OptIn(ExperimentalSerializationApi::class)
    companion object : KSerializer<BuildSecret> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor(BuildSecret::class.simpleName!!) {
            element("environment", String.serializer().descriptor, isOptional = true)
            element("path", Expression.serializer().descriptor, isOptional = true)
        }

        private val environmentFieldIndex = descriptor.getElementIndex("environment")
        private val pathFieldIndex = descriptor.getElementIndex("path")

        override fun deserialize(decoder: Decoder): BuildSecret {
            val input = decoder.beginStructure(descriptor)

            return deserializeObject(input as YamlInput).also {
                input.endStructure(descriptor)
            }
        }

        private fun deserializeObject(input: YamlInput): BuildSecret {
            var environment: String? = null
            var path: Expression? = null

            loop@ while (true) {
                when (val i = input.decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break@loop
                    environmentFieldIndex -> environment = input.decodeStringElement(descriptor, i)
                    pathFieldIndex -> path = input.decodeSerializableElement(descriptor, i, Expression.serializer())
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            if (environment != null && path != null) {
                throw ConfigurationException("A secret can have either 'environment' or 'path', but both have been provided.", input.node.path)
            }

            if (environment != null) {
                return EnvironmentSecret(environment)
            }

            if (path != null) {
                return FileSecret(path)
            }

            throw ConfigurationException("A secret must have either 'environment' or 'path', but neither has been provided.", input.node.path)
        }

        override fun serialize(encoder: Encoder, value: BuildSecret) {
            when (value) {
                is EnvironmentSecret -> EnvironmentSecret.serializer().serialize(encoder, value)
                is FileSecret -> FileSecret.serializer().serialize(encoder, value)
            }
        }
    }
}

@Serializable
data class EnvironmentSecret(
    @SerialName("environment") val sourceEnvironmentVariableName: String
) : BuildSecret

@Serializable
data class FileSecret(
    @SerialName("path") val sourceFile: Expression
) : BuildSecret

@Serializable
data class PullImage(
    val imageName: String,
    override val imagePullPolicy: ImagePullPolicy = ImagePullPolicy.IfNotPresent
) : ImageSource()
