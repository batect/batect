/*
    Copyright 2017-2021 Charles Korn.

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
import batect.docker.api.BuilderVersion
import batect.docker.api.PingResponse
import batect.docker.api.SystemInfoAPI
import batect.docker.data
import batect.docker.minimumDockerAPIVersion
import batect.docker.minimumDockerVersion
import batect.logging.LogMessageBuilder
import batect.logging.Logger
import batect.primitives.Version
import batect.primitives.VersionComparisonMode
import batect.telemetry.AttributeValue
import batect.telemetry.TelemetrySessionBuilder
import batect.telemetry.addUnhandledExceptionEvent
import java.io.IOException

class SystemInfoClient(
    private val api: SystemInfoAPI,
    private val telemetrySessionBuilder: TelemetrySessionBuilder,
    private val logger: Logger
) {
    // Why does this method not just throw exceptions when things fail, like the other methods in this class do?
    // It's used in a number of places where throwing exceptions would be undesirable or unsafe (eg. during logging startup
    // and when showing version info), so instead we wrap the result.
    fun getDockerVersionInfo(): DockerVersionInfoRetrievalResult {
        return try {
            val info = api.getServerVersionInfo()

            DockerVersionInfoRetrievalResult.Succeeded(info)
        } catch (t: Throwable) {
            logger.error {
                message("An exception was thrown while getting Docker version info.")
                exception(t)
            }

            DockerVersionInfoRetrievalResult.Failed("Could not get Docker version information because ${t.javaClass.simpleName} was thrown: ${t.message}")
        }
    }

    fun checkConnectivity(): DockerConnectivityCheckResult {
        logger.info {
            message("Checking Docker daemon connectivity.")
        }

        try {
            val pingResponse = api.ping()

            logger.info {
                message("Ping succeeded.")
                data("response", pingResponse)
            }

            val versionInfo = api.getServerVersionInfo()

            logger.info {
                message("Getting version info succeeded.")
                data("versionInfo", versionInfo)
            }

            if (Version.parse(versionInfo.apiVersion).compareTo(Version.parse(minimumDockerAPIVersion), VersionComparisonMode.DockerStyle) < 0) {
                telemetrySessionBuilder.addEvent("IncompatibleDockerVersion", mapOf("dockerVersion" to AttributeValue(versionInfo.version.toString())))

                return DockerConnectivityCheckResult.Failed("Batect requires Docker $minimumDockerVersion or later, but version ${versionInfo.version} is installed.")
            }

            val containerType = DockerContainerType.values().singleOrNull { it.name.equals(versionInfo.operatingSystem, ignoreCase = true) }

            if (containerType == null) {
                return DockerConnectivityCheckResult.Failed("Batect requires Docker to be running in Linux or Windows containers mode.")
            }

            return DockerConnectivityCheckResult.Succeeded(containerType, versionInfo.version, pingResponse.builderVersion, versionInfo.experimental)
        } catch (e: DockerException) {
            logger.warn {
                message("Connectivity check failed.")
                exception(e)
            }

            telemetrySessionBuilder.addUnhandledExceptionEvent(e, isUserFacing = true)

            return DockerConnectivityCheckResult.Failed(e.message!!)
        } catch (e: IOException) {
            logger.warn {
                message("Connectivity check failed.")
                exception(e)
            }

            telemetrySessionBuilder.addUnhandledExceptionEvent(e, isUserFacing = true)

            return DockerConnectivityCheckResult.Failed(e.message!!)
        }
    }

    private fun LogMessageBuilder.data(key: String, value: PingResponse) = this.data(key, value, PingResponse.serializer())
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
    data class Succeeded(val containerType: DockerContainerType, val dockerVersion: Version, val builderVersion: BuilderVersion, val experimentalFeaturesEnabled: Boolean) : DockerConnectivityCheckResult()

    data class Failed(val message: String) : DockerConnectivityCheckResult()
}

enum class DockerContainerType {
    Linux,
    Windows
}
