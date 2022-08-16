/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.proxies

import batect.docker.DockerHostNameResolutionResult
import batect.docker.DockerHostNameResolver
import batect.logging.Logger
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ProxyEnvironmentVariablePreprocessor(private val hostNameResolver: DockerHostNameResolver, private val logger: Logger) {
    fun process(value: String): String {
        return when (val hostNameResolutionResult = hostNameResolver.resolveNameOfDockerHost()) {
            is DockerHostNameResolutionResult.NotSupported -> {
                logger.info {
                    message("Not attempting to preprocess proxy environment variable value because getting the local Docker hostname is not supported.")
                    data("value", value)
                }

                value
            }
            is DockerHostNameResolutionResult.Resolved -> replaceLocalhost(value, hostNameResolutionResult.hostName)
        }
    }

    private fun replaceLocalhost(value: String, localhostName: String): String {
        val parsed = value.toHttpUrlOrNull()

        if (parsed == null) {
            logger.warn {
                message("Not attempting to preprocess proxy environment variable value because it is not a valid URL.")
                data("value", value)
            }

            return value
        }

        if (parsed.host in setOf("localhost", "127.0.0.1", "::1")) {
            val newValue = parsed.newBuilder().host(localhostName).toString()

            logger.info {
                message("Modified proxy environment variable that referred to the local machine.")
                data("originalValue", value)
                data("newValue", newValue)
            }

            return newValue
        }

        logger.info {
            message("Not preprocessing proxy environment variable value because it does not refer to the local machine.")
            data("value", value)
        }

        return value
    }
}
