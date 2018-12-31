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
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.Serializer
import kotlinx.serialization.internal.HashSetClassDesc
import kotlinx.serialization.internal.StringSerializer

@Serializer(forClass = Set::class)
internal object DependencySetDeserializer : KSerializer<Set<String>> {
    val elementSerializer = StringSerializer

    override val descriptor: SerialDescriptor = HashSetClassDesc(elementSerializer.descriptor)

    override fun deserialize(input: Decoder): Set<String> {
        val structureInput = input.beginStructure(descriptor, elementSerializer)
        val result = read(structureInput)

        structureInput.endStructure(descriptor)

        return result
    }

    private fun read(input: CompositeDecoder): Set<String> {
        val size = input.decodeCollectionSize(descriptor)

        while (true) {
            when (val index = input.decodeElementIndex(descriptor)) {
                CompositeDecoder.READ_ALL -> return readAll(input, size)
                CompositeDecoder.READ_DONE -> return emptySet()
                else -> return readUntilDone(input, index)
            }
        }
    }

    private fun readAll(input: CompositeDecoder, size: Int): Set<String> {
        val soFar = mutableSetOf<String>()

        for (currentIndex in 0..size) {
            soFar.add(readSingle(input, currentIndex, soFar))
        }

        return soFar
    }

    private fun readUntilDone(input: CompositeDecoder, firstIndex: Int): Set<String> {
        var currentIndex = firstIndex
        val soFar = mutableSetOf<String>()

        do {
            soFar.add(readSingle(input, currentIndex, soFar))

            currentIndex = input.decodeElementIndex(descriptor)
        } while (currentIndex != CompositeDecoder.READ_DONE)

        return soFar
    }

    private fun readSingle(input: CompositeDecoder, index: Int, soFar: Set<String>): String {
        val value = input.decodeSerializableElement(descriptor, index, elementSerializer)

        if (value in soFar) {
            val location = (input as YamlInput).getCurrentLocation()

            throw ConfigurationException(getDuplicateValueMessage(value), null, location.line, location.column, null)
        }

        return value
    }

    private fun getDuplicateValueMessage(value: String) = "The dependency '$value' is given more than once"

    override fun serialize(output: Encoder, obj: Set<String>) = throw UnsupportedOperationException()
}
