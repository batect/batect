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
import batect.config.io.deserializers.EnvironmentSerializer
import batect.config.io.deserializers.PathDeserializer
import batect.docker.Capability
import batect.os.Command
import batect.os.PathResolutionResult
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
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.list
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.set

@Serializable
data class Container(
    val name: String,
    val imageSource: ImageSource,
    val command: Command? = null,
    val entrypoint: Command? = null,
    val environment: Map<String, Expression> = emptyMap(),
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
    val additionalHosts: Map<String, String> = emptyMap(),
    val setupCommands: List<SetupCommand> = emptyList(),
    val logDriver: String = Container.defaultLogDriver,
    val logOptions: Map<String, String> = emptyMap()
) {
    @Serializer(forClass = Container::class)
    companion object : KSerializer<Container> {
        const val defaultLogDriver = "json-file"

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
        private const val additionalHostsFieldName = "additional_hosts"
        private const val setupCommandsFieldName = "setup_commands"
        private const val logDriverFieldName = "log_driver"
        private const val logOptionsFieldName = "log_options"

        override val descriptor: SerialDescriptor = SerialDescriptor("Container") {
            element(buildDirectoryFieldName, String.serializer().descriptor, isOptional = true)
            element(buildArgsFieldName, EnvironmentSerializer.descriptor, isOptional = true)
            element(dockerfileFieldName, String.serializer().descriptor, isOptional = true)
            element(imageNameFieldName, String.serializer().descriptor, isOptional = true)
            element(commandFieldName, Command.serializer().descriptor, isOptional = true)
            element(entrypointFieldName, String.serializer().descriptor, isOptional = true)
            element(environmentFieldName, EnvironmentSerializer.descriptor, isOptional = true)
            element(workingDirectoryFieldName, String.serializer().descriptor, isOptional = true)
            element(volumeMountsFieldName, VolumeMount.serializer().set.descriptor, isOptional = true)
            element(deviceMountsFieldName, DeviceMount.serializer().set.descriptor, isOptional = true)
            element(portMappingsFieldName, PortMapping.serializer().set.descriptor, isOptional = true)
            element(dependenciesFieldName, String.serializer().set.descriptor, isOptional = true)
            element(healthCheckConfigFieldName, HealthCheckConfig.serializer().descriptor, isOptional = true)
            element(runAsCurrentUserConfigFieldName, RunAsCurrentUserConfig.serializer().descriptor, isOptional = true)
            element(privilegedFieldName, Boolean.serializer().descriptor, isOptional = true)
            element(enableInitProcessFieldName, Boolean.serializer().descriptor, isOptional = true)
            element(capabilitiesToAddFieldName, Capability.serializer().set.descriptor, isOptional = true)
            element(capabilitiesToDropFieldName, Capability.serializer().set.descriptor, isOptional = true)
            element(additionalHostnamesFieldName, String.serializer().set.descriptor, isOptional = true)
            element(additionalHostsFieldName, MapSerializer(String.serializer(), String.serializer()).descriptor, isOptional = true)
            element(setupCommandsFieldName, SetupCommand.serializer().list.descriptor, isOptional = true)
            element(logDriverFieldName, String.serializer().descriptor, isOptional = true)
            element(logOptionsFieldName, MapSerializer(String.serializer(), String.serializer()).descriptor, isOptional = true)
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
        private val additionalHostsFieldIndex = descriptor.getElementIndex(additionalHostsFieldName)
        private val setupCommandsFieldIndex = descriptor.getElementIndex(setupCommandsFieldName)
        private val logDriverFieldIndex = descriptor.getElementIndex(logDriverFieldName)
        private val logOptionsFieldIndex = descriptor.getElementIndex(logOptionsFieldName)

        override fun deserialize(decoder: Decoder): Container {
            val input = decoder.beginStructure(descriptor) as YamlInput

            return deserializeFromObject(input).also { input.endStructure(descriptor) }
        }

        private fun deserializeFromObject(input: YamlInput): Container {
            var buildDirectory: Expression? = null
            var buildArgs: Map<String, Expression>? = null
            var dockerfilePath: String? = null
            var imageName: String? = null
            var command: Command? = null
            var entrypoint: Command? = null
            var environment = emptyMap<String, Expression>()
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
            var additionalHosts = emptyMap<String, String>()
            var setupCommands = emptyList<SetupCommand>()
            var logDriver = Container.defaultLogDriver
            var logOptions = emptyMap<String, String>()

            loop@ while (true) {
                when (val i = input.decodeElementIndex(descriptor)) {
                    CompositeDecoder.READ_DONE -> break@loop
                    buildDirectoryFieldIndex -> buildDirectory = input.decodeSerializableElement(descriptor, i, Expression.serializer())
                    buildArgsFieldIndex -> buildArgs = input.decodeSerializableElement(descriptor, i, EnvironmentSerializer)
                    dockerfileFieldIndex -> dockerfilePath = input.decodeStringElement(descriptor, i)
                    imageNameFieldIndex -> imageName = input.decodeStringElement(descriptor, i)
                    commandFieldIndex -> command = input.decodeSerializableElement(descriptor, i, Command.Companion)
                    entrypointFieldIndex -> entrypoint = input.decodeSerializableElement(descriptor, i, Command.Companion)
                    environmentFieldIndex -> environment = input.decodeSerializableElement(descriptor, i, EnvironmentSerializer)
                    workingDirectoryFieldIndex -> workingDirectory = input.decodeStringElement(descriptor, i)
                    volumeMountsFieldIndex -> volumeMounts = input.decodeSerializableElement(descriptor, i, VolumeMount.serializer().set)
                    deviceMountsFieldIndex -> deviceMounts = input.decodeSerializableElement(descriptor, i, DeviceMount.serializer().set)
                    portMappingsFieldIndex -> portMappings = input.decodeSerializableElement(descriptor, i, PortMapping.serializer().set)
                    dependenciesFieldIndex -> dependencies = input.decodeSerializableElement(descriptor, i, DependencySetSerializer)
                    healthCheckConfigFieldIndex -> healthCheckConfig = input.decodeSerializableElement(descriptor, i, HealthCheckConfig.serializer())
                    runAsCurrentUserConfigFieldIndex -> runAsCurrentUserConfig = input.decodeSerializableElement(descriptor, i, RunAsCurrentUserConfig.serializer())
                    privilegedFieldIndex -> privileged = input.decodeBooleanElement(descriptor, i)
                    enableInitProcessFieldIndex -> enableInitProcess = input.decodeBooleanElement(descriptor, i)
                    capabilitiesToAddFieldIndex -> capabilitiesToAdd = input.decodeSerializableElement(descriptor, i, Capability.serializer().set)
                    capabilitiesToDropFieldIndex -> capabilitiesToDrop = input.decodeSerializableElement(descriptor, i, Capability.serializer().set)
                    additionalHostnamesFieldIndex -> additionalHostnames = input.decodeSerializableElement(descriptor, i, String.serializer().set)
                    additionalHostsFieldIndex -> additionalHosts = input.decodeSerializableElement(descriptor, i, MapSerializer(String.serializer(), String.serializer()))
                    setupCommandsFieldIndex -> setupCommands = input.decodeSerializableElement(descriptor, i, SetupCommand.serializer().list)
                    logDriverFieldIndex -> logDriver = input.decodeStringElement(descriptor, i)
                    logOptionsFieldIndex -> logOptions = input.decodeSerializableElement(descriptor, i, MapSerializer(String.serializer(), String.serializer()))

                    else -> throw SerializationException("Unknown index $i")
                }
            }

            return Container(
                "UNNAMED-FROM-CONFIG-FILE",
                resolveImageSource(input, buildDirectory, buildArgs, dockerfilePath, imageName, input.node.location),
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
                additionalHosts,
                setupCommands,
                logDriver,
                logOptions
            )
        }

        private fun resolveImageSource(
            input: YamlInput,
            buildDirectory: Expression?,
            buildArgs: Map<String, Expression>?,
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
                val loader = input.context.getContextual(PathResolutionResult::class)!! as PathDeserializer
                val relativeTo = loader.pathResolver.relativeTo

                return BuildImage(buildDirectory, relativeTo, buildArgs ?: emptyMap(), dockerfilePath ?: "Dockerfile")
            } else {
                return PullImage(imageName!!)
            }
        }

        override fun serialize(encoder: Encoder, value: Container) {
            val output = encoder.beginStructure(descriptor)

            when (value.imageSource) {
                is PullImage -> output.encodeStringElement(descriptor, imageNameFieldIndex, value.imageSource.imageName)
                is BuildImage -> {
                    output.encodeSerializableElement(descriptor, buildDirectoryFieldIndex, Expression.serializer(), value.imageSource.buildDirectory)
                    output.encodeSerializableElement(descriptor, buildArgsFieldIndex, EnvironmentSerializer, value.imageSource.buildArgs)
                    output.encodeSerializableElement(descriptor, dockerfileFieldIndex, String.serializer().nullable, value.imageSource.dockerfilePath)
                }
            }

            output.encodeSerializableElement(descriptor, commandFieldIndex, Command.serializer().nullable, value.command)
            output.encodeSerializableElement(descriptor, entrypointFieldIndex, Command.serializer().nullable, value.entrypoint)
            output.encodeSerializableElement(descriptor, environmentFieldIndex, EnvironmentSerializer, value.environment)
            output.encodeSerializableElement(descriptor, workingDirectoryFieldIndex, String.serializer().nullable, value.workingDirectory)
            output.encodeSerializableElement(descriptor, volumeMountsFieldIndex, VolumeMount.serializer().set, value.volumeMounts)
            output.encodeSerializableElement(descriptor, deviceMountsFieldIndex, DeviceMount.serializer().set, value.deviceMounts)
            output.encodeSerializableElement(descriptor, portMappingsFieldIndex, PortMapping.serializer().set, value.portMappings)
            output.encodeSerializableElement(descriptor, dependenciesFieldIndex, DependencySetSerializer, value.dependencies)
            output.encodeSerializableElement(descriptor, healthCheckConfigFieldIndex, HealthCheckConfig.serializer(), value.healthCheckConfig)
            output.encodeSerializableElement(descriptor, runAsCurrentUserConfigFieldIndex, RunAsCurrentUserConfig.serializer(), value.runAsCurrentUserConfig)
            output.encodeBooleanElement(descriptor, privilegedFieldIndex, value.privileged)
            output.encodeBooleanElement(descriptor, enableInitProcessFieldIndex, value.enableInitProcess)
            output.encodeSerializableElement(descriptor, capabilitiesToAddFieldIndex, Capability.serializer().set, value.capabilitiesToAdd)
            output.encodeSerializableElement(descriptor, capabilitiesToDropFieldIndex, Capability.serializer().set, value.capabilitiesToDrop)
            output.encodeSerializableElement(descriptor, additionalHostnamesFieldIndex, String.serializer().set, value.additionalHostnames)
            output.encodeSerializableElement(descriptor, additionalHostsFieldIndex, MapSerializer(String.serializer(), String.serializer()), value.additionalHosts)
            output.encodeSerializableElement(descriptor, setupCommandsFieldIndex, SetupCommand.serializer().list, value.setupCommands)
            output.encodeStringElement(descriptor, logDriverFieldIndex, value.logDriver)
            output.encodeSerializableElement(descriptor, logOptionsFieldIndex, MapSerializer(String.serializer(), String.serializer()), value.logOptions)

            output.endStructure(descriptor)
        }
    }
}
