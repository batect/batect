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

package batect.docker

import batect.os.OperatingSystem
import batect.os.SystemInfo
import batect.sockets.namedpipes.NamedPipeDns
import batect.sockets.namedpipes.NamedPipeSocketFactory
import batect.sockets.unix.UnixSocketDns
import batect.sockets.unix.UnixSocketFactory
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.net.Proxy
import javax.net.SocketFactory

class DockerHttpConfig(
    private val baseClient: OkHttpClient,
    private val dockerHost: String,
    private val tlsConfig: DockerTLSConfig,
    private val systemInfo: SystemInfo
) {
    private val isUnixSocket = dockerHost.startsWith("unix://")
    private val isNamedPipe = dockerHost.startsWith("npipe://")

    val client: OkHttpClient = buildClient()
    val baseUrl: HttpUrl = buildBaseUrl()

    private fun buildClient(): OkHttpClient = when {
        isUnixSocket -> buildUnixSocketClient()
        isNamedPipe -> buildNamedPipeClient()
        else -> buildTcpClient()
    }

    private fun buildUnixSocketClient(): OkHttpClient = buildNonTcpClient(UnixSocketFactory(), UnixSocketDns())
    private fun buildNamedPipeClient(): OkHttpClient = buildNonTcpClient(NamedPipeSocketFactory(), NamedPipeDns())

    private fun buildNonTcpClient(socketFactory: SocketFactory, dns: Dns): OkHttpClient = configureCommonClientSettings()
        .proxy(Proxy.NO_PROXY)
        .socketFactory(socketFactory)
        .dns(dns)
        .build()

    private fun buildTcpClient(): OkHttpClient = configureCommonClientSettings().build()

    private fun configureCommonClientSettings(): OkHttpClient.Builder = baseClient.newBuilder()
        .sslSocketFactory(tlsConfig.sslSocketFactory, tlsConfig.trustManager)
        .hostnameVerifier(tlsConfig.hostnameVerifier)

    private fun buildBaseUrl(): HttpUrl = when {
        isUnixSocket -> buildUnixSocketBaseUrl()
        isNamedPipe -> buildNamedPipeBaseUrl()
        else -> cleanUrl().toHttpUrl()
    }

    private fun buildUnixSocketBaseUrl(): HttpUrl {
        if (systemInfo.operatingSystem !in setOf(OperatingSystem.Linux, OperatingSystem.Mac)) {
            throw InvalidDockerConfigurationException("This operating system does not support Unix sockets and so therefore the Docker host '$dockerHost' cannot be used.")
        }

        val socketPath = dockerHost.replace("^unix://".toRegex(), "")

        return HttpUrl.Builder()
            .scheme(tlsConfig.scheme)
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
            .scheme(tlsConfig.scheme)
            .host(NamedPipeDns.encodePath(socketPath))
            .build()
    }

    private fun cleanUrl(): String {
        if (dockerHost.contains("://")) {
            val scheme = dockerHost.substringBefore("://")

            if (scheme != "tcp") {
                throw InvalidDockerConfigurationException("The scheme '$scheme' in '$dockerHost' is not a valid Docker host scheme.")
            }

            return dockerHost.replace("^tcp://".toRegex(), "${tlsConfig.scheme}://")
        }

        if (dockerHost.matches(""":\d+""".toRegex())) {
            return "${tlsConfig.scheme}://0.0.0.0$dockerHost"
        }

        return "${tlsConfig.scheme}://$dockerHost"
    }

    val connectionType: ConnectionType = when {
        isUnixSocket -> ConnectionType.UnixSocket
        isNamedPipe -> ConnectionType.NamedPipe
        else -> ConnectionType.TCP
    }
}

enum class ConnectionType {
    UnixSocket,
    NamedPipe,
    TCP
}
