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
import com.charleskorn.kaml.Location
import com.charleskorn.kaml.YamlInput
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PrimitiveKind
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.UnionKind
import kotlinx.serialization.decode

abstract class StringOrObjectSerializer<T> : KSerializer<T> {
    abstract val serialName: String
    protected val stringDescriptor = SerialDescriptor("value", PrimitiveKind.STRING)
    abstract val objectDescriptor: SerialDescriptor
    abstract val neitherStringNorObjectErrorMessage: String

    final override val descriptor: SerialDescriptor by lazy {
        SerialDescriptor(serialName, UnionKind.CONTEXTUAL) {
            element("object", objectDescriptor)
            element("string", stringDescriptor)
        }
    }

    final override fun deserialize(decoder: Decoder): T {
        if (decoder !is YamlInput) {
            throw UnsupportedOperationException("Can only deserialize from YAML source.")
        }

        val input = decoder.beginStructure(descriptor) as YamlInput

        val result = when (input.node) {
            is YamlScalar -> beginAndDecodeString(input)
            is YamlMap -> beginAndDecodeObject(input)
            else -> throw ConfigurationException(neitherStringNorObjectErrorMessage, decoder.node.location.line, decoder.node.location.column)
        }

        input.endStructure(descriptor)

        validateDeserializedObject(result, input.node.location)

        return result
    }

    private fun beginAndDecodeString(decoder: YamlInput): T {
        val input = decoder.beginStructure(stringDescriptor) as YamlInput
        val value = deserializeFromString(input.decodeString(), input)
        input.endStructure(objectDescriptor)
        return value
    }

    private fun beginAndDecodeObject(decoder: YamlInput): T {
        val input = decoder.beginStructure(objectDescriptor) as YamlInput
        val value = deserializeFromObject(input)
        input.endStructure(objectDescriptor)
        return value
    }

    protected abstract fun deserializeFromString(value: String, input: YamlInput): T
    protected abstract fun deserializeFromObject(input: YamlInput): T

    protected open fun validateDeserializedObject(value: T, location: Location) {}
}

abstract class SimpleStringOrObjectSerializer<T>(val objectSerializer: KSerializer<T>) : StringOrObjectSerializer<T>() {
    override val serialName: String = objectSerializer.descriptor.serialName
    override val objectDescriptor = objectSerializer.descriptor
    override fun deserializeFromObject(input: YamlInput): T = input.decode(objectSerializer)
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeSerializableValue(objectSerializer, value)
}
