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
import batect.os.PathResolutionResult
import batect.os.PathType
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PrimitiveDescriptor
import kotlinx.serialization.PrimitiveKind
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.decode
import java.nio.file.Path

@Serializable(with = FileInclude.Companion::class)
data class FileInclude(val path: Path) {
    override fun toString(): String = path.toString()

    @Serializer(forClass = FileInclude::class)
    companion object : KSerializer<FileInclude> {
        override val descriptor: SerialDescriptor = PrimitiveDescriptor("path", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): FileInclude {
            val elementDeserializer = decoder.context.getContextual(PathResolutionResult::class)!!
            val resolutionResult = decoder.decode(elementDeserializer)
            val location = (decoder as YamlInput).getCurrentLocation()

            when (resolutionResult) {
                is PathResolutionResult.Resolved -> when (resolutionResult.pathType) {
                    PathType.File -> return FileInclude(resolutionResult.absolutePath)
                    PathType.DoesNotExist -> throw ConfigurationException("Included file '${resolutionResult.originalPath}' (resolved to '${resolutionResult.absolutePath}') does not exist.", location.line, location.column)
                    else -> throw ConfigurationException("'${resolutionResult.originalPath}' (resolved to '${resolutionResult.absolutePath}') is not a file.", location.line, location.column)
                }
                is PathResolutionResult.InvalidPath -> throw ConfigurationException("Include path '${resolutionResult.originalPath}' is not a valid path.", location.line, location.column)
            }
        }

        override fun serialize(encoder: Encoder, value: FileInclude) {
            encoder.encodeString(value.path.toString())
        }
    }
}
