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
import batect.docker.DockerVolume
import batect.docker.data
import batect.logging.LogMessageBuilder
import batect.logging.Logger
import batect.os.SystemInfo
import batect.utils.Json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.set
import okhttp3.HttpUrl
import okhttp3.Request

class VolumesAPI(
    httpConfig: DockerHttpConfig,
    systemInfo: SystemInfo,
    logger: Logger
) : APIBase(httpConfig, systemInfo, logger) {
    fun getAll(): Set<DockerVolume> {
        logger.info {
            message("Getting all volumes.")
        }

        val request = Request.Builder()
            .get()
            .url(urlForVolumes)
            .build()

        httpConfig.client.newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not get all volumes.")
                    data("error", error)
                }

                throw GetAllVolumesFailedException(error.message)
            }

            val body = response.body!!.string()
            val parsedBody = Json.nonstrictParser.parse(VolumesList.serializer(), body)

            logger.info {
                message("Retrieved all volumes.")
                data("volumes", parsedBody.volumes)
            }

            return parsedBody.volumes
        }
    }

    fun delete(volume: DockerVolume) {
        logger.info {
            message("Deleting volume.")
            data("volume", volume)
        }

        val request = Request.Builder()
            .delete()
            .url(urlForVolume(volume))
            .build()

        httpConfig.client.newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not delete volume.")
                    data("error", error)
                }

                throw VolumeDeletionFailedException(volume.name, error.message)
            }
        }

        logger.info {
            message("Volume deleted.")
        }
    }

    private val urlForVolumes: HttpUrl = baseUrl.newBuilder()
        .addPathSegment("volumes")
        .build()

    private fun urlForVolume(volume: DockerVolume): HttpUrl = urlForVolumes.newBuilder()
        .addPathSegment(volume.name)
        .build()

    @Serializable
    private data class VolumesList(
        @SerialName("Volumes") val volumes: Set<DockerVolume>
    )

    fun LogMessageBuilder.data(key: String, value: Set<DockerVolume>) = this.data(key, value, DockerVolume.serializer().set)
}
