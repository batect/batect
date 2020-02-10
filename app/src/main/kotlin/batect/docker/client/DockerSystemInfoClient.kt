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

package batect.docker.client

import batect.docker.DockerException
import batect.docker.DockerVersionInfo
import batect.docker.api.SystemInfoAPI
import batect.docker.data
import batect.docker.minimumDockerAPIVersion
import batect.docker.minimumDockerVersion
import batect.logging.Logger
import batect.utils.Version
import batect.utils.VersionComparisonMode
import java.io.IOException

class DockerSystemInfoClient(
    private val api: SystemInfoAPI,
    private val logger: Logger
) {
    // Why does this method not just throw exceptions when things fail, like the other methods in this class do?
    // It's used in a number of places where throwing exceptions would be undesirable or unsafe (eg. during logging startup
    // and when showing version info), so instead we wrap the result.
    fun getDockerVersionInfo(): DockerVersionInfoRetrievalResult {
        try {
            val info = api.getServerVersionInfo()

            return DockerVersionInfoRetrievalResult.Succeeded(info)
        } catch (t: Throwable) {
            logger.error {
                message("An exception was thrown while getting Docker version info.")
                exception(t)
            }

            return DockerVersionInfoRetrievalResult.Failed("Could not get Docker version information because ${t.javaClass.simpleName} was thrown: ${t.message}")
        }
    }

    fun checkConnectivity(): DockerConnectivityCheckResult {
        logger.info {
            message("Checking Docker daemon connectivity.")
        }

        try {
            api.ping()

            logger.info {
                message("Ping succeeded.")
            }

            val versionInfo = api.getServerVersionInfo()

            logger.info {
                message("Getting version info succeeded.")
                data("versionInfo", versionInfo)
            }

            if (Version.parse(versionInfo.apiVersion).compareTo(Version.parse(minimumDockerAPIVersion), VersionComparisonMode.DockerStyle) < 0) {
                return DockerConnectivityCheckResult.Failed("batect requires Docker $minimumDockerVersion or later, but version ${versionInfo.version} is installed.")
            }

            val containerType = DockerContainerType.values().singleOrNull { it.name.equals(versionInfo.operatingSystem, ignoreCase = true) }

            if (containerType == null) {
                return DockerConnectivityCheckResult.Failed("batect requires Docker to be running in Linux or Windows containers mode.")
            }

            return DockerConnectivityCheckResult.Succeeded(containerType)
        } catch (e: DockerException) {
            logger.warn {
                message("Connectivity check failed.")
                exception(e)
            }

            return DockerConnectivityCheckResult.Failed(e.message!!)
        } catch (e: IOException) {
            logger.warn {
                message("Connectivity check failed.")
                exception(e)
            }

            return DockerConnectivityCheckResult.Failed(e.message!!)
        }
    }
}

sealed class DockerVersionInfoRetrievalResult {
    data class Succeeded(val info: DockerVersionInfo) : DockerVersionInfoRetrievalResult() {
        override fun toString(): String = info.toString()
    }

    data class Failed(val message: String) : DockerVersionInfoRetrievalResult() {
        override fun toString(): String = "($message)"
    }
}

sealed class DockerConnectivityCheckResult {
    data class Succeeded(val containerType: DockerContainerType) : DockerConnectivityCheckResult()

    data class Failed(val message: String) : DockerConnectivityCheckResult()
}

enum class DockerContainerType {
    Linux,
    Windows
}
