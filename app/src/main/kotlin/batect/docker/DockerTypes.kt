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

package batect.docker

import batect.logging.LogMessageBuilder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration

@Serializable
data class DockerImage(val id: String)

@Serializable
data class DockerContainer(val id: String, val name: String? = null)

@Serializable
data class DockerVolumeMount(val localPath: String, val containerPath: String, val options: String? = null) {
    override fun toString(): String = if (options == null) "$localPath:$containerPath" else "$localPath:$containerPath:$options"
}

data class DockerContainerRunResult(val exitCode: Long)

@Serializable
data class DockerNetwork(val id: String)

@Serializable
data class DockerEvent(val status: String)

@Serializable
data class DockerContainerInfo(
    @SerialName("State") val state: DockerContainerState,
    @SerialName("Config") val config: DockerContainerConfiguration
)

@Serializable
data class DockerContainerState(
    @SerialName("Health") val health: DockerContainerHealthCheckState? = null
)

@Serializable
data class DockerContainerHealthCheckState(
    @SerialName("Log") val log: List<DockerHealthCheckResult>
)

@Serializable
data class DockerHealthCheckResult(
    @SerialName("ExitCode") val exitCode: Int,
    @SerialName("Output") val output: String
)

@Serializable
data class DockerContainerConfiguration(@SerialName("Healthcheck") val healthCheck: DockerContainerHealthCheckConfig)

@Serializable
data class DockerContainerHealthCheckConfig(
    @SerialName("Test") val test: List<String>? = null,
    @SerialName("Interval") @Serializable(with = DurationSerializer::class) val interval: Duration = Duration.ofSeconds(30),
    @SerialName("Timeout") @Serializable(with = DurationSerializer::class) val timeout: Duration = Duration.ofSeconds(30),
    @SerialName("StartPeriod") @Serializable(with = DurationSerializer::class) val startPeriod: Duration = Duration.ZERO,
    @SerialName("Retries") val retries: Int = 3
)

@Serializable
data class DockerExecInstance(
    @SerialName("Id") val id: String
)

@Serializable
data class DockerExecInstanceInfo(
    @SerialName("ExitCode") val exitCode: Int?,
    @SerialName("Running") val running: Boolean
)

@Serializable
data class DockerExecResult(
    val exitCode: Int,
    val output: String
)

fun LogMessageBuilder.data(key: String, value: DockerImage) = this.data(key, value, DockerImage.serializer())
fun LogMessageBuilder.data(key: String, value: DockerContainer) = this.data(key, value, DockerContainer.serializer())
fun LogMessageBuilder.data(key: String, value: DockerNetwork) = this.data(key, value, DockerNetwork.serializer())
fun LogMessageBuilder.data(key: String, value: DockerEvent) = this.data(key, value, DockerEvent.serializer())
fun LogMessageBuilder.data(key: String, value: DockerExecInstance) = this.data(key, value, DockerExecInstance.serializer())
fun LogMessageBuilder.data(key: String, value: DockerExecResult) = this.data(key, value, DockerExecResult.serializer())
