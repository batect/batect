/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.docker

import batect.cli.CommandLineOptions
import batect.config.Container
import batect.config.Expression
import batect.config.ExpressionEvaluationContext
import batect.config.ExpressionEvaluationException
import batect.config.LiteralValue
import batect.execution.ContainerDependencyGraph
import batect.proxies.ProxyEnvironmentVariablesProvider
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.isEmptyMap
import batect.testutils.on
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DockerContainerEnvironmentVariableProviderSpec : Spek({
    describe("a Docker container environment variable provider") {
        val graph by createForEachTest {
            mock<ContainerDependencyGraph> {
                on { allContainers } doReturn setOf(
                    Container("container-1", imageSourceDoesNotMatter()),
                    Container("container-2", imageSourceDoesNotMatter()),
                )
            }
        }

        val commandLineOptions by createForEachTest { mock<CommandLineOptions>() }

        given("the console's type is not provided") {
            val terminalType = null as String?

            given("propagating proxy environment variables is disabled") {
                beforeEachTest { whenever(commandLineOptions.dontPropagateProxyEnvironmentVariables).doReturn(true) }

                val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider> {
                    on { getProxyEnvironmentVariables(setOf("container-1", "container-2")) } doReturn mapOf("SOME_PROXY_VAR" to "this should not be used")
                }

                val provider by createForEachTest { DockerContainerEnvironmentVariableProvider(proxyEnvironmentVariablesProvider, mock(), graph, commandLineOptions) }

                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    environment = mapOf("SOME_VAR" to LiteralValue("SOME_VALUE")),
                )

                val environmentVariables by createForEachTest { provider.environmentVariablesFor(container, terminalType) }

                on("getting environment variables for the container") {
                    it("returns the environment variables from the container") {
                        assertThat(environmentVariables, equalTo(mapOf("SOME_VAR" to "SOME_VALUE")))
                    }
                }
            }
        }

        given("the console's type is provided") {
            val terminalType = "some-term"
            val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider>()
            val provider by createForEachTest { DockerContainerEnvironmentVariableProvider(proxyEnvironmentVariablesProvider, mock(), graph, commandLineOptions) }

            beforeEachTest { whenever(commandLineOptions.dontPropagateProxyEnvironmentVariables).doReturn(true) }

            given("a container with no override for the TERM environment variable") {
                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    environment = mapOf("SOME_VAR" to LiteralValue("SOME_VALUE")),
                )

                on("getting environment variables for the container") {
                    val environmentVariables by createForEachTest { provider.environmentVariablesFor(container, terminalType) }

                    it("returns the environment variables from the container and the TERM environment variable from the host") {
                        assertThat(
                            environmentVariables,
                            equalTo(
                                mapOf(
                                    "SOME_VAR" to "SOME_VALUE",
                                    "TERM" to "some-term",
                                ),
                            ),
                        )
                    }
                }
            }

            given("a container with an override for the TERM environment variable") {
                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    environment = mapOf(
                        "SOME_VAR" to LiteralValue("SOME_VALUE"),
                        "TERM" to LiteralValue("some-other-term"),
                    ),
                )

                on("getting environment variables for the container") {
                    val environmentVariables by createForEachTest { provider.environmentVariablesFor(container, terminalType) }

                    it("returns the environment variables from the container and the TERM environment variable from the container") {
                        assertThat(
                            environmentVariables,
                            equalTo(
                                mapOf(
                                    "SOME_VAR" to "SOME_VALUE",
                                    "TERM" to "some-other-term",
                                ),
                            ),
                        )
                    }
                }
            }
        }

        given("there are references to config variables or host environment variables") {
            val terminalType = null as String?
            val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider>()
            val expressionEvaluationContext = mock<ExpressionEvaluationContext>()
            val provider by createForEachTest { DockerContainerEnvironmentVariableProvider(proxyEnvironmentVariablesProvider, expressionEvaluationContext, graph, commandLineOptions) }

            beforeEachTest { whenever(commandLineOptions.dontPropagateProxyEnvironmentVariables).doReturn(true) }

            val invalidReference = mock<Expression> {
                on { evaluate(expressionEvaluationContext) } doThrow ExpressionEvaluationException("Couldn't evaluate expression.")
            }

            given("and the reference is valid") {
                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    environment = mapOf("SOME_VAR" to LiteralValue("SOME_VALID_VALUE")),
                )

                on("getting environment variables for the container") {
                    val environmentVariables by createForEachTest { provider.environmentVariablesFor(container, terminalType) }

                    it("returns the value of the reference") {
                        assertThat(
                            environmentVariables,
                            equalTo(
                                mapOf(
                                    "SOME_VAR" to "SOME_VALID_VALUE",
                                ),
                            ),
                        )
                    }
                }
            }

            given("and the reference is not valid") {
                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    environment = mapOf("SOME_VAR" to invalidReference),
                )

                on("getting environment variables for the container") {
                    it("throws an appropriate exception") {
                        assertThat(
                            { provider.environmentVariablesFor(container, terminalType) },
                            throws<ExpressionEvaluationException>(withMessage("The value for the environment variable 'SOME_VAR' cannot be evaluated: Couldn't evaluate expression.")),
                        )
                    }
                }
            }
        }

        given("there are proxy environment variables present on the host") {
            val terminalType = null as String?
            val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider> {
                on { getProxyEnvironmentVariables(setOf("container-1", "container-2")) } doReturn mapOf(
                    "HTTP_PROXY" to "http://some-proxy",
                    "NO_PROXY" to "dont-proxy-this",
                )
            }

            val provider by createForEachTest { DockerContainerEnvironmentVariableProvider(proxyEnvironmentVariablesProvider, mock(), graph, commandLineOptions) }

            given("propagating proxy environment variables is enabled") {
                beforeEachTest { whenever(commandLineOptions.dontPropagateProxyEnvironmentVariables).doReturn(false) }

                given("the container environment variables do not override the proxy settings") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter(),
                    )

                    on("getting environment variables for the container") {
                        val environmentVariables by createForEachTest { provider.environmentVariablesFor(container, terminalType) }

                        it("returns the proxy environment variables from the host") {
                            assertThat(
                                environmentVariables,
                                equalTo(
                                    mapOf(
                                        "HTTP_PROXY" to "http://some-proxy",
                                        "NO_PROXY" to "dont-proxy-this",
                                    ),
                                ),
                            )
                        }
                    }
                }

                given("the container environment variables override the proxy settings") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter(),
                        environment = mapOf(
                            "HTTP_PROXY" to LiteralValue("http://some-other-proxy"),
                        ),
                    )

                    on("getting environment variables for the container") {
                        val environmentVariables by createForEachTest { provider.environmentVariablesFor(container, terminalType) }

                        it("returns the proxy environment variables from the host, with overrides from the container") {
                            assertThat(
                                environmentVariables,
                                equalTo(
                                    mapOf(
                                        "HTTP_PROXY" to "http://some-other-proxy",
                                        "NO_PROXY" to "dont-proxy-this",
                                    ),
                                ),
                            )
                        }
                    }
                }
            }

            given("propagating proxy environment variables is disabled") {
                beforeEachTest { whenever(commandLineOptions.dontPropagateProxyEnvironmentVariables).doReturn(true) }

                on("getting environment variables for the container") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter(),
                    )

                    val environmentVariables by createForEachTest { provider.environmentVariablesFor(container, terminalType) }

                    it("does not propagate the proxy environment variables") {
                        assertThat(environmentVariables, isEmptyMap())
                    }
                }
            }
        }
    }
})
