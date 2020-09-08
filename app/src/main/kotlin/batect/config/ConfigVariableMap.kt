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

@Serializable(with = ConfigVariableMap.Companion::class)
class ConfigVariableMap(contents: Iterable<ConfigVariableDefinition>) : NamedObjectMap<ConfigVariableDefinition>("config variable", contents) {
    constructor(vararg contents: ConfigVariableDefinition) : this(contents.asIterable())

    override fun nameFor(value: ConfigVariableDefinition): String = value.name

    companion object : NamedObjectMapSerializer<ConfigVariableMap, ConfigVariableDefinition>(ConfigVariableDefinition.serializer()), KSerializer<ConfigVariableMap> {
        override fun addName(name: String, element: ConfigVariableDefinition): ConfigVariableDefinition = element.copy(name = name)
        override fun getName(element: ConfigVariableDefinition): String = element.name
        override fun createCollection(elements: Set<ConfigVariableDefinition>): ConfigVariableMap = ConfigVariableMap(elements)

        override val descriptor: SerialDescriptor = MapSerializer(keySerializer, elementSerializer).descriptor

        private val validNameRegex = """^[a-zA-Z][a-zA-Z0-9._-]*$""".toRegex()

        override fun validateName(name: String, location: Location) {
            if (name.startsWith("batect", ignoreCase = true) || !validNameRegex.matches(name)) {
                throw ConfigurationException("Invalid config variable name '$name'. Config variable names must start with a letter, contain only letters, digits, dashes, periods and underscores, and must not start with 'batect'.", location.line, location.column)
            }
        }
    }
}
