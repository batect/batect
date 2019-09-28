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

import com.charleskorn.kaml.Location
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.CompositeDecoder
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.map

abstract class NamedObjectMap<E>(contentName: String, contents: Iterable<E>) : Map<String, E>, Set<E> {
    init {
        val duplicates = contents
            .groupBy { nameFor(it) }
            .filter { it.value.size > 1 }
            .map { it.key }

        if (duplicates.isNotEmpty()) {
            throw IllegalArgumentException("Cannot create a ${this.javaClass.simpleName} where a $contentName name is used more than once. Duplicated $contentName names: ${duplicates.joinToString(", ")}")
        }
    }

    private val implementation: Map<String, E> = contents.associateBy { nameFor(it) }

    // Map members
    override val entries: Set<Map.Entry<String, E>>
        get() = implementation.entries

    override val keys: Set<String>
        get() = implementation.keys

    override val values: Collection<E>
        get() = implementation.values

    override val size: Int
        get() = implementation.size

    override fun containsKey(key: String): Boolean = implementation.containsKey(key)
    override fun containsValue(value: E): Boolean = implementation.containsValue(value)
    override fun get(key: String): E? = implementation[key]
    override fun isEmpty(): Boolean = implementation.isEmpty()

    override fun contains(element: E): Boolean = containsValue(element)
    override fun containsAll(elements: Collection<E>): Boolean = values.containsAll(elements)
    override fun iterator(): Iterator<E> = values.iterator()

    abstract fun nameFor(value: E): String

    override fun equals(other: Any?): Boolean = implementation == other
    override fun hashCode(): Int = implementation.hashCode()
}

abstract class NamedObjectMapSerializer<TCollection : Iterable<TElement>, TElement>(val elementSerializer: KSerializer<TElement>) {
    val keySerializer = StringSerializer

    // We can't just declare the value of this here due to https://github.com/Kotlin/kotlinx.serialization/issues/315#issuecomment-460015206
    abstract val descriptor: SerialDescriptor

    fun deserialize(decoder: Decoder): TCollection {
        val input = decoder.beginStructure(descriptor)

        return read(input).also { input.endStructure(descriptor) }
    }

    private fun read(input: CompositeDecoder): TCollection {
        val size = input.decodeCollectionSize(descriptor)

        while (true) {
            when (val index = input.decodeElementIndex(descriptor)) {
                CompositeDecoder.READ_ALL -> return readAll(input, size)
                else -> return readUntilDone(input, index)
            }
        }
    }

    private fun readAll(input: CompositeDecoder, size: Int): TCollection {
        val soFar = mutableSetOf<TElement>()

        for (currentIndex in 0..size) {
            soFar.add(readSingle(input, currentIndex, false))
        }

        return createCollection(soFar)
    }

    private fun readUntilDone(input: CompositeDecoder, firstIndex: Int): TCollection {
        var currentIndex = firstIndex
        val soFar = mutableSetOf<TElement>()

        while (currentIndex != CompositeDecoder.READ_DONE) {
            soFar.add(readSingle(input, currentIndex, true))

            currentIndex = input.decodeElementIndex(descriptor)
        }

        return createCollection(soFar)
    }

    private fun readSingle(input: CompositeDecoder, index: Int, checkIndex: Boolean): TElement {
        val nameLocation = (input as YamlInput).getCurrentLocation()
        val name = input.decodeSerializableElement(descriptor, index, keySerializer)

        validateName(name, nameLocation)

        val valueIndex = if (checkIndex) {
            input.decodeElementIndex(descriptor)
        } else {
            index + 1
        }

        val unnamed = input.decodeSerializableElement(descriptor, valueIndex, elementSerializer)

        return addName(name, unnamed)
    }

    @Suppress("UNUSED_PARAMETER")
    fun serialize(encoder: Encoder, obj: TCollection) {
        val output = encoder.beginCollection(descriptor, obj.count())

        obj.forEachIndexed { index, element ->
            output.encodeSerializableElement(descriptor, 2*index, keySerializer, getName(element))
            output.encodeSerializableElement(descriptor, 2*index + 1, elementSerializer, element)
        }

        output.endStructure(descriptor)
    }

    open fun validateName(name: String, location: Location) {}
    protected abstract fun addName(name: String, element: TElement): TElement
    protected abstract fun createCollection(elements: Set<TElement>): TCollection
    protected abstract fun getName(element: TElement): String
}
