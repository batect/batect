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
import batect.config.io.deserializers.DependencySetSerializer
import batect.config.io.deserializers.PrerequisiteListSerializer
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = Task.Companion::class)
data class Task(
    val name: String = "",
    val runConfiguration: TaskRunConfiguration?,
    val description: String = "",
    val group: String = "",
    val dependsOnContainers: Set<String> = emptySet(),
    val prerequisiteTasks: List<String> = emptyList()
) {
    @OptIn(ExperimentalSerializationApi::class)
    companion object : KSerializer<Task> {
        private const val runConfigurationFieldName = "run"
        private const val descriptionFieldName = "description"
        private const val groupFieldName = "group"
        private const val dependsOnContainersFieldName = "dependencies"
        private const val prerequisiteTasksFieldName = "prerequisites"

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Task") {
            element(runConfigurationFieldName, TaskRunConfiguration.serializer().descriptor, isOptional = true)
            element(descriptionFieldName, String.serializer().descriptor, isOptional = true)
            element(groupFieldName, String.serializer().descriptor, isOptional = true)
            element(dependsOnContainersFieldName, DependencySetSerializer.descriptor, isOptional = true)
            element(prerequisiteTasksFieldName, PrerequisiteListSerializer.descriptor, isOptional = true)
        }

        private val runConfigurationFieldIndex = descriptor.getElementIndex(runConfigurationFieldName)
        private val descriptionFieldIndex = descriptor.getElementIndex(descriptionFieldName)
        private val groupFieldIndex = descriptor.getElementIndex(groupFieldName)
        private val dependsOnContainersFieldIndex = descriptor.getElementIndex(dependsOnContainersFieldName)
        private val prerequisiteTasksFieldIndex = descriptor.getElementIndex(prerequisiteTasksFieldName)

        override fun deserialize(decoder: Decoder): Task {
            val input = decoder.beginStructure(descriptor) as YamlInput

            return deserializeFromObject(input).also { input.endStructure(descriptor) }
        }

        private fun deserializeFromObject(input: YamlInput): Task {
            val location = input.getCurrentLocation()

            var runConfiguration: TaskRunConfiguration? = null
            var description = ""
            var group = ""
            var dependsOnContainers: Set<String> = emptySet()
            var prerequisiteTasks: List<String> = emptyList()

            loop@ while (true) {
                when (val i = input.decodeElementIndex(descriptor)) {
                    runConfigurationFieldIndex -> runConfiguration = input.decodeSerializableValue(TaskRunConfiguration.serializer())
                    descriptionFieldIndex -> description = input.decodeStringElement(descriptor, i)
                    groupFieldIndex -> group = input.decodeStringElement(descriptor, i)
                    dependsOnContainersFieldIndex -> dependsOnContainers = input.decodeSerializableValue(DependencySetSerializer)
                    prerequisiteTasksFieldIndex -> prerequisiteTasks = input.decodeSerializableValue(PrerequisiteListSerializer)

                    CompositeDecoder.DECODE_DONE -> break@loop
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            if (runConfiguration == null && prerequisiteTasks.isEmpty()) {
                throw ConfigurationException("At least one of '$runConfigurationFieldName' or '$prerequisiteTasksFieldName' is required.", location.line, location.column)
            }

            if (dependsOnContainers.isNotEmpty() && runConfiguration == null) {
                throw ConfigurationException("'$runConfigurationFieldName' is required if '$dependsOnContainersFieldName' is provided.", location.line, location.column)
            }

            return Task(
                runConfiguration = runConfiguration,
                description = description,
                group = group,
                dependsOnContainers = dependsOnContainers,
                prerequisiteTasks = prerequisiteTasks
            )
        }

        override fun serialize(encoder: Encoder, value: Task) {
            val output = encoder.beginStructure(descriptor)

            output.encodeSerializableElement(descriptor, runConfigurationFieldIndex, TaskRunConfiguration.serializer().nullable, value.runConfiguration)
            output.encodeStringElement(descriptor, descriptionFieldIndex, value.description)
            output.encodeStringElement(descriptor, groupFieldIndex, value.group)
            output.encodeSerializableElement(descriptor, dependsOnContainersFieldIndex, DependencySetSerializer, value.dependsOnContainers)
            output.encodeSerializableElement(descriptor, prerequisiteTasksFieldIndex, PrerequisiteListSerializer, value.prerequisiteTasks)

            output.endStructure(descriptor)
        }
    }
}
