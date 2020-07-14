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
import batect.docker.Json
import batect.docker.minimumDockerAPIVersion
import batect.logging.Logger
import batect.os.SystemInfo
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

abstract class APIBase(
    protected val httpConfig: DockerHttpConfig,
    protected val systemInfo: SystemInfo,
    protected val logger: Logger
) {
    protected val baseUrl: HttpUrl = httpConfig.baseUrl.newBuilder()
        .addPathSegment("v$minimumDockerAPIVersion")
        .build()

    protected fun clientWithTimeout(quantity: Long, unit: TimeUnit): OkHttpClient = httpConfig.client.newBuilder()
        .readTimeout(quantity, unit)
        .build()

    protected fun clientWithNoTimeout() = clientWithTimeout(0, TimeUnit.NANOSECONDS)

    protected fun checkForFailure(response: Response, errorHandler: (DockerAPIError) -> Unit) {
        if (response.isSuccessful || response.code == 101) {
            return
        }

        val responseBody = response.body!!.string().trim()
        val contentType = response.body!!.contentType()!!

        if (contentType.type != jsonMediaType.type || contentType.subtype != jsonMediaType.subtype) {
            logger.warn {
                message("Error response from Docker daemon was not in JSON format.")
                data("statusCode", response.code)
                data("message", responseBody)
            }

            errorHandler(DockerAPIError(response.code, responseBody))
            return
        }

        val parsedError = Json.default.parseJson(responseBody).jsonObject
        val message = parsedError.getValue("message").primitive.content
            .correctLineEndings()

        errorHandler(DockerAPIError(response.code, message))
    }

    protected fun String.correctLineEndings(): String = this.replace("\n", systemInfo.lineSeparator)
}
