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

package batect.docker

import batect.os.unixsockets.UnixSocketDns
import batect.os.unixsockets.UnixSocketFactory
import batect.testutils.equalTo
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isA
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration
import java.util.concurrent.TimeUnit

object DockerHttpConfigSpec : Spek({
    describe("a set of Docker HTTP configuration") {
        val baseClient = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("some-proxy", 1234)))
            .readTimeout(1, TimeUnit.DAYS)
            .build()

        val config = DockerHttpConfig(baseClient)

        on("getting a HTTP client configured for use with the Docker API") {
            val client = config.client

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
            val encodedPath = UnixSocketDns.encodePath("/var/run/docker.sock")

            it("returns a URL ready for use with the local Unix socket") {
                assertThat(config.baseUrl, equalTo(HttpUrl.parse("http://$encodedPath")!!))
            }
        }
    }
})
