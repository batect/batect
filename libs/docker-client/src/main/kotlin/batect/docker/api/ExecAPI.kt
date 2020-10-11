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

package batect.docker.api

import batect.docker.DockerContainer
import batect.docker.DockerExecInstance
import batect.docker.DockerExecInstanceInfo
import batect.docker.DockerHttpConfig
import batect.docker.ExecCreationRequest
import batect.docker.ExecFailedException
import batect.docker.Json
import batect.docker.data
import batect.docker.run.ConnectionHijacker
import batect.docker.run.ContainerOutputStream
import batect.logging.Logger
import batect.os.SystemInfo
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.Request
import java.util.concurrent.TimeUnit

// Flow is:
// - Create exec instance
// - Start it (streams output and runs to completion)
// - Inspect it to get exit code

class ExecAPI(
    httpConfig: DockerHttpConfig,
    systemInfo: SystemInfo,
    logger: Logger,
    private val hijackerFactory: () -> ConnectionHijacker = ::ConnectionHijacker
) : APIBase(httpConfig, systemInfo, logger) {
    fun create(container: DockerContainer, creationRequest: ExecCreationRequest): DockerExecInstance {
        logger.info {
            message("Creating exec instance.")
            data("request", creationRequest)
        }

        val url = baseUrl.newBuilder()
            .addPathSegment("containers")
            .addPathSegment(container.id)
            .addPathSegment("exec")
            .build()

        val body = creationRequest.toJson()

        val request = Request.Builder()
            .post(jsonRequestBody(body))
            .url(url)
            .build()

        clientWithTimeout(30, TimeUnit.SECONDS).newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Exec instance creation failed.")
                    data("error", error)
                }

                throw ExecFailedException("Output from Docker was: ${error.message}")
            }

            val instance = Json.ignoringUnknownKeys.decodeFromString(DockerExecInstance.serializer(), response.body!!.string())

            logger.info {
                message("Exec instance created.")
                data("id", instance.id)
            }

            return instance
        }
    }

    fun start(creationRequest: ExecCreationRequest, instance: DockerExecInstance): ContainerOutputStream {
        logger.info {
            message("Starting exec instance.")
            data("instance", instance)
            data("creationRequest", creationRequest)
        }

        val url = urlForInstance(instance).newBuilder()
            .addPathSegment("start")
            .build()

        val body = buildJsonObject {
            put("Detach", false)
            put("Tty", creationRequest.attachTty)
        }.toString()

        val request = Request.Builder()
            .post(jsonRequestBody(body))
            .url(url)
            .header("Connection", "Upgrade")
            .header("Upgrade", "tcp")
            .build()

        val hijacker = hijackerFactory()

        val client = httpConfig.client.newBuilder()
            .readTimeout(0, TimeUnit.NANOSECONDS)
            .connectionPool(ConnectionPool(5, Long.MAX_VALUE, TimeUnit.NANOSECONDS))
            .addNetworkInterceptor(hijacker)
            .build()

        val response = client.newCall(request).execute()

        checkForFailure(response) { error ->
            logger.error {
                message("Starting exec instance failed.")
                data("error", error)
                data("instance", instance)
            }

            throw ExecFailedException("Starting exec instance '${instance.id}' failed: ${error.message}")
        }

        return ContainerOutputStream(response, hijacker.source!!)
    }

    fun inspect(instance: DockerExecInstance): DockerExecInstanceInfo {
        logger.info {
            message("Inspecting exec instance.")
            data("instance", instance)
        }

        val url = urlForInstance(instance).newBuilder()
            .addPathSegment("json")
            .build()

        val request = Request.Builder()
            .get()
            .url(url)
            .build()

        httpConfig.client.newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not inspect exec instance.")
                    data("instance", instance)
                    data("error", error)
                }

                throw ExecInstanceInspectionFailedException("Could not inspect exec instance '${instance.id}': ${error.message}")
            }

            return Json.ignoringUnknownKeys.decodeFromString(DockerExecInstanceInfo.serializer(), response.body!!.string())
        }
    }

    private fun urlForInstance(instance: DockerExecInstance): HttpUrl = baseUrl.newBuilder()
        .addPathSegment("exec")
        .addPathSegment(instance.id)
        .build()
}
