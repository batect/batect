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

import batect.docker.DockerHttpConfig
import batect.docker.DockerNetwork
import batect.docker.data
import batect.logging.Logger
import batect.os.SystemInfo
import batect.utils.Json
import kotlinx.serialization.json.json
import okhttp3.HttpUrl
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

class NetworksAPI(
    httpConfig: DockerHttpConfig,
    systemInfo: SystemInfo,
    logger: Logger
) : APIBase(httpConfig, systemInfo, logger) {
    fun create(driver: String): DockerNetwork {
        logger.info {
            message("Creating new network.")
        }

        val url = urlForNetworks.newBuilder()
            .addPathSegment("create")
            .build()

        val body = json {
            "Name" to UUID.randomUUID().toString()
            "CheckDuplicate" to true
            "Driver" to driver
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

            val parsedResponse = Json.parser.parseJson(response.body!!.string()).jsonObject
            val networkId = parsedResponse.getValue("Id").primitive.content

            logger.info {
                message("Network created.")
                data("networkId", networkId)
            }

            return DockerNetwork(networkId)
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

    private val urlForNetworks: HttpUrl = baseUrl.newBuilder()
        .addPathSegment("networks")
        .build()

    private fun urlForNetwork(network: DockerNetwork): HttpUrl = urlForNetworks.newBuilder()
        .addPathSegment(network.id)
        .build()
}
