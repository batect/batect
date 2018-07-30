/*
   Copyright 2017-2018 Charles Korn.

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
import batect.config.VolumeMount
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.json

data class DockerContainerCreationRequest(
    val image: DockerImage,
    val network: DockerNetwork,
    val command: Iterable<String>,
    val hostname: String,
    val networkAlias: String,
    val environmentVariables: Map<String, String>,
    val workingDirectory: String?,
    val volumeMounts: Set<VolumeMount>,
    val portMappings: Set<PortMapping>,
    val healthCheckConfig: HealthCheckConfig,
    val userAndGroup: UserAndGroup?
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
            "Env" to formatEnvironmentVariables()

            if (command.count() > 0) {
                "Cmd" to command.toJsonArray()
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
                "PortBindings" to formatPortMappings()
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
                        "Aliases" to listOf(networkAlias).toJsonArray()
                    }
                }
            }
        }.toString()
    }

    private fun Iterable<String>.toJsonArray() = JsonArray(this.map { JsonPrimitive(it) })

    private fun formatEnvironmentVariables(): JsonArray = environmentVariables
        .map { (key, value) -> "$key=$value" }
        .toJsonArray()

    private fun formatVolumeMounts(): JsonArray = volumeMounts
        .map { it.toString() }
        .toJsonArray()

    private fun formatPortMappings(): JsonObject = json {
        portMappings.forEach {
            "${it.containerPort}/tcp" to json {
                "HostIp" to ""
                "HostPort" to it.localPort.toString()
            }
        }
    }
}

data class UserAndGroup(val userId: Int, val groupId: Int)
