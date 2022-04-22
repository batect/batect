/*
    Copyright 2017-2022 Charles Korn.

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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal object DependencySetSerializer : KSerializer<Set<String>> {
    private val elementSerializer = String.serializer()

    override val descriptor: SerialDescriptor = SetSerializer(elementSerializer).descriptor

    override fun deserialize(decoder: Decoder): Set<String> {
        val input = decoder.beginStructure(descriptor)
        val result = read(input)

        input.endStructure(descriptor)

        return result
    }

    private fun read(input: CompositeDecoder): Set<String> {
        val soFar = mutableSetOf<String>()

        while (true) {
            val currentIndex = input.decodeElementIndex(descriptor)

            if (currentIndex == CompositeDecoder.DECODE_DONE) {
                break
            }

            soFar.add(readSingle(input, currentIndex, soFar))
        }

        return soFar
    }

    private fun readSingle(input: CompositeDecoder, index: Int, soFar: Set<String>): String {
        val value = input.decodeSerializableElement(descriptor, index, elementSerializer)

        if (value in soFar) {
            throw ConfigurationException(getDuplicateValueMessage(value), input as YamlInput)
        }

        return value
    }

    private fun getDuplicateValueMessage(value: String) = "The dependency '$value' is given more than once"

    override fun serialize(encoder: Encoder, value: Set<String>) = SetSerializer(String.serializer()).serialize(encoder, value)
}
