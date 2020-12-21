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

import batect.docker.DockerException
import batect.docker.DockerHttpConfig
import batect.docker.build.BuildKitSession
import batect.docker.run.ConnectionHijacker
import batect.logging.LogMessageBuilder
import batect.logging.Logger
import batect.os.SystemInfo
import okhttp3.Request
import java.net.Socket

class SessionsAPI(
    httpConfig: DockerHttpConfig,
    systemInfo: SystemInfo,
    logger: Logger,
    private val hijackerFactory: () -> ConnectionHijacker = ::ConnectionHijacker
) : APIBase(httpConfig, systemInfo, logger) {
    fun start(session: BuildKitSession): SessionConnection {
        logger.info {
            message("Starting session.")
            data("session", session)
        }

        val url = baseUrl.newBuilder().addPathSegment("session").build()

        val request = Request.Builder()
            .post(emptyRequestBody())
            .url(url)
            .header("Connection", "Upgrade")
            .header("Upgrade", "h2c")
            .header("X-Docker-Expose-Session-Uuid", session.sessionId)
            .header("X-Docker-Expose-Session-Name", session.name)
            .header("X-Docker-Expose-Session-Sharedkey", session.sharedKey)
            .build()

        val hijacker = hijackerFactory()

        val client = httpConfig.client.newBuilder()
            .withNoReadTimeout()
            .connectionPoolWithNoEviction()
            .addNetworkInterceptor(hijacker)
            .build()

        val response = client.newCall(request).execute()

        checkForFailure(response) { error ->
            logger.error {
                message("Starting session failed.")
                data("error", error)
            }

            throw DockerException("Starting session failed: ${error.message}")
        }

        return SessionConnection(hijacker.socket!!)
    }

    private fun LogMessageBuilder.data(key: String, value: BuildKitSession) = data(key, value, BuildKitSession.serializer())
}

data class SessionConnection(val socket: Socket) : AutoCloseable {
    override fun close() {
        socket.close()
    }
}
