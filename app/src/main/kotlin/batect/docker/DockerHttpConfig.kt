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

import batect.os.OperatingSystem
import batect.os.SystemInfo
import batect.os.unix.unixsockets.UnixSocketDns
import batect.os.unix.unixsockets.UnixSocketFactory
import batect.os.windows.namedpipes.NamedPipeDns
import batect.os.windows.namedpipes.NamedPipeSocketFactory
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.net.Proxy
import javax.net.SocketFactory

class DockerHttpConfig(
    private val baseClient: OkHttpClient,
    private val dockerHost: String,
    private val systemInfo: SystemInfo
) {
    private val isUnixSocket = dockerHost.startsWith("unix://")
    private val isNamedPipe = dockerHost.startsWith("npipe://")

    val client: OkHttpClient = buildClient()
    val baseUrl: HttpUrl = buildBaseUrl()

    private fun buildClient(): OkHttpClient = when {
        isUnixSocket -> buildUnixSocketClient()
        isNamedPipe -> buildNamedPipeClient()
        else -> baseClient
    }

    private fun buildUnixSocketClient(): OkHttpClient = buildNonTcpClient(UnixSocketFactory(), UnixSocketDns())
    private fun buildNamedPipeClient(): OkHttpClient = buildNonTcpClient(NamedPipeSocketFactory(), NamedPipeDns())

    private fun buildNonTcpClient(socketFactory: SocketFactory, dns: Dns): OkHttpClient = baseClient.newBuilder()
        .proxy(Proxy.NO_PROXY)
        .socketFactory(socketFactory)
        .dns(dns)
        .build()

    private fun buildBaseUrl(): HttpUrl = when {
        isUnixSocket -> buildUnixSocketBaseUrl()
        isNamedPipe -> buildNamedPipeBaseUrl()
        else -> HttpUrl.get(cleanUrl())
    }

    private fun buildUnixSocketBaseUrl(): HttpUrl {
        if (systemInfo.operatingSystem !in setOf(OperatingSystem.Linux, OperatingSystem.Mac)) {
            throw InvalidDockerConfigurationException("This operating system does not support Unix sockets and so therefore the Docker host '$dockerHost' cannot be used.")
        }

        val socketPath = dockerHost.replace("^unix://".toRegex(), "")

        return HttpUrl.Builder()
            .scheme("http")
            .host(UnixSocketDns.encodePath(socketPath))
            .build()
    }

    private fun buildNamedPipeBaseUrl(): HttpUrl {
        if (systemInfo.operatingSystem != OperatingSystem.Windows) {
            throw InvalidDockerConfigurationException("Named pipes are only supported on Windows and so therefore the Docker host '$dockerHost' cannot be used.")
        }

        val socketPath = dockerHost
            .replace("^npipe://".toRegex(), "")
            .replace('/', '\\')

        return HttpUrl.Builder()
            .scheme("http")
            .host(NamedPipeDns.encodePath(socketPath))
            .build()
    }

    private fun cleanUrl(): String {
        if (dockerHost.contains("://")) {
            val scheme = dockerHost.substringBefore("://")

            if (scheme != "tcp") {
                throw InvalidDockerConfigurationException("The scheme '$scheme' in '$dockerHost' is not a valid Docker host scheme.")
            }

            return dockerHost.replace("^tcp://".toRegex(), "http://")
        }

        if (dockerHost.matches(""":\d+""".toRegex())) {
            return "http://0.0.0.0$dockerHost"
        }

        return "http://$dockerHost"
    }
}
