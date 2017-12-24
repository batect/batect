/*
   Copyright 2017 Charles Korn.

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

package batect.os

import batect.testutils.isEmptyMap
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ProxyEnvironmentVariablesProviderSpec : Spek({
    describe("a proxy environment variables provider") {
        given("there are no environment variables defined") {
            val environmentVariables = emptyMap<String, String>()
            val provider = ProxyEnvironmentVariablesProvider(environmentVariables)

            on("getting proxy environment variables") {
                val proxyEnvironmentVariables = provider.proxyEnvironmentVariables

                it("returns an empty list") {
                    assertThat(proxyEnvironmentVariables, isEmptyMap())
                }
            }
        }

        given("there are no proxy-related environment variables defined") {
            val environmentVariables = mapOf(
                "PATH" to "/usr/bin:/blah",
                "HOME" to "/home/someuser"
            )

            val provider = ProxyEnvironmentVariablesProvider(environmentVariables)

            on("getting proxy environment variables") {
                val proxyEnvironmentVariables = provider.proxyEnvironmentVariables

                it("returns an empty list") {
                    assertThat(proxyEnvironmentVariables, isEmptyMap())
                }
            }
        }

        listOf("http_proxy", "https_proxy", "ftp_proxy", "no_proxy").forEach { lowercaseName ->
            val uppercaseName = lowercaseName.toUpperCase()

            given("the proxy-related environment variable '$lowercaseName' is defined") {
                given("and its name is given in lowercase") {
                    val environmentVariables = mapOf(
                        "PATH" to "/usr/bin:/blah",
                        "HOME" to "/home/someuser",
                        lowercaseName to "http://proxy:1234"
                    )

                    val provider = ProxyEnvironmentVariablesProvider(environmentVariables)

                    on("getting proxy environment variables") {
                        val proxyEnvironmentVariables = provider.proxyEnvironmentVariables

                        it("returns that environment variable") {
                            assertThat(proxyEnvironmentVariables, equalTo(mapOf(
                                lowercaseName to "http://proxy:1234"
                            )))
                        }
                    }
                }

                given("and its name is given in uppercase") {
                    val environmentVariables = mapOf(
                        "PATH" to "/usr/bin:/blah",
                        "HOME" to "/home/someuser",
                        uppercaseName to "http://proxy:1234"
                    )

                    val provider = ProxyEnvironmentVariablesProvider(environmentVariables)

                    on("getting proxy environment variables") {
                        val proxyEnvironmentVariables = provider.proxyEnvironmentVariables

                        it("returns that environment variable") {
                            assertThat(proxyEnvironmentVariables, equalTo(mapOf(
                                uppercaseName to "http://proxy:1234"
                            )))
                        }
                    }
                }

                given("and its name is given in both lowercase and uppercase") {
                    val environmentVariables = mapOf(
                        "PATH" to "/usr/bin:/blah",
                        "HOME" to "/home/someuser",
                        lowercaseName to "http://lowercase_proxy:1234",
                        uppercaseName to "http://uppercase_proxy:1234"
                    )

                    val provider = ProxyEnvironmentVariablesProvider(environmentVariables)

                    on("getting proxy environment variables") {
                        val proxyEnvironmentVariables = provider.proxyEnvironmentVariables

                        it("returns both forms of the variable") {
                            assertThat(proxyEnvironmentVariables, equalTo(mapOf(
                                lowercaseName to "http://lowercase_proxy:1234",
                                uppercaseName to "http://uppercase_proxy:1234"
                            )))
                        }
                    }
                }
            }
        }

    }
})
