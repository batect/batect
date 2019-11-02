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

import batect.config.Container
import batect.config.VolumeMount
import batect.execution.ContainerRuntimeConfiguration

class DockerContainerCreationRequestFactory(
    private val environmentVariableProvider: DockerContainerEnvironmentVariableProvider
) {
    fun create(
        container: Container,
        image: DockerImage,
        network: DockerNetwork,
        config: ContainerRuntimeConfiguration,
        additionalVolumeMounts: Set<VolumeMount>,
        propagateProxyEnvironmentVariables: Boolean,
        userAndGroup: UserAndGroup?,
        terminalType: String?,
        allContainersInNetwork: Set<Container>
    ): DockerContainerCreationRequest {
        return DockerContainerCreationRequest(
            image,
            network,
            if (config.command != null) config.command.parsedCommand else emptyList(),
            if (config.entrypoint != null) config.entrypoint.parsedCommand else emptyList(),
            container.name,
            container.additionalHostnames + container.name,
            environmentVariableProvider.environmentVariablesFor(container, config, propagateProxyEnvironmentVariables, terminalType, allContainersInNetwork),
            config.workingDirectory,
            container.volumeMounts + additionalVolumeMounts,
            container.portMappings + config.additionalPortMappings,
            container.healthCheckConfig,
            userAndGroup,
            container.privileged,
            container.enableInitProcess,
            container.capabilitiesToAdd,
            container.capabilitiesToDrop
        )
    }
}
