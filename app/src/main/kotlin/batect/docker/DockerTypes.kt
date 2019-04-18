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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration

data class DockerImage(val id: String)
data class DockerContainer(val id: String)
data class DockerContainerRunResult(val exitCode: Int)
data class DockerNetwork(val id: String)
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
