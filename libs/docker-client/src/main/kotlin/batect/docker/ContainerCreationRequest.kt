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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

@Serializable
data class ContainerCreationRequest(
    val name: String,
    val image: DockerImage,
    val network: DockerNetwork,
    val command: List<String>,
    val entrypoint: List<String>,
    val hostname: String,
    val networkAliases: Set<String>,
    val extraHosts: Map<String, String>,
    val environmentVariables: Map<String, String>,
    val workingDirectory: String?,
    val volumeMounts: Set<DockerVolumeMount>,
    val deviceMounts: Set<DockerDeviceMount>,
    val portMappings: Set<DockerPortMapping>,
    val healthCheckConfig: HealthCheckConfig,
    val userAndGroup: UserAndGroup?,
    val privileged: Boolean,
    val init: Boolean,
    val capabilitiesToAdd: Set<Capability>,
    val capabilitiesToDrop: Set<Capability>,
    val useTTY: Boolean,
    val attachStdin: Boolean,
    val logDriver: String,
    val logOptions: Map<String, String>,
    val shmSize: Long?
) {
    init {
        if (hostname.length > maximumHostNameLength) {
            throw ContainerCreationFailedException("The hostname '$hostname' is more than $maximumHostNameLength characters long.")
        }
    }

    fun toJson(): String {
        return buildJsonObject {
            put("AttachStdin", attachStdin)
            put("AttachStdout", true)
            put("AttachStderr", true)
            put("Tty", useTTY)
            put("OpenStdin", attachStdin)
            put("StdinOnce", attachStdin)
            put("Image", image.id)
            put("Hostname", hostname)
            put("Env", environmentVariables.toDockerFormatJsonArray())
            put("ExposedPorts", formatExposedPorts())

            if (command.isNotEmpty()) {
                put("Cmd", command.toJsonArray())
            }

            if (entrypoint.isNotEmpty()) {
                put("Entrypoint", entrypoint.toJsonArray())
            }

            if (workingDirectory != null) {
                put("WorkingDir", workingDirectory)
            }

            if (userAndGroup != null) {
                put("User", "${userAndGroup.userId}:${userAndGroup.groupId}")
            }

            putJsonObject("HostConfig") {
                put("NetworkMode", network.id)
                put("Binds", formatVolumeMounts())
                put("Devices", formatDeviceMounts())
                put("PortBindings", formatPortMappings())
                put("Privileged", privileged)
                put("Init", init)
                put("CapAdd", formatCapabilitySet(capabilitiesToAdd))
                put("CapDrop", formatCapabilitySet(capabilitiesToDrop))
                putJsonObject("LogConfig") {
                    put("Type", logDriver)
                    put("Config", logOptions.toJsonObject())
                }
                put("ExtraHosts", formatExtraHosts())

                if (shmSize != null) {
                    put("ShmSize", shmSize)
                }
            }

            putJsonObject("Healthcheck") {
                if (healthCheckConfig.command == null) {
                    put("Test", emptyList<String>().toJsonArray())
                } else {
                    put("Test", listOf("CMD-SHELL", healthCheckConfig.command).toJsonArray())
                }

                put("Interval", (healthCheckConfig.interval?.toNanos() ?: 0))
                put("Retries", (healthCheckConfig.retries ?: 0))
                put("StartPeriod", (healthCheckConfig.startPeriod?.toNanos() ?: 0))
                put("Timeout", (healthCheckConfig.timeout?.toNanos() ?: 0))
            }

            putJsonObject("NetworkingConfig") {
                putJsonObject("EndpointsConfig") {
                    putJsonObject(network.id) {
                        put("Aliases", networkAliases.toJsonArray())
                    }
                }
            }
        }.toString()
    }

    private fun formatVolumeMounts(): JsonArray = volumeMounts
        .map { it.toString() }
        .toJsonArray()

    private fun formatDeviceMounts(): JsonArray = JsonArray(
        deviceMounts
            .map {
                buildJsonObject {
                    put("PathOnHost", it.localPath)
                    put("PathInContainer", it.containerPath)
                    put("CgroupPermissions", it.options)
                }
            }
    )

    private fun formatPortMappings(): JsonObject = buildJsonObject {
        portMappings.forEach { mapping ->
            val localPorts = mapping.local.ports
            val containerPorts = mapping.container.ports

            localPorts.zip(containerPorts).forEach { (local, container) ->
                put(
                    "$container/${mapping.protocol}",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("HostIp", "")
                                put("HostPort", local.toString())
                            }
                        )
                    )
                )
            }
        }
    }

    private fun formatExposedPorts(): JsonObject = buildJsonObject {
        portMappings.forEach { mapping ->
            mapping.container.ports.forEach { port ->
                putJsonObject("$port/${mapping.protocol}") {}
            }
        }
    }

    private fun formatCapabilitySet(set: Set<Capability>): JsonArray = set
        .map { it.toString() }
        .toJsonArray()

    private fun formatExtraHosts(): JsonArray = extraHosts
        .map { (host, ip) -> "$host:$ip" }
        .toJsonArray()
}

@Serializable
data class UserAndGroup(val userId: Int, val groupId: Int)
