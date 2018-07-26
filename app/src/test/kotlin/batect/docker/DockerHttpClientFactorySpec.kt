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
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.net.Proxy

object DockerHttpClientFactorySpec : Spek({
    describe("a Docker HTTP client factory") {
        val factory = DockerHttpClientFactory

        on("creating a new HTTP client") {
            val client = factory.create()

            it("disables the proxy") {
                assertThat(client.proxy(), equalTo(Proxy.NO_PROXY))
            }

            it("configures the client to use the Unix socket factory") {
                assertThat(client.socketFactory(), isA<UnixSocketFactory>())
            }

            it("configures the client to use the fake DNS service") {
                assertThat(client.dns(), isA<UnixSocketDns>())
            }
        }
    }
})
