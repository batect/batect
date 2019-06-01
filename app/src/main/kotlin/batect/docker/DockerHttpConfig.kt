/*
   Copyright 2017-2019 Charles Korn.

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

package batect.docker

import batect.os.unix.unixsockets.UnixSocketDns
import batect.os.unix.unixsockets.UnixSocketFactory
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.net.Proxy

class DockerHttpConfig(
    private val baseClient: OkHttpClient,
    private val dockerHost: String
) {
    private val isUnixSocket = dockerHost.startsWith("unix://")
    val client: OkHttpClient = buildClient()
    val baseUrl: HttpUrl = buildBaseUrl()

    private fun buildClient(): OkHttpClient {
        if (isUnixSocket) {
            return buildUnixSocketClient()
        }

        return baseClient
    }

    private fun buildUnixSocketClient(): OkHttpClient {
        return baseClient.newBuilder()
            .proxy(Proxy.NO_PROXY)
            .socketFactory(UnixSocketFactory())
            .dns(UnixSocketDns())
            .build()
    }

    private fun buildBaseUrl(): HttpUrl {
        if (isUnixSocket) {
            return buildUnixSocketBaseUrl()
        }

        return HttpUrl.get(cleanUrl())
    }

    private fun buildUnixSocketBaseUrl(): HttpUrl {
        val socketPath = dockerHost.replace("^unix://".toRegex(), "")

        return HttpUrl.Builder()
            .scheme("http")
            .host(UnixSocketDns.encodePath(socketPath))
            .build()
    }

    private fun cleanUrl(): String {
        if (dockerHost.contains("://")) {
            val scheme = dockerHost.substringBefore("://")

            if (scheme !in setOf("tcp", "http")) {
                throw InvalidDockerConfigurationException("The scheme '$scheme' in '$dockerHost' is not a valid Docker host scheme.")
            }

            return dockerHost.replace("^tcp://".toRegex(), "http://")
        }

        if (dockerHost.matches(""":\d+""".toRegex())) {
            return "http://0.0.0.0$dockerHost"
        }

        return "http://$dockerHost"
    }

    companion object {
        const val defaultDockerHost = "unix:///var/run/docker.sock"
    }
}
