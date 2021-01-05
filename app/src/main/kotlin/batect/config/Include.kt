/*
   Copyright 2017-2021 Charles Korn.

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

import batect.config.includes.GitRepositoryReference
import batect.config.io.ConfigurationException
import batect.config.io.deserializers.SimpleStringOrObjectSerializer
import batect.os.PathResolutionResult
import batect.os.PathType
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.Path

@Serializable
sealed class Include

@Serializable
@SerialName("file")
data class FileInclude(
    @Serializable(with = FileIncludePathSerializer::class) val path: Path
) : Include() {
    override fun toString(): String = path.toString()
}

@Serializable
@SerialName("git")
data class GitInclude(
    val repo: String,
    val ref: String,
    val path: String = "batect-bundle.yml"
) : Include() {
    override fun toString(): String = "$repo@$ref: $path"

    @Transient
    val repositoryReference = GitRepositoryReference(repo, ref)
}

object FileIncludePathSerializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Path", PrimitiveKind.STRING)

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): Path {
        val elementDeserializer = decoder.serializersModule.getContextual(PathResolutionResult::class)!!

        when (val resolutionResult = decoder.decodeSerializableValue(elementDeserializer)) {
            is PathResolutionResult.Resolved -> when (resolutionResult.pathType) {
                PathType.File -> return resolutionResult.absolutePath
                PathType.DoesNotExist -> throw ConfigurationException("Included file '${resolutionResult.originalPath}' (${resolutionResult.resolutionDescription}) does not exist.", decoder as YamlInput)
                else -> throw ConfigurationException("'${resolutionResult.originalPath}' (${resolutionResult.resolutionDescription}) is not a file.", decoder as YamlInput)
            }
            is PathResolutionResult.InvalidPath -> throw ConfigurationException("Include path '${resolutionResult.originalPath}' is not a valid path.", decoder as YamlInput)
        }
    }

    override fun serialize(encoder: Encoder, value: Path) = encoder.encodeString(value.toString())
}

object IncludeConfigSerializer : SimpleStringOrObjectSerializer<Include>(Include.serializer()) {
    override val neitherStringNorObjectErrorMessage: String = "Include definition is invalid. It must either be a YAML object or a path to a file."

    override fun deserializeFromString(value: String, input: YamlInput): Include {
        val path = FileIncludePathSerializer.deserialize(input)

        return FileInclude(path)
    }
}

object IncludeSetConfigSerializer : KSerializer<Set<Include>> by SetSerializer(IncludeConfigSerializer)
