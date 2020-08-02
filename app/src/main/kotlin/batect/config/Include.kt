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

import batect.config.includes.GitRepositoryReference
import batect.config.io.ConfigurationException
import batect.config.io.deserializers.SimpleStringOrObjectSerializer
import batect.os.PathResolutionResult
import batect.os.PathType
import com.charleskorn.kaml.YamlInput
import java.nio.file.Path
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PrimitiveKind
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.set
import kotlinx.serialization.decode

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
    override val descriptor: SerialDescriptor = SerialDescriptor("Path", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Path {
        val elementDeserializer = decoder.context.getContextual(PathResolutionResult::class)!!
        val resolutionResult = decoder.decode(elementDeserializer)
        val location = (decoder as YamlInput).getCurrentLocation()

        when (resolutionResult) {
            is PathResolutionResult.Resolved -> when (resolutionResult.pathType) {
                PathType.File -> return resolutionResult.absolutePath
                PathType.DoesNotExist -> throw ConfigurationException("Included file '${resolutionResult.originalPath}' (${resolutionResult.resolutionDescription}) does not exist.", location.line, location.column)
                else -> throw ConfigurationException("'${resolutionResult.originalPath}' (${resolutionResult.resolutionDescription}) is not a file.", location.line, location.column)
            }
            is PathResolutionResult.InvalidPath -> throw ConfigurationException("Include path '${resolutionResult.originalPath}' is not a valid path.", location.line, location.column)
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

object IncludeSetConfigSerializer : KSerializer<Set<Include>> by IncludeConfigSerializer.set
