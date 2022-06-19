/*
    Copyright 2017-2022 Charles Korn.

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

import batect.docker.DockerHttpConfig
import batect.docker.DockerNetwork
import batect.docker.Json
import batect.docker.data
import batect.logging.Logger
import batect.os.SystemInfo
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

class NetworksAPI(
    httpConfig: DockerHttpConfig,
    systemInfo: SystemInfo,
    logger: Logger
) : APIBase(httpConfig, systemInfo, logger) {
    fun create(
        name: String,
        driver: String
    ): DockerNetwork {
        logger.info {
            message("Creating new network.")
            data("name", name)
            data("driver", driver)
        }

        val url = urlForNetworks.newBuilder()
            .addPathSegment("create")
            .build()

        val body = buildJsonObject {
            put("Name", name)
            put("CheckDuplicate", true)
            put("Driver", driver)
        }

        val request = Request.Builder()
            .post(jsonRequestBody(body))
            .url(url)
            .build()

        clientWithTimeout(30, TimeUnit.SECONDS).newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not create network.")
                    data("error", error)
                }

                throw NetworkCreationFailedException(error.message)
            }

            val network = networkFromResponse(response)

            logger.info {
                message("Network created.")
                data("network", network)
            }

            return network
        }
    }

    fun delete(network: DockerNetwork) {
        logger.info {
            message("Deleting network.")
            data("network", network)
        }

        val request = Request.Builder()
            .delete()
            .url(urlForNetwork(network))
            .build()

        httpConfig.client.newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not delete network.")
                    data("error", error)
                }

                throw NetworkDeletionFailedException(network.id, error.message)
            }
        }

        logger.info {
            message("Network deleted.")
        }
    }

    fun getByNameOrId(identifier: String): DockerNetwork {
        logger.info {
            message("Getting network.")
            data("identifier", identifier)
        }

        val request = Request.Builder()
            .get()
            .url(urlForNetwork(identifier))
            .build()

        httpConfig.client.newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not get network.")
                    data("error", error)
                }

                if (error.statusCode == 404) {
                    throw NetworkDoesNotExistException(identifier)
                }

                throw NetworkInspectionFailedException(identifier, error.message)
            }

            val network = networkFromResponse(response)

            logger.info {
                message("Network details retrieved.")
                data("identifier", identifier)
                data("network", network)
            }

            return network
        }
    }

    private fun networkFromResponse(response: Response): DockerNetwork {
        val parsedResponse = Json.default.parseToJsonElement(response.body!!.string()).jsonObject
        val networkId = parsedResponse.getValue("Id").jsonPrimitive.content
        return DockerNetwork(networkId)
    }

    private val urlForNetworks: HttpUrl = baseUrl.newBuilder()
        .addPathSegment("networks")
        .build()

    private fun urlForNetwork(network: DockerNetwork): HttpUrl = urlForNetwork(network.id)

    private fun urlForNetwork(identifier: String): HttpUrl = urlForNetworks.newBuilder()
        .addPathSegment(identifier)
        .build()
}
