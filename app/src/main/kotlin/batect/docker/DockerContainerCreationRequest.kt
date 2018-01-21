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
)

data class UserAndGroup(val userId: Int, val groupId: Int)
