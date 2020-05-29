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

package batect.journeytests

import batect.journeytests.testutils.ApplicationRunner
import batect.testutils.createForGroup
import batect.testutils.on
import batect.testutils.runBeforeGroup
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ProxyVariablesJourneyTest : Spek({
    describe("a task that uses proxy environment variables") {
        val runner by createForGroup { ApplicationRunner("proxy-variables") }

        on("running that task") {
            val httpProxy = "some-http-proxy"
            val httpsProxy = "some-https-proxy"
            val ftpProxy = "some-ftp-proxy"
            val noProxy = "bypass-proxy"

            val result by runBeforeGroup {
                runner.runApplication(
                    listOf("the-task"), mapOf(
                        "http_proxy" to httpProxy,
                        "https_proxy" to httpsProxy,
                        "ftp_proxy" to ftpProxy,
                        "no_proxy" to noProxy
                    )
                )
            }

            it("prints the output from that task, which shows that the proxy environment variables were set at both build and run time") {
                assertThat(result.output, containsSubstring("""
                    At build time, environment variables were:
                    http_proxy: $httpProxy
                    https_proxy: $httpsProxy
                    ftp_proxy: $ftpProxy
                    no_proxy: $noProxy

                    At runtime, environment variables are:
                    http_proxy: $httpProxy
                    https_proxy: $httpsProxy
                    ftp_proxy: $ftpProxy
                    no_proxy: $noProxy,build-env
                """.trimIndent()))
            }

            it("returns the exit code from that task") {
                assertThat(result.exitCode, equalTo(0))
            }
        }
    }
})
