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

package batect.telemetry

import batect.logging.Logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration

class AbacusClient(
    private val client: OkHttpClient,
    private val logger: Logger,
) {
    private val host = System.getenv().getOrDefault("BATECT_TELEMETRY_HOST", "api.abacus.batect.dev")
    private val url = "https://$host/v1/sessions"
    private val jsonMediaType = "application/json".toMediaType()

    fun upload(sessionJson: ByteArray, timeoutAfter: Duration? = null) {
        val clientWithTimeout = if (timeoutAfter == null) client else client.newBuilder().callTimeout(timeoutAfter).build()

        val request = Request.Builder()
            .method("PUT", sessionJson.toRequestBody(jsonMediaType))
            .url(url)
            .build()

        logger.info {
            message("Uploading session.")
            data("url", url)
        }

        try {
            clientWithTimeout.newCall(request).execute().use { response ->
                logger.info {
                    message("Finished uploading session.")
                    data("successful", response.isSuccessful)
                    data("httpResponseCode", response.code)
                    data("httpResponseMessage", response.message)
                }

                if (!response.isSuccessful && response.code != 304) {
                    throw AbacusClientException("The server returned HTTP ${response.code}.")
                }
            }
        } catch (e: Throwable) {
            throw AbacusClientException("HTTP PUT $url failed: ${e.message}", e)
        }
    }
}

class AbacusClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
