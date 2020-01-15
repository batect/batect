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

package batect.integrationtests

import batect.config.DeviceMount
import batect.config.HealthCheckConfig
import batect.config.PortMapping
import batect.config.VolumeMount
import batect.docker.DockerContainerCreationRequest
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.docker.UserAndGroup

fun creationRequestForContainer(
    image: DockerImage,
    network: DockerNetwork,
    command: List<String>,
    volumeMounts: Set<VolumeMount> = emptySet(),
    deviceMounts: Set<DeviceMount> = emptySet(),
    portMappings: Set<PortMapping> = emptySet(),
    userAndGroup: UserAndGroup? = null
): DockerContainerCreationRequest {
    return DockerContainerCreationRequest(
        image,
        network,
        command,
        emptyList(),
        "test-container",
        setOf("test-container"),
        emptyMap(),
        null,
        volumeMounts,
        deviceMounts,
        portMappings,
        HealthCheckConfig(),
        userAndGroup,
        privileged = false,
        init = false,
        capabilitiesToAdd = emptySet(),
        capabilitiesToDrop = emptySet()
    )
}
