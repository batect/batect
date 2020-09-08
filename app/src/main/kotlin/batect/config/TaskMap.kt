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
import com.charleskorn.kaml.Location
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor

@Serializable(with = TaskMap.Companion::class)
class TaskMap(contents: Iterable<Task>) : NamedObjectMap<Task>("task", contents) {
    constructor(vararg contents: Task) : this(contents.asIterable())

    override fun nameFor(value: Task): String = value.name

    companion object : NamedObjectMapSerializer<TaskMap, Task>(Task.serializer()), KSerializer<TaskMap> {
        override fun addName(name: String, element: Task): Task = element.copy(name = name)
        override fun getName(element: Task): String = element.name
        override fun createCollection(elements: Set<Task>): TaskMap = TaskMap(elements)

        override val descriptor: SerialDescriptor = MapSerializer(keySerializer, elementSerializer).descriptor

        private const val letterOrDigit = "a-zA-Z0-9"
        private val validNameRegex = """^[$letterOrDigit]([$letterOrDigit._:-]*[$letterOrDigit])*$""".toRegex()

        override fun validateName(name: String, location: Location) {
            if (!validNameRegex.matches(name)) {
                throw ConfigurationException("Invalid task name '$name'. Task names must contain only letters, digits, colons, dashes, periods and underscores, and must start and end with a letter or digit.", location.line, location.column)
            }
        }
    }
}
