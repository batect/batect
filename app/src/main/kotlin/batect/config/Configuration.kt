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

import batect.config.io.ConfigurationException
import batect.config.io.deserializers.PathDeserializer
import batect.docker.DockerImageNameValidator
import batect.os.PathResolutionResult
import batect.os.PathResolver
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.CompositeDecoder
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.decode
import kotlinx.serialization.internal.SerialClassDescImpl

@Serializable
data class Configuration(
    val projectName: String,
    val tasks: TaskMap = TaskMap(),
    val containers: ContainerMap = ContainerMap()
) {
    @Serializer(forClass = Configuration::class)
    companion object : KSerializer<Configuration> {
        private const val projectNameFieldName = "project_name"
        private const val tasksFieldName = "tasks"
        private const val containersFieldName = "containers"

        override val descriptor: SerialDescriptor = object : SerialClassDescImpl("Configuration") {
            init {
                addElement(projectNameFieldName, isOptional = true)
                addElement(tasksFieldName, isOptional = true)
                addElement(containersFieldName, isOptional = true)
            }
        }

        private val projectNameFieldIndex = descriptor.getElementIndex(projectNameFieldName)
        private val tasksFieldIndex = descriptor.getElementIndex(tasksFieldName)
        private val containersFieldIndex = descriptor.getElementIndex(containersFieldName)

        override fun deserialize(decoder: Decoder): Configuration {
            val input = decoder.beginStructure(descriptor) as YamlInput

            return deserializeFromObject(input).also { input.endStructure(descriptor) }
        }

        private fun deserializeFromObject(input: YamlInput): Configuration {
            var projectName: String? = null
            var tasks = TaskMap()
            var containers = ContainerMap()

            loop@ while (true) {
                when (val i = input.decodeElementIndex(descriptor)) {
                    CompositeDecoder.READ_DONE -> break@loop
                    projectNameFieldIndex -> projectName = input.decodeProjectName(i)
                    tasksFieldIndex -> tasks = input.decode(TaskMap.Companion)
                    containersFieldIndex -> containers = input.decode(ContainerMap.Companion)
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            if (projectName == null) {
                // HACK: this is a terrible hack but there's no other way to get something contextual while deserializing
                val pathResolver = (input.context.getContextual(PathResolutionResult::class)!! as PathDeserializer).pathResolver

                projectName = inferProjectName(pathResolver)
            }

            return Configuration(projectName, tasks, containers)
        }

        private fun YamlInput.decodeProjectName(index: Int): String {
            val projectName = this.decodeStringElement(descriptor, index)

            if (!DockerImageNameValidator.isValidImageName(projectName)) {
                val location = this.getCurrentLocation()

                throw ConfigurationException(
                    "Invalid project name '$projectName'. The project name must be a valid Docker reference: it ${DockerImageNameValidator.validNameDescription}.",
                    location.line,
                    location.column
                )
            }

            return projectName
        }

        private fun inferProjectName(pathResolver: PathResolver): String {
            if (pathResolver.relativeTo.root == pathResolver.relativeTo) {
                throw ConfigurationException("No project name has been given explicitly, but the configuration file is in the root directory and so a project name cannot be inferred.")
            }

            val inferredProjectName = pathResolver.relativeTo.fileName.toString()

            if (!DockerImageNameValidator.isValidImageName(inferredProjectName)) {
                throw ConfigurationException("The inferred project name '$inferredProjectName' is invalid. The project name must be a valid Docker reference: it ${DockerImageNameValidator.validNameDescription}. Provide a valid project name explicitly with '$projectNameFieldName'.")
            }

            return inferredProjectName
        }

        override fun serialize(encoder: Encoder, obj: Configuration): Unit = throw UnsupportedOperationException()
    }
}
