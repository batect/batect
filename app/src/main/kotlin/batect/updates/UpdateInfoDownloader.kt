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

package batect.updates

import batect.logging.Logger
import batect.logging.data
import batect.primitives.Version
import batect.utils.Json
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.ZoneOffset
import java.time.ZonedDateTime

class UpdateInfoDownloader(private val client: OkHttpClient, private val logger: Logger, private val dateTimeProvider: () -> ZonedDateTime) {
    constructor(client: OkHttpClient, logger: Logger) : this(client, logger, { ZonedDateTime.now(ZoneOffset.UTC) })

    fun getLatestVersionInfo(): UpdateInfo {
        val url = "https://updates.batect.dev/v1/latest"
        val request = Request.Builder()
            .url(url)
            .build()

        try {
            logger.info {
                message("Downloading latest version information.")
                data("url", request.url)
            }

            client.newCall(request).execute().use { response ->
                logger.info {
                    message("Finished downloading latest version information.")
                    data("successful", response.isSuccessful)
                    data("httpResponseCode", response.code)
                    data("httpResponseMessage", response.message)
                }

                if (!response.isSuccessful) {
                    throw UpdateInfoDownloadException("The server returned HTTP ${response.code}.")
                }

                val versionInfo = Json.ignoringUnknownKeys.decodeFromString(VersionInfo.serializer(), response.body!!.string())
                val scripts = extractScriptInfo(versionInfo)
                val updateInfo = UpdateInfo(Version.parse(versionInfo.version), versionInfo.url, dateTimeProvider(), scripts)

                logger.info {
                    message("Parsed latest version information.")
                    data("updateInfo", updateInfo)
                }

                return updateInfo
            }
        } catch (e: Throwable) {
            logger.info {
                message("Downloading latest version information failed with an exception.")
                data("url", request.url)
                exception(e)
            }

            throw UpdateInfoDownloadException("Could not download latest release information from $url: ${e.message}", e)
        }
    }

    private fun extractScriptInfo(versionInfo: VersionInfo): List<ScriptInfo> {
        return versionInfo.files
            .filter { it.name in setOf("batect", "batect.cmd") }
            .map { ScriptInfo(it.name, it.url) }
    }

    @Serializable
    private data class VersionInfo(
        val version: String,
        val url: String,
        val files: List<VersionFile>
    )

    @Serializable
    private data class VersionFile(
        val name: String,
        val url: String
    )
}

class UpdateInfoDownloadException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
