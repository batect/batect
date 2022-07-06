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

package batect.config

import batect.config.io.ConfigurationException
import batect.config.io.deserializers.DependencySetSerializer
import batect.config.io.deserializers.EnvironmentSerializer
import batect.config.io.deserializers.PrerequisiteListSerializer
import batect.config.io.deserializers.TaskContainerCustomisationSerializer
import com.charleskorn.kaml.YamlInput
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlPath
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
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
    val prerequisiteTasks: List<String> = emptyList(),
    val customisations: Map<String, TaskContainerCustomisation> = emptyMap()
) {
    @OptIn(ExperimentalSerializationApi::class)
    companion object : KSerializer<Task> {
        private const val runConfigurationFieldName = "run"
        private const val descriptionFieldName = "description"
        private const val groupFieldName = "group"
        private const val dependsOnContainersFieldName = "dependencies"
        private const val prerequisiteTasksFieldName = "prerequisites"
        private const val customisationsFieldName = "customise"

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Task") {
            element(runConfigurationFieldName, TaskRunConfiguration.serializer().descriptor, isOptional = true)
            element(descriptionFieldName, String.serializer().descriptor, isOptional = true)
            element(groupFieldName, String.serializer().descriptor, isOptional = true)
            element(dependsOnContainersFieldName, DependencySetSerializer.descriptor, isOptional = true)
            element(prerequisiteTasksFieldName, PrerequisiteListSerializer.descriptor, isOptional = true)
            element(customisationsFieldName, TaskContainerCustomisationSerializer.descriptor, isOptional = true)
        }

        private val runConfigurationFieldIndex = descriptor.getElementIndex(runConfigurationFieldName)
        private val descriptionFieldIndex = descriptor.getElementIndex(descriptionFieldName)
        private val groupFieldIndex = descriptor.getElementIndex(groupFieldName)
        private val dependsOnContainersFieldIndex = descriptor.getElementIndex(dependsOnContainersFieldName)
        private val prerequisiteTasksFieldIndex = descriptor.getElementIndex(prerequisiteTasksFieldName)
        private val customisationsFieldIndex = descriptor.getElementIndex(customisationsFieldName)

        override fun deserialize(decoder: Decoder): Task {
            val input = decoder.beginStructure(descriptor) as YamlInput

            return deserializeFromObject(input).also { input.endStructure(descriptor) }
        }

        private fun deserializeFromObject(input: YamlInput): Task {
            val path = input.getCurrentPath()
            var runConfiguration: TaskRunConfiguration? = null
            var description = ""
            var group = ""
            var dependsOnContainers: Set<String> = emptySet()
            var prerequisiteTasks: List<String> = emptyList()
            var customisations: Map<String, TaskContainerCustomisation> = emptyMap()
            var customisationsPath: YamlPath? = null

            loop@ while (true) {
                when (val i = input.decodeElementIndex(descriptor)) {
                    runConfigurationFieldIndex -> runConfiguration = input.decodeSerializableValue(TaskRunConfiguration.serializer())
                    descriptionFieldIndex -> description = input.decodeStringElement(descriptor, i)
                    groupFieldIndex -> group = input.decodeStringElement(descriptor, i)
                    dependsOnContainersFieldIndex -> dependsOnContainers = input.decodeSerializableValue(DependencySetSerializer)
                    prerequisiteTasksFieldIndex -> prerequisiteTasks = input.decodeSerializableValue(PrerequisiteListSerializer)
                    customisationsFieldIndex -> {
                        customisationsPath = (input.node as YamlMap).getKey(customisationsFieldName)!!.path
                        customisations = input.decodeSerializableValue(TaskContainerCustomisationSerializer)
                    }

                    CompositeDecoder.DECODE_DONE -> break@loop
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            if (runConfiguration == null && prerequisiteTasks.isEmpty()) {
                throw ConfigurationException("At least one of '$runConfigurationFieldName' or '$prerequisiteTasksFieldName' is required.", path)
            }

            if (dependsOnContainers.isNotEmpty() && runConfiguration == null) {
                throw ConfigurationException("'$runConfigurationFieldName' is required if '$dependsOnContainersFieldName' is provided.", path)
            }

            val mainContainerName = runConfiguration?.container

            if (mainContainerName != null && customisations.containsKey(mainContainerName)) {
                throw ConfigurationException("Cannot apply customisations to main task container '$mainContainerName'. Set the corresponding properties on 'run' instead.", customisationsPath!!)
            }

            return Task(
                runConfiguration = runConfiguration,
                description = description,
                group = group,
                dependsOnContainers = dependsOnContainers,
                prerequisiteTasks = prerequisiteTasks,
                customisations = customisations
            )
        }

        override fun serialize(encoder: Encoder, value: Task) {
            val output = encoder.beginStructure(descriptor)

            output.encodeSerializableElement(descriptor, runConfigurationFieldIndex, TaskRunConfiguration.serializer().nullable, value.runConfiguration)
            output.encodeStringElement(descriptor, descriptionFieldIndex, value.description)
            output.encodeStringElement(descriptor, groupFieldIndex, value.group)
            output.encodeSerializableElement(descriptor, dependsOnContainersFieldIndex, DependencySetSerializer, value.dependsOnContainers)
            output.encodeSerializableElement(descriptor, prerequisiteTasksFieldIndex, PrerequisiteListSerializer, value.prerequisiteTasks)
            output.encodeSerializableElement(descriptor, customisationsFieldIndex, TaskContainerCustomisationSerializer, value.customisations)

            output.endStructure(descriptor)
        }
    }
}

@Serializable
data class TaskContainerCustomisation(
    @SerialName("environment") @Serializable(with = EnvironmentSerializer::class)
    val additionalEnvironmentVariables: Map<String, Expression> = emptyMap(),
    @SerialName("ports") @Serializable(with = PortMappingConfigSetSerializer::class)
    val additionalPortMappings: Set<PortMapping> = emptySet(),
    @SerialName("working_directory") val workingDirectory: String? = null
)
