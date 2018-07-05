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

import batect.os.OperatingSystem
import batect.os.SystemInfo
import batect.utils.Version

class DockerHostNameResolver(
    private val systemInfo: SystemInfo,
    private val dockerClient: DockerClient
) {
    private val dockerVersionInfoRetrievalResult by lazy { dockerClient.getDockerVersionInfo() }

    fun resolveNameOfDockerHost(): DockerHostNameResolutionResult {
        if (systemInfo.operatingSystem != OperatingSystem.Mac) {
            return DockerHostNameResolutionResult.NotSupported
        }

        val version = (dockerVersionInfoRetrievalResult as? DockerVersionInfoRetrievalResult.Succeeded)?.info?.server?.version

        return when {
            version == null -> DockerHostNameResolutionResult.NotSupported
            version >= Version(18, 3, 0) -> DockerHostNameResolutionResult.Resolved("host.docker.internal")
            version >= Version(17, 12, 0) -> DockerHostNameResolutionResult.Resolved("docker.for.mac.host.internal")
            version >= Version(17, 6, 0) -> DockerHostNameResolutionResult.Resolved("docker.for.mac.localhost")
            else -> DockerHostNameResolutionResult.NotSupported
        }
    }
}

sealed class DockerHostNameResolutionResult {
    object NotSupported : DockerHostNameResolutionResult()
    data class Resolved(val hostName: String) : DockerHostNameResolutionResult()
}
