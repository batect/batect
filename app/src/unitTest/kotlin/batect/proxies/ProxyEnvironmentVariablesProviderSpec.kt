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

package batect.proxies

import batect.os.HostEnvironmentVariables
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.isEmptyMap
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ProxyEnvironmentVariablesProviderSpec : Spek({
    describe("a proxy environment variables provider") {
        val preprocessor = mock<ProxyEnvironmentVariablePreprocessor> {
            on { process(any()) } doAnswer {
                "${it.arguments[0]}_processed"
            }
        }

        given("there are no environment variables defined") {
            val environmentVariables = HostEnvironmentVariables()
            val provider = ProxyEnvironmentVariablesProvider(preprocessor, environmentVariables)

            mapOf(
                "there are no additional 'don't proxy' entries" to emptySet(),
                "there are some additional 'don't proxy' entries" to setOf("host-1", "host-2")
            ).forEach { (description, extraNoProxyEntries) ->
                given(description) {
                    on("getting proxy environment variables") {
                        val proxyEnvironmentVariables = provider.getProxyEnvironmentVariables(extraNoProxyEntries)

                        it("returns an empty list") {
                            assertThat(proxyEnvironmentVariables, isEmptyMap())
                        }
                    }
                }
            }
        }

        given("there are no proxy-related environment variables defined") {
            val environmentVariables = HostEnvironmentVariables(
                "PATH" to "/usr/bin:/blah",
                "HOME" to "/home/someuser"
            )

            val provider = ProxyEnvironmentVariablesProvider(preprocessor, environmentVariables)

            mapOf(
                "there are no additional 'don't proxy' entries" to emptySet(),
                "there are some additional 'don't proxy' entries" to setOf("host-1", "host-2")
            ).forEach { (description, extraNoProxyEntries) ->
                given(description) {
                    on("getting proxy environment variables") {
                        val proxyEnvironmentVariables = provider.getProxyEnvironmentVariables(extraNoProxyEntries)

                        it("returns an empty list") {
                            assertThat(proxyEnvironmentVariables, isEmptyMap())
                        }
                    }
                }
            }
        }

        listOf("http_proxy", "https_proxy", "ftp_proxy").forEach { lowercaseName ->
            val uppercaseName = lowercaseName.uppercase()

            given("the proxy-related environment variable '$lowercaseName' is defined") {
                given("there are no additional 'don't proxy' entries") {
                    val extraNoProxyEntries = emptySet<String>()

                    given("and the variable name is given in lowercase") {
                        val environmentVariables = HostEnvironmentVariables(
                            "PATH" to "/usr/bin:/blah",
                            "HOME" to "/home/someuser",
                            lowercaseName to "http://proxy:1234"
                        )

                        val provider = ProxyEnvironmentVariablesProvider(preprocessor, environmentVariables)

                        on("getting proxy environment variables") {
                            val proxyEnvironmentVariables = provider.getProxyEnvironmentVariables(extraNoProxyEntries)

                            it("returns that environment variable and its uppercase equivalent, passing it through the preprocessor") {
                                assertThat(
                                    proxyEnvironmentVariables,
                                    equalTo(
                                        mapOf(
                                            lowercaseName to "http://proxy:1234_processed",
                                            uppercaseName to "http://proxy:1234_processed"
                                        )
                                    )
                                )
                            }
                        }
                    }

                    given("and the variable name is given in uppercase") {
                        val environmentVariables = HostEnvironmentVariables(
                            "PATH" to "/usr/bin:/blah",
                            "HOME" to "/home/someuser",
                            uppercaseName to "http://proxy:1234"
                        )

                        val provider = ProxyEnvironmentVariablesProvider(preprocessor, environmentVariables)

                        on("getting proxy environment variables") {
                            val proxyEnvironmentVariables = provider.getProxyEnvironmentVariables(extraNoProxyEntries)

                            it("returns that environment variable and its lowercase equivalent, passing it through the preprocessor") {
                                assertThat(
                                    proxyEnvironmentVariables,
                                    equalTo(
                                        mapOf(
                                            lowercaseName to "http://proxy:1234_processed",
                                            uppercaseName to "http://proxy:1234_processed"
                                        )
                                    )
                                )
                            }
                        }
                    }

                    given("and the variable name is given in both lowercase and uppercase") {
                        val environmentVariables = HostEnvironmentVariables(
                            "PATH" to "/usr/bin:/blah",
                            "HOME" to "/home/someuser",
                            lowercaseName to "http://lowercase-proxy:1234",
                            uppercaseName to "http://uppercase_proxy:1234"
                        )

                        val provider = ProxyEnvironmentVariablesProvider(preprocessor, environmentVariables)

                        on("getting proxy environment variables") {
                            val proxyEnvironmentVariables = provider.getProxyEnvironmentVariables(extraNoProxyEntries)

                            it("returns both forms of the variable, passing both through the preprocessor") {
                                assertThat(
                                    proxyEnvironmentVariables,
                                    equalTo(
                                        mapOf(
                                            lowercaseName to "http://lowercase-proxy:1234_processed",
                                            uppercaseName to "http://uppercase_proxy:1234_processed"
                                        )
                                    )
                                )
                            }
                        }
                    }
                }

                given("there are some additional 'don't proxy' entries") {
                    val extraNoProxyEntries = setOf("host-1", "host-2")

                    given("and the variable name is given in lowercase") {
                        val environmentVariables = HostEnvironmentVariables(
                            "PATH" to "/usr/bin:/blah",
                            "HOME" to "/home/someuser",
                            lowercaseName to "http://proxy:1234"
                        )

                        val provider = ProxyEnvironmentVariablesProvider(preprocessor, environmentVariables)

                        on("getting proxy environment variables") {
                            val proxyEnvironmentVariables = provider.getProxyEnvironmentVariables(extraNoProxyEntries)

                            it("returns that environment variable, with the no_proxy variables set") {
                                assertThat(
                                    proxyEnvironmentVariables,
                                    equalTo(
                                        mapOf(
                                            lowercaseName to "http://proxy:1234_processed",
                                            uppercaseName to "http://proxy:1234_processed",
                                            "no_proxy" to "host-1,host-2",
                                            "NO_PROXY" to "host-1,host-2"
                                        )
                                    )
                                )
                            }
                        }
                    }

                    given("and the variable name is given in uppercase") {
                        val environmentVariables = HostEnvironmentVariables(
                            "PATH" to "/usr/bin:/blah",
                            "HOME" to "/home/someuser",
                            uppercaseName to "http://proxy:1234"
                        )

                        val provider = ProxyEnvironmentVariablesProvider(preprocessor, environmentVariables)

                        on("getting proxy environment variables") {
                            val proxyEnvironmentVariables = provider.getProxyEnvironmentVariables(extraNoProxyEntries)

                            it("returns that environment variable, with the no_proxy variables set") {
                                assertThat(
                                    proxyEnvironmentVariables,
                                    equalTo(
                                        mapOf(
                                            lowercaseName to "http://proxy:1234_processed",
                                            uppercaseName to "http://proxy:1234_processed",
                                            "no_proxy" to "host-1,host-2",
                                            "NO_PROXY" to "host-1,host-2"
                                        )
                                    )
                                )
                            }
                        }
                    }

                    given("and the variable name is given in both lowercase and uppercase") {
                        val environmentVariables = HostEnvironmentVariables(
                            "PATH" to "/usr/bin:/blah",
                            "HOME" to "/home/someuser",
                            lowercaseName to "http://lowercase-proxy:1234",
                            uppercaseName to "http://uppercase-proxy:1234"
                        )

                        val provider = ProxyEnvironmentVariablesProvider(preprocessor, environmentVariables)

                        on("getting proxy environment variables") {
                            val proxyEnvironmentVariables = provider.getProxyEnvironmentVariables(extraNoProxyEntries)

                            it("returns both forms of the variable, with the no_proxy variables set") {
                                assertThat(
                                    proxyEnvironmentVariables,
                                    equalTo(
                                        mapOf(
                                            lowercaseName to "http://lowercase-proxy:1234_processed",
                                            uppercaseName to "http://uppercase-proxy:1234_processed",
                                            "no_proxy" to "host-1,host-2",
                                            "NO_PROXY" to "host-1,host-2"
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        given("the proxy-related environment variable 'no_proxy' is defined") {
            given("there are no additional 'don't proxy' entries") {
                val extraNoProxyEntries = emptySet<String>()

                given("and the variable name is given in lowercase") {
                    val environmentVariables = HostEnvironmentVariables(
                        "PATH" to "/usr/bin:/blah",
                        "HOME" to "/home/someuser",
                        "no_proxy" to "host-1"
                    )

                    val provider = ProxyEnvironmentVariablesProvider(preprocessor, environmentVariables)

                    on("getting proxy environment variables") {
                        val proxyEnvironmentVariables = provider.getProxyEnvironmentVariables(extraNoProxyEntries)

                        it("returns that environment variable and its uppercase equivalent") {
                            assertThat(
                                proxyEnvironmentVariables,
                                equalTo(
                                    mapOf(
                                        "no_proxy" to "host-1",
                                        "NO_PROXY" to "host-1"
                                    )
                                )
                            )
                        }
                    }
                }

                given("and the variable name is given in uppercase") {
                    val environmentVariables = HostEnvironmentVariables(
                        "PATH" to "/usr/bin:/blah",
                        "HOME" to "/home/someuser",
                        "NO_PROXY" to "host-1"
                    )

                    val provider = ProxyEnvironmentVariablesProvider(preprocessor, environmentVariables)

                    on("getting proxy environment variables") {
                        val proxyEnvironmentVariables = provider.getProxyEnvironmentVariables(extraNoProxyEntries)

                        it("returns that environment variable and its lowercase equivalent") {
                            assertThat(
                                proxyEnvironmentVariables,
                                equalTo(
                                    mapOf(
                                        "NO_PROXY" to "host-1",
                                        "no_proxy" to "host-1"
                                    )
                                )
                            )
                        }
                    }
                }

                given("and the variable name is given in both lowercase and uppercase") {
                    val environmentVariables = HostEnvironmentVariables(
                        "PATH" to "/usr/bin:/blah",
                        "HOME" to "/home/someuser",
                        "no_proxy" to "lowercase-host-1",
                        "NO_PROXY" to "uppercase-host-1"
                    )

                    val provider = ProxyEnvironmentVariablesProvider(preprocessor, environmentVariables)

                    on("getting proxy environment variables") {
                        val proxyEnvironmentVariables = provider.getProxyEnvironmentVariables(extraNoProxyEntries)

                        it("returns both forms of the variable") {
                            assertThat(
                                proxyEnvironmentVariables,
                                equalTo(
                                    mapOf(
                                        "no_proxy" to "lowercase-host-1",
                                        "NO_PROXY" to "uppercase-host-1"
                                    )
                                )
                            )
                        }
                    }
                }
            }

            given("there are some additional 'don't proxy' entries") {
                val extraNoProxyEntries = setOf("host-1", "host-2")

                given("and the variable name is given in lowercase") {
                    val environmentVariables = HostEnvironmentVariables(
                        "PATH" to "/usr/bin:/blah",
                        "HOME" to "/home/someuser",
                        "no_proxy" to "existing-host"
                    )

                    val provider = ProxyEnvironmentVariablesProvider(preprocessor, environmentVariables)

                    on("getting proxy environment variables") {
                        val proxyEnvironmentVariables = provider.getProxyEnvironmentVariables(extraNoProxyEntries)

                        it("returns that environment variable, with the no_proxy variables set") {
                            assertThat(
                                proxyEnvironmentVariables,
                                equalTo(
                                    mapOf(
                                        "no_proxy" to "existing-host,host-1,host-2",
                                        "NO_PROXY" to "existing-host,host-1,host-2"
                                    )
                                )
                            )
                        }
                    }
                }

                given("and the variable name is given in uppercase") {
                    val environmentVariables = HostEnvironmentVariables(
                        "PATH" to "/usr/bin:/blah",
                        "HOME" to "/home/someuser",
                        "NO_PROXY" to "existing-host"
                    )

                    val provider = ProxyEnvironmentVariablesProvider(preprocessor, environmentVariables)

                    on("getting proxy environment variables") {
                        val proxyEnvironmentVariables = provider.getProxyEnvironmentVariables(extraNoProxyEntries)

                        it("returns that environment variable, with the no_proxy variables set") {
                            assertThat(
                                proxyEnvironmentVariables,
                                equalTo(
                                    mapOf(
                                        "no_proxy" to "existing-host,host-1,host-2",
                                        "NO_PROXY" to "existing-host,host-1,host-2"
                                    )
                                )
                            )
                        }
                    }
                }

                given("and the variable name is given in both lowercase and uppercase") {
                    val environmentVariables = HostEnvironmentVariables(
                        "PATH" to "/usr/bin:/blah",
                        "HOME" to "/home/someuser",
                        "no_proxy" to "lowercase-host",
                        "NO_PROXY" to "uppercase-host"
                    )

                    val provider = ProxyEnvironmentVariablesProvider(preprocessor, environmentVariables)

                    on("getting proxy environment variables") {
                        val proxyEnvironmentVariables = provider.getProxyEnvironmentVariables(extraNoProxyEntries)

                        it("returns both forms of the variable, with the no_proxy variables set") {
                            assertThat(
                                proxyEnvironmentVariables,
                                equalTo(
                                    mapOf(
                                        "no_proxy" to "lowercase-host,host-1,host-2",
                                        "NO_PROXY" to "uppercase-host,host-1,host-2"
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }
    }
})
