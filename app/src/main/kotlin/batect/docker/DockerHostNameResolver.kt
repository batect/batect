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

import batect.docker.client.DockerSystemInfoClient
import batect.docker.client.DockerVersionInfoRetrievalResult
import batect.os.OperatingSystem
import batect.os.SystemInfo
import batect.Version
import batect.VersionComparisonMode

class DockerHostNameResolver(
    private val systemInfo: SystemInfo,
    private val dockerSystemInfoClient: DockerSystemInfoClient
) {
    private val dockerVersionInfoRetrievalResult by lazy { dockerSystemInfoClient.getDockerVersionInfo() }

    fun resolveNameOfDockerHost(): DockerHostNameResolutionResult = when (systemInfo.operatingSystem) {
        OperatingSystem.Mac -> getDockerHostName("mac")
        OperatingSystem.Windows -> getDockerHostName("win")
        OperatingSystem.Linux, OperatingSystem.Other -> DockerHostNameResolutionResult.NotSupported
    }

    private fun getDockerHostName(operatingSystemPart: String): DockerHostNameResolutionResult {
        val version = (dockerVersionInfoRetrievalResult as? DockerVersionInfoRetrievalResult.Succeeded)?.info?.version

        return when {
            version == null -> DockerHostNameResolutionResult.NotSupported
            version.isGreaterThanOrEqualToDockerVersion(Version(18, 3, 0)) -> DockerHostNameResolutionResult.Resolved("host.docker.internal")
            version.isGreaterThanOrEqualToDockerVersion(Version(17, 12, 0)) -> DockerHostNameResolutionResult.Resolved("docker.for.$operatingSystemPart.host.internal")
            version.isGreaterThanOrEqualToDockerVersion(Version(17, 6, 0)) -> DockerHostNameResolutionResult.Resolved("docker.for.$operatingSystemPart.localhost")
            else -> DockerHostNameResolutionResult.NotSupported
        }
    }

    private fun Version.isGreaterThanOrEqualToDockerVersion(dockerVersion: Version): Boolean = this.compareTo(dockerVersion, VersionComparisonMode.DockerStyle) >= 0
}

sealed class DockerHostNameResolutionResult {
    object NotSupported : DockerHostNameResolutionResult()
    data class Resolved(val hostName: String) : DockerHostNameResolutionResult()
}
