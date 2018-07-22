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

package batect.updates

import batect.logging.Logger
import batect.utils.Version
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JSON
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.ZoneOffset
import java.time.ZonedDateTime

class UpdateInfoDownloader(private val client: OkHttpClient, private val logger: Logger, private val dateTimeProvider: () -> ZonedDateTime) {
    constructor(client: OkHttpClient, logger: Logger) : this(client, logger, { ZonedDateTime.now(ZoneOffset.UTC) })

    fun getLatestVersionInfo(): UpdateInfo {
        val url = "https://api.github.com/repos/charleskorn/batect/releases/latest"
        val request = Request.Builder()
            .url(url)
            .build()

        try {
            logger.info {
                message("Downloading latest version information.")
                data("url", request.url().toString())
            }

            client.newCall(request).execute().use { response ->
                logger.info {
                    message("Finished downloading latest version information.")
                    data("successful", response.isSuccessful)
                    data("httpResponseCode", response.code())
                    data("httpResponseMessage", response.message())
                }

                if (!response.isSuccessful) {
                    throw UpdateInfoDownloadException("The server returned HTTP ${response.code()}.")
                }

                val releaseInfo = JSON.nonstrict.parse<GitHubReleaseInfo>(response.body()!!.string())
                val updateInfo = UpdateInfo(Version.parse(releaseInfo.tagName), releaseInfo.htmlUrl, dateTimeProvider(), releaseInfo.scriptDownloadUrl)

                logger.info {
                    message("Parsed latest version information.")
                    data("updateInfo", updateInfo)
                }

                return updateInfo
            }
        } catch (e: Throwable) {
            logger.info {
                message("Downloading latest version information failed with an exception.")
                data("url", request.url().toString())
                exception(e)
            }

            throw UpdateInfoDownloadException("Could not download latest release information from $url: ${e.message}", e)
        }
    }

    @Serializable
    private data class GitHubReleaseInfo(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
        val assets: List<GitHubReleaseAsset>
    ) {
        @Transient
        val scriptDownloadUrl: String? = assets
            .singleOrNull { it.name == "batect" && it.contentType == "application/octet-stream" }
            ?.browserDownloadUrl
    }

    @Serializable
    private data class GitHubReleaseAsset(
        val name: String,
        @SerialName("content_type") val contentType: String,
        @SerialName("browser_download_url") val browserDownloadUrl: String
    )
}

class UpdateInfoDownloadException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
