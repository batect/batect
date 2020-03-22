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

package batect.config.io.deserializers

import batect.config.io.ConfigurationException
import batect.os.PathResolutionResult
import batect.os.PathType
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.CompositeDecoder
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.set
import java.nio.file.Path

object IncludeSetSerializer : KSerializer<Set<Path>> {
    private val elementSerializer = String.serializer()
    private val elementDescriptor = elementSerializer.descriptor
    override val descriptor: SerialDescriptor = elementSerializer.set.descriptor

    override fun deserialize(decoder: Decoder): Set<Path> {
        val input = decoder.beginStructure(descriptor, elementSerializer)
        val result = read(input)

        input.endStructure(descriptor)

        return result
    }

    private fun read(input: CompositeDecoder): Set<Path> {
        val soFar = mutableSetOf<Path>()

        while (true) {
            val currentIndex = input.decodeElementIndex(descriptor)

            if (currentIndex == CompositeDecoder.READ_DONE) {
                break
            }

            soFar.add(readSingle(input, currentIndex))
        }

        return soFar
    }

    private fun readSingle(input: CompositeDecoder, index: Int): Path {
        val elementDeserializer = input.context.getContextual(PathResolutionResult::class)!!
        val resolutionResult = input.decodeSerializableElement(elementDescriptor, index, elementDeserializer)
        val location = (input as YamlInput).getCurrentLocation()

        when (resolutionResult) {
            is PathResolutionResult.Resolved -> when (resolutionResult.pathType) {
                PathType.File -> return resolutionResult.absolutePath
                PathType.DoesNotExist -> throw ConfigurationException("Included file '${resolutionResult.originalPath}' (resolved to '${resolutionResult.absolutePath}') does not exist.", location.line, location.column)
                else -> throw ConfigurationException("'${resolutionResult.originalPath}' (resolved to '${resolutionResult.absolutePath}') is not a file.", location.line, location.column)
            }
            is PathResolutionResult.InvalidPath -> throw ConfigurationException("Include path '${resolutionResult.originalPath}' is not a valid path.", location.line, location.column)
        }
    }

    override fun serialize(encoder: Encoder, value: Set<Path>) {
        val output = encoder.beginCollection(descriptor, value.size)

        value.forEachIndexed { index, path ->
            output.encodeStringElement(elementDescriptor, index, path.toString())
        }

        output.endStructure(descriptor)
    }
}
