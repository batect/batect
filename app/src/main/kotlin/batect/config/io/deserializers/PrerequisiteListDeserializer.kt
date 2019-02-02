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

package batect.config.io.deserializers

import batect.config.io.ConfigurationException
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.CompositeDecoder
import kotlinx.serialization.CompositeDecoder.Companion.READ_ALL
import kotlinx.serialization.CompositeDecoder.Companion.READ_DONE
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.Serializer
import kotlinx.serialization.internal.ArrayListClassDesc
import kotlinx.serialization.internal.StringSerializer

@Serializer(forClass = List::class)
internal object PrerequisiteListDeserializer : KSerializer<List<String>> {
    val elementSerializer = StringSerializer

    override val descriptor: SerialDescriptor = ArrayListClassDesc(elementSerializer.descriptor)

    override fun deserialize(decoder: Decoder): List<String> {
        val input = decoder.beginStructure(descriptor, elementSerializer)
        val result = read(input)

        input.endStructure(descriptor)

        return result
    }

    private fun read(input: CompositeDecoder): List<String> {
        val size = input.decodeCollectionSize(descriptor)

        while (true) {
            when (val index = input.decodeElementIndex(descriptor)) {
                READ_ALL -> return readAll(input, size)
                else -> return readUntilDone(input, index)
            }
        }
    }

    private fun readAll(input: CompositeDecoder, size: Int): List<String> {
        val soFar = mutableListOf<String>()

        for (currentIndex in 0..size) {
            soFar.add(readSingle(input, currentIndex, soFar))
        }

        return soFar
    }

    private fun readUntilDone(input: CompositeDecoder, firstIndex: Int): List<String> {
        var currentIndex = firstIndex
        val soFar = mutableListOf<String>()

        while (currentIndex != READ_DONE) {
            soFar.add(currentIndex, readSingle(input, currentIndex, soFar))

            currentIndex = input.decodeElementIndex(descriptor)
        }

        return soFar
    }

    private fun readSingle(input: CompositeDecoder, index: Int, soFar: List<String>): String {
        val value = input.decodeSerializableElement(descriptor, index, elementSerializer)

        if (value in soFar) {
            val location = (input as YamlInput).getCurrentLocation()

            throw ConfigurationException(getDuplicateValueMessage(value), location.line, location.column)
        }

        return value
    }

    private fun getDuplicateValueMessage(value: String) = "The prerequisite '$value' is given more than once"

    override fun serialize(encoder: Encoder, obj: List<String>) = throw UnsupportedOperationException()
}
