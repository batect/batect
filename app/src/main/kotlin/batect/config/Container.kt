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
import batect.config.io.deserializers.DependencySetSerializer
import batect.config.io.deserializers.EnvironmentSerializer
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
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.internal.nullable
import kotlinx.serialization.list
import kotlinx.serialization.set
import java.nio.file.Path

@Serializable
data class Container(
    val name: String,
    val imageSource: ImageSource,
    val command: Command? = null,
    val entrypoint: Command? = null,
    val environment: Map<String, EnvironmentVariableExpression> = emptyMap(),
    val workingDirectory: String? = null,
    val volumeMounts: Set<VolumeMount> = emptySet(),
    val deviceMounts: Set<DeviceMount> = emptySet(),
    val portMappings: Set<PortMapping> = emptySet(),
    val dependencies: Set<String> = emptySet(),
    val healthCheckConfig: HealthCheckConfig = HealthCheckConfig(),
    val runAsCurrentUserConfig: RunAsCurrentUserConfig = RunAsCurrentUserConfig.RunAsDefaultContainerUser,
    val privileged: Boolean = false,
    val enableInitProcess: Boolean = false,
    val capabilitiesToAdd: Set<Capability> = emptySet(),
    val capabilitiesToDrop: Set<Capability> = emptySet(),
    val additionalHostnames: Set<String> = emptySet(),
    val setupCommands: List<SetupCommand> = emptyList()
) {
    @Serializer(forClass = Container::class)
    companion object : KSerializer<Container> {
        private const val buildDirectoryFieldName = "build_directory"
        private const val buildArgsFieldName = "build_args"
        private const val dockerfileFieldName = "dockerfile"
        private const val imageNameFieldName = "image"
        private const val commandFieldName = "command"
        private const val entrypointFieldName = "entrypoint"
        private const val environmentFieldName = "environment"
        private const val workingDirectoryFieldName = "working_directory"
        private const val volumeMountsFieldName = "volumes"
        private const val deviceMountsFieldName = "devices"
        private const val portMappingsFieldName = "ports"
        private const val dependenciesFieldName = "dependencies"
        private const val healthCheckConfigFieldName = "health_check"
        private const val runAsCurrentUserConfigFieldName = "run_as_current_user"
        private const val privilegedFieldName = "privileged"
        private const val enableInitProcessFieldName = "enable_init_process"
        private const val capabilitiesToAddFieldName = "capabilities_to_add"
        private const val capabilitiesToDropFieldName = "capabilities_to_drop"
        private const val additionalHostnamesFieldName = "additional_hostnames"
        private const val setupCommandsFieldName = "setup_commands"

        override val descriptor: SerialDescriptor = object : SerialClassDescImpl("ContainerFromFile") {
            init {
                addElement(buildDirectoryFieldName, isOptional = true)
                addElement(buildArgsFieldName, isOptional = true)
                addElement(dockerfileFieldName, isOptional = true)
                addElement(imageNameFieldName, isOptional = true)
                addElement(commandFieldName, isOptional = true)
                addElement(entrypointFieldName, isOptional = true)
                addElement(environmentFieldName, isOptional = true)
                addElement(workingDirectoryFieldName, isOptional = true)
                addElement(volumeMountsFieldName, isOptional = true)
                addElement(deviceMountsFieldName, isOptional = true)
                addElement(portMappingsFieldName, isOptional = true)
                addElement(dependenciesFieldName, isOptional = true)
                addElement(healthCheckConfigFieldName, isOptional = true)
                addElement(runAsCurrentUserConfigFieldName, isOptional = true)
                addElement(privilegedFieldName, isOptional = true)
                addElement(enableInitProcessFieldName, isOptional = true)
                addElement(capabilitiesToAddFieldName, isOptional = true)
                addElement(capabilitiesToDropFieldName, isOptional = true)
                addElement(additionalHostnamesFieldName, isOptional = true)
                addElement(setupCommandsFieldName, isOptional = true)
            }
        }

        private val buildDirectoryFieldIndex = descriptor.getElementIndex(buildDirectoryFieldName)
        private val buildArgsFieldIndex = descriptor.getElementIndex(buildArgsFieldName)
        private val dockerfileFieldIndex = descriptor.getElementIndex(dockerfileFieldName)
        private val imageNameFieldIndex = descriptor.getElementIndex(imageNameFieldName)
        private val commandFieldIndex = descriptor.getElementIndex(commandFieldName)
        private val entrypointFieldIndex = descriptor.getElementIndex(entrypointFieldName)
        private val environmentFieldIndex = descriptor.getElementIndex(environmentFieldName)
        private val workingDirectoryFieldIndex = descriptor.getElementIndex(workingDirectoryFieldName)
        private val volumeMountsFieldIndex = descriptor.getElementIndex(volumeMountsFieldName)
        private val deviceMountsFieldIndex = descriptor.getElementIndex(deviceMountsFieldName)
        private val portMappingsFieldIndex = descriptor.getElementIndex(portMappingsFieldName)
        private val dependenciesFieldIndex = descriptor.getElementIndex(dependenciesFieldName)
        private val healthCheckConfigFieldIndex = descriptor.getElementIndex(healthCheckConfigFieldName)
        private val runAsCurrentUserConfigFieldIndex = descriptor.getElementIndex(runAsCurrentUserConfigFieldName)
        private val privilegedFieldIndex = descriptor.getElementIndex(privilegedFieldName)
        private val enableInitProcessFieldIndex = descriptor.getElementIndex(enableInitProcessFieldName)
        private val capabilitiesToAddFieldIndex = descriptor.getElementIndex(capabilitiesToAddFieldName)
        private val capabilitiesToDropFieldIndex = descriptor.getElementIndex(capabilitiesToDropFieldName)
        private val additionalHostnamesFieldIndex = descriptor.getElementIndex(additionalHostnamesFieldName)
        private val setupCommandsFieldIndex = descriptor.getElementIndex(setupCommandsFieldName)

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
            var entrypoint: Command? = null
            var environment = emptyMap<String, EnvironmentVariableExpression>()
            var workingDirectory: String? = null
            var volumeMounts = emptySet<VolumeMount>()
            var deviceMounts = emptySet<DeviceMount>()
            var portMappings = emptySet<PortMapping>()
            var dependencies = emptySet<String>()
            var healthCheckConfig = HealthCheckConfig()
            var runAsCurrentUserConfig: RunAsCurrentUserConfig = RunAsCurrentUserConfig.RunAsDefaultContainerUser
            var privileged = false
            var enableInitProcess = false
            var capabilitiesToAdd = emptySet<Capability>()
            var capabilitiesToDrop = emptySet<Capability>()
            var additionalHostnames = emptySet<String>()
            var setupCommands = emptyList<SetupCommand>()

            loop@ while (true) {
                when (val i = input.decodeElementIndex(descriptor)) {
                    CompositeDecoder.READ_DONE -> break@loop
                    buildDirectoryFieldIndex -> buildDirectory = input.decodeBuildDirectory()
                    buildArgsFieldIndex -> buildArgs = input.decode(EnvironmentSerializer)
                    dockerfileFieldIndex -> dockerfilePath = input.decodeStringElement(descriptor, i)
                    imageNameFieldIndex -> imageName = input.decodeStringElement(descriptor, i)
                    commandFieldIndex -> command = input.decode(Command.Companion)
                    entrypointFieldIndex -> entrypoint = input.decode(Command.Companion)
                    environmentFieldIndex -> environment = input.decode(EnvironmentSerializer)
                    workingDirectoryFieldIndex -> workingDirectory = input.decodeStringElement(descriptor, i)
                    volumeMountsFieldIndex -> volumeMounts = input.decode(VolumeMount.serializer().set)
                    deviceMountsFieldIndex -> deviceMounts = input.decode(DeviceMount.serializer().set)
                    portMappingsFieldIndex -> portMappings = input.decode(PortMapping.serializer().set)
                    dependenciesFieldIndex -> dependencies = input.decode(DependencySetSerializer)
                    healthCheckConfigFieldIndex -> healthCheckConfig = input.decode(HealthCheckConfig.serializer())
                    runAsCurrentUserConfigFieldIndex -> runAsCurrentUserConfig = input.decode(RunAsCurrentUserConfig.serializer())
                    privilegedFieldIndex -> privileged = input.decodeBooleanElement(descriptor, i)
                    enableInitProcessFieldIndex -> enableInitProcess = input.decodeBooleanElement(descriptor, i)
                    capabilitiesToAddFieldIndex -> capabilitiesToAdd = input.decode(Capability.serializer().set)
                    capabilitiesToDropFieldIndex -> capabilitiesToDrop = input.decode(Capability.serializer().set)
                    additionalHostnamesFieldIndex -> additionalHostnames = input.decode(StringSerializer.set)
                    setupCommandsFieldIndex -> setupCommands = input.decode(SetupCommand.serializer().list)

                    else -> throw SerializationException("Unknown index $i")
                }
            }

            return Container(
                "UNNAMED-FROM-CONFIG-FILE",
                resolveImageSource(buildDirectory, buildArgs, dockerfilePath, imageName, input.node.location),
                command,
                entrypoint,
                environment,
                workingDirectory,
                volumeMounts,
                deviceMounts,
                portMappings,
                dependencies,
                healthCheckConfig,
                runAsCurrentUserConfig,
                privileged,
                enableInitProcess,
                capabilitiesToAdd,
                capabilitiesToDrop,
                additionalHostnames,
                setupCommands
            )
        }

        private fun YamlInput.decodeBuildDirectory(): Path {
            val loader = this.context.getContextual(PathResolutionResult::class)!!
            val resolutionResult = this.decode(loader)
            val location = this.getCurrentLocation()

            return resolveBuildDirectory(resolutionResult, location)
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

        override fun serialize(encoder: Encoder, obj: Container) {
            val output = encoder.beginStructure(descriptor)

            when (obj.imageSource) {
                is PullImage -> output.encodeStringElement(descriptor, imageNameFieldIndex, obj.imageSource.imageName)
                is BuildImage -> {
                    output.encodeStringElement(descriptor, buildDirectoryFieldIndex, obj.imageSource.buildDirectory.toString())
                    output.encodeSerializableElement(descriptor, buildArgsFieldIndex, EnvironmentSerializer, obj.imageSource.buildArgs)
                    output.encodeSerializableElement(descriptor, dockerfileFieldIndex, StringSerializer.nullable, obj.imageSource.dockerfilePath)
                }
            }

            output.encodeSerializableElement(descriptor, commandFieldIndex, Command.serializer().nullable, obj.command)
            output.encodeSerializableElement(descriptor, entrypointFieldIndex, Command.serializer().nullable, obj.entrypoint)
            output.encodeSerializableElement(descriptor, environmentFieldIndex, EnvironmentSerializer, obj.environment)
            output.encodeSerializableElement(descriptor, workingDirectoryFieldIndex, StringSerializer.nullable, obj.workingDirectory)
            output.encodeSerializableElement(descriptor, volumeMountsFieldIndex, VolumeMount.serializer().set, obj.volumeMounts)
            output.encodeSerializableElement(descriptor, deviceMountsFieldIndex, DeviceMount.serializer().set, obj.deviceMounts)
            output.encodeSerializableElement(descriptor, portMappingsFieldIndex, PortMapping.serializer().set, obj.portMappings)
            output.encodeSerializableElement(descriptor, dependenciesFieldIndex, DependencySetSerializer, obj.dependencies)
            output.encodeSerializableElement(descriptor, healthCheckConfigFieldIndex, HealthCheckConfig.serializer(), obj.healthCheckConfig)
            output.encodeSerializableElement(descriptor, runAsCurrentUserConfigFieldIndex, RunAsCurrentUserConfig.serializer(), obj.runAsCurrentUserConfig)
            output.encodeBooleanElement(descriptor, privilegedFieldIndex, obj.privileged)
            output.encodeBooleanElement(descriptor, enableInitProcessFieldIndex, obj.enableInitProcess)
            output.encodeSerializableElement(descriptor, capabilitiesToAddFieldIndex, Capability.serializer().set, obj.capabilitiesToAdd)
            output.encodeSerializableElement(descriptor, capabilitiesToDropFieldIndex, Capability.serializer().set, obj.capabilitiesToDrop)
            output.encodeSerializableElement(descriptor, additionalHostnamesFieldIndex, StringSerializer.set, obj.additionalHostnames)
            output.encodeSerializableElement(descriptor, setupCommandsFieldIndex, SetupCommand.serializer().list, obj.setupCommands)

            output.endStructure(descriptor)
        }
    }
}
