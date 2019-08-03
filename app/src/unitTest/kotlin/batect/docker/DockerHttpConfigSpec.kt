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
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

object DockerHttpConfigSpec : Spek({
    describe("a set of Docker HTTP configuration") {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("some-proxy", 1234))

        val baseClient by createForEachTest {
            OkHttpClient.Builder()
                .proxy(proxy)
                .readTimeout(1, TimeUnit.DAYS)
                .build()
        }

        given("a Unix socket address") {
            val dockerHost = "unix:///var/run/very/very/long/name/docker.sock"

            given("the application is running on an operating system that supports Unix sockets") {
                val systemInfo by createForEachTest { systemInfoFor(OperatingSystem.Linux) }
                val config by createForEachTest { DockerHttpConfig(baseClient, dockerHost, systemInfo) }

                on("getting a HTTP client configured for use with the Docker API") {
                    val client by runForEachTest { config.client }

                    it("overrides any existing proxy settings") {
                        assertThat(client.proxy(), equalTo(Proxy.NO_PROXY))
                    }

                    it("configures the client to use the Unix socket factory") {
                        assertThat(client.socketFactory(), isA<UnixSocketFactory>())
                    }

                    it("configures the client to use the fake DNS service") {
                        assertThat(client.dns(), isA<UnixSocketDns>())
                    }

                    it("inherits all other settings from the base client provided") {
                        assertThat(client.readTimeoutMillis().toLong(), equalTo(Duration.ofDays(1).toMillis()))
                    }
                }

                on("getting the base URL to use") {
                    val encodedPath = UnixSocketDns.encodePath("/var/run/very/very/long/name/docker.sock")

                    it("returns a URL ready for use with the local Unix socket") {
                        assertThat(config.baseUrl, equalTo(HttpUrl.parse("http://$encodedPath")!!))
                    }
                }
            }

            given("the application is running on an operating system that does not support Unix sockets") {
                val systemInfo by createForEachTest { systemInfoFor(OperatingSystem.Windows) }

                it("throws an appropriate exception") {
                    assertThat(
                        { DockerHttpConfig(baseClient, dockerHost, systemInfo) },
                        throws<InvalidDockerConfigurationException>(withMessage("This operating system does not support Unix sockets and so therefore the Docker host 'unix:///var/run/very/very/long/name/docker.sock' cannot be used."))
                    )
                }
            }
        }

        given("a named pipe address") {
            val dockerHost = "npipe:////./pipe/some/very/very/very/long/name/docker_engine"

            given("the application is running on Windows") {
                val systemInfo by createForEachTest { systemInfoFor(OperatingSystem.Windows) }
                val config by createForEachTest { DockerHttpConfig(baseClient, dockerHost, systemInfo) }

                on("getting a HTTP client configured for use with the Docker API") {
                    val client by runForEachTest { config.client }

                    it("overrides any existing proxy settings") {
                        assertThat(client.proxy(), equalTo(Proxy.NO_PROXY))
                    }

                    it("configures the client to use the named pipe socket factory") {
                        assertThat(client.socketFactory(), isA<NamedPipeSocketFactory>())
                    }

                    it("configures the client to use the fake DNS service") {
                        assertThat(client.dns(), isA<NamedPipeDns>())
                    }

                    it("inherits all other settings from the base client provided") {
                        assertThat(client.readTimeoutMillis().toLong(), equalTo(Duration.ofDays(1).toMillis()))
                    }
                }

                on("getting the base URL to use") {
                    val encodedPath = NamedPipeDns.encodePath("""\\.\pipe\some\very\very\very\long\name\docker_engine""")

                    it("returns a URL ready for use with the local Unix socket") {
                        assertThat(config.baseUrl, equalTo(HttpUrl.parse("http://$encodedPath")!!))
                    }
                }
            }

            given("the application is not running on Windows") {
                val systemInfo by createForEachTest { systemInfoFor(OperatingSystem.Linux) }

                it("throws an appropriate exception") {
                    assertThat(
                        { DockerHttpConfig(baseClient, dockerHost, systemInfo) },
                        throws<InvalidDockerConfigurationException>(withMessage("Named pipes are only supported on Windows and so therefore the Docker host 'npipe:////./pipe/some/very/very/very/long/name/docker_engine' cannot be used."))
                    )
                }
            }
        }

        // See https://docs.docker.com/engine/reference/commandline/dockerd/#daemon-socket-option for reference on the formats
        // that Docker supports.
        mapOf(
            "tcp://1.2.3.4" to HttpUrl.get("http://1.2.3.4"),
            "tcp://1.2.3.4:1234" to HttpUrl.get("http://1.2.3.4:1234"),
            "tcp://1.2.3.4/somewhere" to HttpUrl.get("http://1.2.3.4/somewhere"),
            "tcp://1.2.3.4:1234/somewhere" to HttpUrl.get("http://1.2.3.4:1234/somewhere"),
            "http://1.2.3.4" to HttpUrl.get("http://1.2.3.4"),
            "http://1.2.3.4:1234" to HttpUrl.get("http://1.2.3.4:1234"),
            "http://1.2.3.4/somewhere" to HttpUrl.get("http://1.2.3.4/somewhere"),
            "http://1.2.3.4:1234/somewhere" to HttpUrl.get("http://1.2.3.4:1234/somewhere"),
            "1.2.3.4" to HttpUrl.get("http://1.2.3.4"),
            "1.2.3.4:1234" to HttpUrl.get("http://1.2.3.4:1234"),
            ":1234" to HttpUrl.get("http://0.0.0.0:1234")
        ).forEach { (host, expectedBaseUrl) ->
            given("the host address '$host'") {
                val systemInfo by createForEachTest { systemInfoFor(OperatingSystem.Other) }
                val config by createForEachTest { DockerHttpConfig(baseClient, host, systemInfo) }

                on("getting a HTTP client configured for use with the Docker API") {
                    val client by runForEachTest { config.client }

                    it("does not override any existing proxy settings") {
                        assertThat(client.proxy(), equalTo(proxy))
                    }

                    it("configures the client to use the default socket factory") {
                        assertThat(client.socketFactory(), equalTo(SocketFactory.getDefault()))
                    }

                    it("configures the client to use the system DNS service") {
                        assertThat(client.dns(), equalTo(Dns.SYSTEM))
                    }

                    it("inherits all other settings from the base client provided") {
                        assertThat(client.readTimeoutMillis().toLong(), equalTo(Duration.ofDays(1).toMillis()))
                    }
                }

                on("getting the base URL to use") {
                    it("returns the expected base URL") {
                        assertThat(config.baseUrl, equalTo(expectedBaseUrl))
                    }
                }
            }
        }

        given("a host address with an invalid scheme") {
            val systemInfo by createForEachTest { systemInfoFor(OperatingSystem.Other) }

            it("throws an appropriate exception") {
                assertThat(
                    { DockerHttpConfig(baseClient, "somethingelse://1.2.3.4", systemInfo) },
                    throws<InvalidDockerConfigurationException>(withMessage("The scheme 'somethingelse' in 'somethingelse://1.2.3.4' is not a valid Docker host scheme."))
                )
            }
        }
    }
})

private fun systemInfoFor(os: OperatingSystem): SystemInfo {
    return mock {
        on { operatingSystem } doReturn os
    }
}
