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
import batect.docker.ImageNameValidator
import com.charleskorn.kaml.YamlPath
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor

@Serializable(with = ContainerMap.Companion::class)
class ContainerMap(contents: Iterable<Container>) : NamedObjectMap<Container>("container", contents) {
    constructor(vararg contents: Container) : this(contents.asIterable())

    override fun nameFor(value: Container): String = value.name

    companion object : NamedObjectMapSerializer<ContainerMap, Container>(Container.serializer()), KSerializer<ContainerMap> {
        override fun addName(name: String, element: Container): Container = element.copy(name = name)
        override fun getName(element: Container): String = element.name
        override fun createCollection(elements: Set<Container>): ContainerMap = ContainerMap(elements)

        override fun validateName(name: String, path: YamlPath) {
            if (!ImageNameValidator.isValidImageName(name)) {
                throw ConfigurationException(
                    "Invalid container name '$name'. Container names must be valid Docker references: they ${ImageNameValidator.validNameDescription}.",
                    path
                )
            }
        }

        override val descriptor: SerialDescriptor = MapSerializer(keySerializer, elementSerializer).descriptor
    }
}
