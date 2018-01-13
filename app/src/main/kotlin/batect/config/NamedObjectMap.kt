/*
   Copyright 2017-2018 Charles Korn.

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

    // Set members
    override fun contains(element: E): Boolean = containsValue(element)
    override fun containsAll(elements: Collection<E>): Boolean = values.containsAll(elements)
    override fun iterator(): Iterator<E> = values.iterator()

    abstract fun nameFor(value: E): String

    override fun equals(other: Any?): Boolean = implementation == other
    override fun hashCode(): Int = implementation.hashCode()
}
