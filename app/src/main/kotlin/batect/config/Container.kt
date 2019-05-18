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
import batect.config.io.deserializers.DependencySetDeserializer
import batect.config.io.deserializers.EnvironmentDeserializer
import batect.docker.Capability
import batect.os.Command
import batect.os.PathResolutionResult
import batect.os.PathType
import com.charleskorn.kaml.Location
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
import kotlinx.serialization.set
import java.nio.file.Path

@Serializable
data class Container(
    val name: String,
    val imageSource: ImageSource,
    val command: Command? = null,
    val environment: Map<String, EnvironmentVariableExpression> = emptyMap(),
    val workingDirectory: String? = null,
    val volumeMounts: Set<VolumeMount> = emptySet(),
    val portMappings: Set<PortMapping> = emptySet(),
    val dependencies: Set<String> = emptySet(),
    val healthCheckConfig: HealthCheckConfig = HealthCheckConfig(),
    val runAsCurrentUserConfig: RunAsCurrentUserConfig = RunAsCurrentUserConfig.RunAsDefaultContainerUser,
    val privileged: Boolean = false,
    val enableInitProcess: Boolean = false,
    val capabilitiesToAdd: Set<Capability> = emptySet(),
    val capabilitiesToDrop: Set<Capability> = emptySet()
) {
    @Serializer(forClass = Container::class)
    companion object : KSerializer<Container> {
        private val buildDirectoryFieldName = "build_directory"
        private val buildArgsFieldName = "build_args"
        private val dockerfileFieldName = "dockerfile"
        private val imageNameFieldName = "image"
        private val commandFieldName = "command"
        private val environmentFieldName = "environment"
        private val workingDirectoryFieldName = "working_directory"
        private val volumeMountsFieldName = "volumes"
        private val portMappingsFieldName = "ports"
        private val dependenciesFieldName = "dependencies"
        private val healthCheckConfigFieldName = "health_check"
        private val runAsCurrentUserConfigFieldName = "run_as_current_user"
        private val privilegedFieldName = "privileged"
        private val enableInitProcessFieldName = "enable_init_process"
        private val capabilitiesToAddFieldName = "capabilities_to_add"
        private val capabilitiesToDropFieldName = "capabilities_to_drop"

        override val descriptor: SerialDescriptor = object : SerialClassDescImpl("ContainerFromFile") {
            init {
                addElement(buildDirectoryFieldName, isOptional = true)
                addElement(buildArgsFieldName, isOptional = true)
                addElement(dockerfileFieldName, isOptional = true)
                addElement(imageNameFieldName, isOptional = true)
                addElement(commandFieldName, isOptional = true)
                addElement(environmentFieldName, isOptional = true)
                addElement(workingDirectoryFieldName, isOptional = true)
                addElement(volumeMountsFieldName, isOptional = true)
                addElement(portMappingsFieldName, isOptional = true)
                addElement(dependenciesFieldName, isOptional = true)
                addElement(healthCheckConfigFieldName, isOptional = true)
                addElement(runAsCurrentUserConfigFieldName, isOptional = true)
                addElement(privilegedFieldName, isOptional = true)
                addElement(enableInitProcessFieldName, isOptional = true)
                addElement(capabilitiesToAddFieldName, isOptional = true)
                addElement(capabilitiesToDropFieldName, isOptional = true)
            }
        }

        private val buildDirectoryFieldIndex = descriptor.getElementIndex(buildDirectoryFieldName)
        private val buildArgsFieldIndex = descriptor.getElementIndex(buildArgsFieldName)
        private val dockerfileFieldIndex = descriptor.getElementIndex(dockerfileFieldName)
        private val imageNameFieldIndex = descriptor.getElementIndex(imageNameFieldName)
        private val commandFieldIndex = descriptor.getElementIndex(commandFieldName)
        private val environmentFieldIndex = descriptor.getElementIndex(environmentFieldName)
        private val workingDirectoryFieldIndex = descriptor.getElementIndex(workingDirectoryFieldName)
        private val volumeMountsFieldIndex = descriptor.getElementIndex(volumeMountsFieldName)
        private val portMappingsFieldIndex = descriptor.getElementIndex(portMappingsFieldName)
        private val dependenciesFieldIndex = descriptor.getElementIndex(dependenciesFieldName)
        private val healthCheckConfigFieldIndex = descriptor.getElementIndex(healthCheckConfigFieldName)
        private val runAsCurrentUserConfigFieldIndex = descriptor.getElementIndex(runAsCurrentUserConfigFieldName)
        private val privilegedFieldIndex = descriptor.getElementIndex(privilegedFieldName)
        private val enableInitProcessConfigFieldIndex = descriptor.getElementIndex(enableInitProcessFieldName)
        private val capabilitiesToAddFieldIndex = descriptor.getElementIndex(capabilitiesToAddFieldName)
        private val capabilitiesToDropFieldIndex = descriptor.getElementIndex(capabilitiesToDropFieldName)

        override fun deserialize(decoder: Decoder): Container {
            val input = decoder.beginStructure(descriptor) as YamlInput

            return deserializeFromObject(input).also { input.endStructure(descriptor) }
        }

        private fun deserializeFromObject(input: YamlInput): Container {
            var buildDirectory: Path? = null
            var buildArgs: Map<String, EnvironmentVariableExpression>? = null
            var dockerfilePath: String? = null
            var imageName: String? = null
            var command: Command? = null
            var environment = emptyMap<String, EnvironmentVariableExpression>()
            var workingDirectory: String? = null
            var volumeMounts = emptySet<VolumeMount>()
            var portMappings = emptySet<PortMapping>()
            var dependencies = emptySet<String>()
            var healthCheckConfig = HealthCheckConfig()
            var runAsCurrentUserConfig: RunAsCurrentUserConfig = RunAsCurrentUserConfig.RunAsDefaultContainerUser
            var privileged = false
            var enableInitProcess = false
            var capabilitiesToAdd = emptySet<Capability>()
            var capabilitiesToDrop = emptySet<Capability>()

            loop@ while (true) {
                when (val i = input.decodeElementIndex(descriptor)) {
                    CompositeDecoder.READ_DONE -> break@loop
                    buildDirectoryFieldIndex -> {
                        val loader = input.context.getContextual(PathResolutionResult::class)!!
                        val resolutionResult = input.decode(loader)
                        val location = input.getCurrentLocation()

                        buildDirectory = resolveBuildDirectory(resolutionResult, location)
                    }
                    buildArgsFieldIndex -> buildArgs = input.decode(EnvironmentDeserializer)
                    dockerfileFieldIndex -> dockerfilePath = input.decodeStringElement(descriptor, i)
                    imageNameFieldIndex -> imageName = input.decodeStringElement(descriptor, i)
                    commandFieldIndex -> command = input.decode(Command.Companion)
                    environmentFieldIndex -> environment = input.decode(EnvironmentDeserializer)
                    workingDirectoryFieldIndex -> workingDirectory = input.decodeStringElement(descriptor, i)
                    volumeMountsFieldIndex -> volumeMounts = input.decode(VolumeMount.serializer().set)
                    portMappingsFieldIndex -> portMappings = input.decode(PortMapping.serializer().set)
                    dependenciesFieldIndex -> dependencies = input.decode(DependencySetDeserializer)
                    healthCheckConfigFieldIndex -> healthCheckConfig = input.decode(HealthCheckConfig.serializer())
                    runAsCurrentUserConfigFieldIndex -> runAsCurrentUserConfig = input.decode(RunAsCurrentUserConfig.serializer())
                    privilegedFieldIndex -> privileged = input.decodeBooleanElement(descriptor, i)
                    enableInitProcessConfigFieldIndex -> enableInitProcess = input.decodeBooleanElement(descriptor, i)
                    capabilitiesToAddFieldIndex -> capabilitiesToAdd = input.decode(Capability.serializer.set)
                    capabilitiesToDropFieldIndex -> capabilitiesToDrop = input.decode(Capability.serializer.set)

                    else -> throw SerializationException("Unknown index $i")
                }
            }

            return Container(
                "UNNAMED-FROM-CONFIG-FILE",
                resolveImageSource(buildDirectory, buildArgs, dockerfilePath, imageName, input.node.location),
                command,
                environment,
                workingDirectory,
                volumeMounts,
                portMappings,
                dependencies,
                healthCheckConfig,
                runAsCurrentUserConfig,
                privileged,
                enableInitProcess,
                capabilitiesToAdd,
                capabilitiesToDrop
            )
        }

        private fun resolveImageSource(
            buildDirectory: Path?,
            buildArgs: Map<String, EnvironmentVariableExpression>?,
            dockerfilePath: String?,
            imageName: String?,
            location: Location
        ): ImageSource {
            if (buildDirectory == null && imageName == null) {
                throw ConfigurationException("One of either build_directory or image must be specified for each container, but neither have been provided for this container.", location.line, location.column)
            }

            if (buildDirectory != null && imageName != null) {
                throw ConfigurationException("Only one of build_directory or image can be specified for a container, but both have been provided for this container.", location.line, location.column)
            }

            if (imageName != null && buildArgs != null) {
                throw ConfigurationException("build_args cannot be used with image, but both have been provided.", location.line, location.column)
            }

            if (imageName != null && dockerfilePath != null) {
                throw ConfigurationException("dockerfile cannot be used with image, but both have been provided.", location.line, location.column)
            }

            if (buildDirectory != null) {
                return BuildImage(buildDirectory, buildArgs ?: emptyMap(), dockerfilePath ?: "Dockerfile")
            } else {
                return PullImage(imageName!!)
            }
        }

        private fun resolveBuildDirectory(buildDirectory: PathResolutionResult, location: Location): Path {
            when (buildDirectory) {
                is PathResolutionResult.Resolved -> when (buildDirectory.pathType) {
                    PathType.Directory -> return buildDirectory.absolutePath
                    PathType.DoesNotExist -> throw ConfigurationException("Build directory '${buildDirectory.originalPath}' (resolved to '${buildDirectory.absolutePath}') does not exist.", location.line, location.line)
                    else -> throw ConfigurationException("Build directory '${buildDirectory.originalPath}' (resolved to '${buildDirectory.absolutePath}') is not a directory.", location.line, location.line)
                }
                is PathResolutionResult.InvalidPath -> throw ConfigurationException("Build directory '${buildDirectory.originalPath}' is not a valid path.", location.line, location.line)
            }
        }

        override fun serialize(encoder: Encoder, obj: Container): Unit = throw UnsupportedOperationException()
    }
}
