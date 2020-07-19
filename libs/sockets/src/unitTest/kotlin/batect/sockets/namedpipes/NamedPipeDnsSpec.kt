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

package batect.sockets.namedpipes

import batect.testutils.equalTo
import batect.testutils.on
import batect.testutils.withMessage
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.throws
import java.net.InetAddress
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object NamedPipeDnsSpec : Spek({
    describe("a named pipes DNS provider") {
        val dns = NamedPipeDns()

        describe("looking up a host name") {
            on("when that host name is the output from encodePath()") {
                val hostName = NamedPipeDns.encodePath("""\\.\pipe\docker_engine""")

                it("returns a dummy resolved address") {
                    assertThat(dns.lookup(hostName), equalTo(listOf(
                        InetAddress.getByAddress(hostName, byteArrayOf(0, 0, 0, 0))
                    )))
                }
            }

            on("when that host name has not been encoded with encodePath()") {
                it("throws an appropriate exception") {
                    assertThat({ dns.lookup("""\\.\pipe\docker_engine""") }, throws<IllegalArgumentException>(withMessage("""Host name '\\.\pipe\docker_engine' was not encoded for use with NamedPipeDns.""")))
                }
            }
        }

        describe("encoding and decoding paths") {
            on("encoding a path") {
                val encoded = NamedPipeDns.encodePath("""\\.\pipe\docker_engine""")

                it("returns a value that does not contain any slashes") {
                    assertThat(encoded, !containsSubstring("/") and !containsSubstring("\\"))
                }

                it("returns a value that can be recovered by passing it to decodePath()") {
                    assertThat(NamedPipeDns.decodePath(encoded), equalTo("""\\.\pipe\docker_engine"""))
                }
            }

            on("decoding a path that was not encoded with encodePath()") {
                it("throws an appropriate exception") {
                    assertThat({ NamedPipeDns.decodePath("www.example.com") }, throws<IllegalArgumentException>(withMessage("Host name 'www.example.com' was not encoded for use with NamedPipeDns.")))
                }
            }
        }
    }
})
