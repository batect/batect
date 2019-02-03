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

import kotlinx.serialization.CompositeDecoder
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.internal.HashMapClassDesc
import kotlinx.serialization.internal.StringSerializer

@Serializable(with = TaskMap.Companion::class)
class TaskMap(contents: Iterable<Task>) : NamedObjectMap<Task>("task", contents) {
    constructor(vararg contents: Task) : this(contents.asIterable())

    override fun nameFor(value: Task): String = value.name

    @Serializer(forClass = TaskMap::class)
    companion object : KSerializer<TaskMap> {
        private val keySerializer = StringSerializer
        private val elementSerializer = Task.serializer()
        override val descriptor: SerialDescriptor = HashMapClassDesc(keySerializer.descriptor, elementSerializer.descriptor)

        override fun deserialize(decoder: Decoder): TaskMap {
            val input = decoder.beginStructure(descriptor)

            return read(input).also { input.endStructure(descriptor) }
        }

        private fun read(input: CompositeDecoder): TaskMap {
            val size = input.decodeCollectionSize(descriptor)

            while (true) {
                when (val index = input.decodeElementIndex(descriptor)) {
                    CompositeDecoder.READ_ALL -> return readAll(input, size)
                    else -> return readUntilDone(input, index)
                }
            }
        }

        private fun readAll(input: CompositeDecoder, size: Int): TaskMap {
            val soFar = mutableSetOf<Task>()

            for (currentIndex in 0..size) {
                soFar.add(readSingle(input, currentIndex, false))
            }

            return TaskMap(soFar)
        }

        private fun readUntilDone(input: CompositeDecoder, firstIndex: Int): TaskMap {
            var currentIndex = firstIndex
            val soFar = mutableSetOf<Task>()

            while (currentIndex != CompositeDecoder.READ_DONE) {
                soFar.add(readSingle(input, currentIndex, true))

                currentIndex = input.decodeElementIndex(descriptor)
            }

            return TaskMap(soFar)
        }

        private fun readSingle(input: CompositeDecoder, index: Int, checkIndex: Boolean): Task {
            val name = input.decodeSerializableElement(descriptor, index, keySerializer)

            val valueIndex = if (checkIndex) {
                input.decodeElementIndex(descriptor)
            } else {
                index + 1
            }

            val unnamed = input.decodeSerializableElement(descriptor, valueIndex, elementSerializer)

            return unnamed.copy(name = name)
        }

        override fun serialize(encoder: Encoder, obj: TaskMap) = throw UnsupportedOperationException()
    }
}
