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

package batect.docker

import batect.config.HealthCheckConfig
import batect.config.PortMapping
import batect.config.DeviceMount
import batect.config.VolumeMount
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.json

@Serializable
data class DockerContainerCreationRequest(
    val image: DockerImage,
    val network: DockerNetwork,
    val command: List<String>,
    val entrypoint: List<String>,
    val hostname: String,
    val networkAliases: Set<String>,
    val environmentVariables: Map<String, String>,
    val workingDirectory: String?,
    val volumeMounts: Set<VolumeMount>,
    val deviceMounts: Set<DeviceMount>,
    val portMappings: Set<PortMapping>,
    val healthCheckConfig: HealthCheckConfig,
    val userAndGroup: UserAndGroup?,
    val privileged: Boolean,
    val init: Boolean,
    val capabilitiesToAdd: Set<Capability>,
    val capabilitiesToDrop: Set<Capability>,
    val logConfigType: String?
) {
    fun toJson(): String {
        return json {
            "AttachStdin" to true
            "AttachStdout" to true
            "AttachStderr" to true
            "Tty" to true
            "OpenStdin" to true
            "StdinOnce" to true
            "Image" to image.id
            "Hostname" to hostname
            "Env" to environmentVariables.toDockerFormatJsonArray()
            "ExposedPorts" to formatExposedPorts()

            if (command.count() > 0) {
                "Cmd" to command.toJsonArray()
            }

            if (entrypoint.count() > 0) {
                "Entrypoint" to entrypoint.toJsonArray()
            }

            if (workingDirectory != null) {
                "WorkingDir" to workingDirectory
            }

            if (userAndGroup != null) {
                "User" to "${userAndGroup.userId}:${userAndGroup.groupId}"
            }

            "HostConfig" to json {
                "NetworkMode" to network.id
                "Binds" to formatVolumeMounts()
                "Devices" to formatDeviceMounts()
                "PortBindings" to formatPortMappings()
                "Privileged" to privileged
                "Init" to init
                "CapAdd" to formatCapabilitySet(capabilitiesToAdd)
                "CapDrop" to formatCapabilitySet(capabilitiesToDrop)
                "LogConfig" to json {
                    "Type" to logConfigType
                }
            }

            "Healthcheck" to json {
                "Test" to emptyList<String>().toJsonArray()
                "Interval" to (healthCheckConfig.interval?.toNanos() ?: 0)
                "Retries" to (healthCheckConfig.retries ?: 0)
                "StartPeriod" to (healthCheckConfig.startPeriod?.toNanos() ?: 0)
            }

            "NetworkingConfig" to json {
                "EndpointsConfig" to json {
                    network.id to json {
                        "Aliases" to networkAliases.toJsonArray()
                    }
                }
            }
        }.toString()
    }

    private fun formatVolumeMounts(): JsonArray = volumeMounts
        .map { it.toString() }
        .toJsonArray()

    private fun formatDeviceMounts(): JsonArray = JsonArray(deviceMounts
        .map { json {
            "PathOnHost" to it.localPath
            "PathInContainer" to it.containerPath
            "CgroupPermissions" to it.options
        } })

    private fun formatPortMappings(): JsonObject = json {
        portMappings.forEach {
            "${it.containerPort}/tcp" to JsonArray(listOf(
                json {
                    "HostIp" to ""
                    "HostPort" to it.localPort.toString()
                }
            ))
        }
    }

    private fun formatExposedPorts(): JsonObject = json {
        portMappings.forEach {
            "${it.containerPort}/tcp" to json {}
        }
    }

    private fun formatCapabilitySet(set: Set<Capability>): JsonArray = set
        .map { it.toString() }
        .toJsonArray()
}

@Serializable
data class UserAndGroup(val userId: Int, val groupId: Int)
