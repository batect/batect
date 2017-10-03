/*
   Copyright 2017 Charles Korn.

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

import batect.utils.Version
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.ZoneOffset
import java.time.ZonedDateTime

class UpdateInfoDownloader(private val client: OkHttpClient, private val dateTimeProvider: () -> ZonedDateTime) {
    constructor(client: OkHttpClient) : this(client, { ZonedDateTime.now(ZoneOffset.UTC) })

    fun getLatestVersionInfo(): UpdateInfo {
        val url = "https://api.github.com/repos/charleskorn/batect/releases/latest"
        val request = Request.Builder()
            .url(url)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw UpdateInfoDownloadException("The server returned HTTP ${response.code()}.")
                }

                val mapper = jacksonObjectMapper()
                mapper.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
                mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

                val releaseInfo = mapper.readValue(response.body()!!.byteStream(), GitHubReleaseInfo::class.java)

                return UpdateInfo(Version.parse(releaseInfo.tagName), releaseInfo.htmlUrl, dateTimeProvider())
            }
        } catch (e: Throwable) {
            throw UpdateInfoDownloadException("Could not download latest release information from $url: ${e.message}", e)
        }
    }

    private data class GitHubReleaseInfo(val tagName: String, val htmlUrl: String)
}

class UpdateInfoDownloadException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
