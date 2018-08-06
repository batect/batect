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

import kotlinx.serialization.Optional
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class DockerImage(val id: String)
data class DockerContainer(val id: String)
data class DockerContainerRunResult(val exitCode: Int)
data class DockerNetwork(val id: String)

@Serializable
data class DockerContainerInfo(@SerialName("State") val state: DockerContainerState)

@Serializable
data class DockerContainerState(@SerialName("Health") @Optional val health: DockerContainerHealthCheckState? = null)

@Serializable
data class DockerContainerHealthCheckState(@SerialName("Log") val log: List<DockerHealthCheckResult> = emptyList())

@Serializable
data class DockerHealthCheckResult(@SerialName("ExitCode") val exitCode: Int, @SerialName("Output") val output: String)
