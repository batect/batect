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

package batect.docker

import batect.cli.CommandLineOptions
import batect.config.Container
import batect.config.HealthCheckConfig
import batect.config.PortMapping
import batect.dockerclient.ContainerCreationSpec
import batect.dockerclient.DeviceMount
import batect.dockerclient.ImageReference
import batect.dockerclient.NetworkReference
import batect.dockerclient.VolumeReference
import batect.primitives.mapToSet
import okio.Path.Companion.toPath

class DockerContainerCreationRequestFactory(
    private val environmentVariableProvider: DockerContainerEnvironmentVariableProvider,
    private val resourceNameGenerator: DockerResourceNameGenerator,
    private val commandLineOptions: CommandLineOptions
) {
    fun create(
        container: Container,
        image: ImageReference,
        network: NetworkReference,
        volumeMounts: Set<DockerVolumeMount>,
        userAndGroup: UserAndGroup?,
        terminalType: String?,
        useTTY: Boolean,
        attachStdin: Boolean
    ): ContainerCreationSpec {
        val builder = ContainerCreationSpec.Builder(image)
            .withName(resourceNameGenerator.generateNameFor(container))
            .withNetwork(network)
            .withHostname(container.name)
            .withNetworkAliases(container.additionalHostnames + container.name)
            .withEnvironmentVariables(environmentVariableProvider.environmentVariablesFor(container, terminalType))
            .withCapabilitiesAdded(container.capabilitiesToAdd.convert())
            .withCapabilitiesDropped(container.capabilitiesToDrop.convert())
            .withLogDriver(container.logDriver)
            .withLoggingOptions(container.logOptions)
            .withHealthcheckConfiguration(container.healthCheckConfig)

        if (!commandLineOptions.disablePortMappings) {
            builder.withPortMappings(container.portMappings)
        }

        if (container.command != null) {
            builder.withCommand(container.command.parsedCommand)
        }

        if (container.entrypoint != null) {
            builder.withEntrypoint(container.entrypoint.parsedCommand)
        }

        if (container.workingDirectory != null) {
            builder.withWorkingDirectory(container.workingDirectory)
        }

        volumeMounts.forEach {
            when (it.source) {
                is DockerVolumeMountSource.LocalPath -> builder.withHostMount(it.source.path.toPath(), it.containerPath, it.options)
                is DockerVolumeMountSource.Volume -> builder.withVolumeMount(VolumeReference(it.source.name), it.containerPath, it.options)
            }
        }

        container.additionalHosts.forEach { (name, address) -> builder.withExtraHost(name, address) }
        container.deviceMounts.forEach { builder.withDeviceMount(it.localPath.toPath(), it.containerPath, it.options ?: DeviceMount.defaultPermissions) }

        if (userAndGroup != null) {
            builder.withUserAndGroup(userAndGroup.userId, userAndGroup.groupId)
        }

        if (container.privileged) {
            builder.withPrivileged()
        }

        if (container.enableInitProcess) {
            builder.withInitProcess()
        }

        if (container.shmSize != null) {
            builder.withShmSize(container.shmSize.bytes)
        }

        if (useTTY) {
            builder.withTTY()
        }

        if (attachStdin) {
            builder.withStdinAttached()
        }

        return builder.build()
    }

    private fun ContainerCreationSpec.Builder.withHealthcheckConfiguration(healthCheckConfig: HealthCheckConfig): ContainerCreationSpec.Builder {
        if (healthCheckConfig.interval != null) {
            withHealthcheckInterval(healthCheckConfig.interval)
        }

        if (healthCheckConfig.retries != null) {
            withHealthcheckRetries(healthCheckConfig.retries)
        }

        if (healthCheckConfig.startPeriod != null) {
            withHealthcheckStartPeriod(healthCheckConfig.startPeriod)
        }

        if (healthCheckConfig.timeout != null) {
            withHealthcheckTimeout(healthCheckConfig.timeout)
        }

        if (healthCheckConfig.command != null) {
            withHealthcheckCommand(healthCheckConfig.command)
        }

        return this
    }

    private fun ContainerCreationSpec.Builder.withPortMappings(portMappings: Set<PortMapping>): ContainerCreationSpec.Builder {
        portMappings.forEach { mapping ->
            val localPorts = mapping.local.ports
            val containerPorts = mapping.container.ports

            localPorts.zip(containerPorts).forEach { (local, container) ->
                withExposedPort(local.toLong(), container.toLong(), mapping.protocol)
            }
        }

        return this
    }

    private fun Set<Capability>.convert(): Set<batect.dockerclient.Capability> = mapToSet { it.convert() }
    private fun Capability.convert(): batect.dockerclient.Capability {
        return batect.dockerclient.Capability.valueOf(this.name)
    }
}
