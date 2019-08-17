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

import batect.config.Container
import batect.config.EnvironmentVariableExpression
import batect.config.HealthCheckConfig
import batect.config.LiteralValue
import batect.config.PortMapping
import batect.config.ReferenceValue
import batect.config.VolumeMount
import batect.os.Command
import batect.os.proxies.ProxyEnvironmentVariablesProvider
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.isEmptyMap
import batect.testutils.on
import batect.testutils.withMessage
import batect.ui.ConsoleInfo
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration

object DockerContainerCreationRequestFactorySpec : Spek({
    describe("a Docker container creation request factory") {
        val image = DockerImage("some-image")
        val network = DockerNetwork("some-network")
        val command = Command.parse("some-app some-arg")
        val entrypoint = Command.parse("sh")
        val workingDirectory = "some-specific-working-directory"
        val allContainersInNetwork = setOf(
            Container("container-1", imageSourceDoesNotMatter()),
            Container("container-2", imageSourceDoesNotMatter())
        )

        given("the console's type is not available") {
            val consoleInfo = mock<ConsoleInfo>()
            val hostEnvironmentVariables = emptyMap<String, String>()

            given("propagating proxy environment variables is disabled") {
                val propagateProxyEnvironmentVariables = false
                val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider> {
                    on { getProxyEnvironmentVariables(setOf("container-1", "container-2")) } doReturn mapOf("SOME_PROXY_VAR" to "this should not be used")
                }

                val factory = DockerContainerCreationRequestFactory(consoleInfo, proxyEnvironmentVariablesProvider, hostEnvironmentVariables)

                given("there are no additional environment variables") {
                    val additionalEnvironmentVariables = emptyMap<String, EnvironmentVariableExpression>()

                    given("there are no additional volume mounts") {
                        val additionalVolumeMounts = emptySet<VolumeMount>()

                        given("there are no additional port mappings") {
                            val additionalPortMappings = emptySet<PortMapping>()

                            on("creating the request") {
                                val container = Container(
                                    "some-container",
                                    imageSourceDoesNotMatter(),
                                    command = Command.parse("some-command-that-wont-be-used"),
                                    entrypoint = Command.parse("some-command-that-wont-be-used"),
                                    workingDirectory = "/some-work-dir",
                                    volumeMounts = setOf(VolumeMount("local", "remote", "mode")),
                                    portMappings = setOf(PortMapping(123, 456)),
                                    environment = mapOf("SOME_VAR" to LiteralValue("SOME_VALUE")),
                                    healthCheckConfig = HealthCheckConfig(Duration.ofSeconds(2), 10, Duration.ofSeconds(5)),
                                    privileged = false,
                                    enableInitProcess = true,
                                    capabilitiesToAdd = setOf(Capability.NET_ADMIN),
                                    capabilitiesToDrop = setOf(Capability.KILL)
                                )

                                val userAndGroup = UserAndGroup(123, 456)
                                val request = factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, additionalVolumeMounts, additionalPortMappings, propagateProxyEnvironmentVariables, userAndGroup, allContainersInNetwork)

                                it("populates the image on the request") {
                                    assertThat(request.image, equalTo(image))
                                }

                                it("populates the network on the request") {
                                    assertThat(request.network, equalTo(network))
                                }

                                it("populates the command on the request") {
                                    assertThat(request.command, equalTo(command.parsedCommand))
                                }

                                it("populates the entrypoint on the request") {
                                    assertThat(request.entrypoint, equalTo(entrypoint.parsedCommand))
                                }

                                it("populates the hostname and network alias on the request with the name of the container") {
                                    assertThat(request.hostname, equalTo(container.name))
                                    assertThat(request.networkAlias, equalTo(container.name))
                                }

                                it("populates the environment variables on the request with the environment variables from the container") {
                                    assertThat(request.environmentVariables, equalTo(mapOf("SOME_VAR" to "SOME_VALUE")))
                                }

                                it("populates the working directory on the request with the working directory provided, not from the container") {
                                    assertThat(request.workingDirectory, equalTo(workingDirectory))
                                }

                                it("populates the volume mounts on the request with the volume mounts from the container") {
                                    assertThat(request.volumeMounts, equalTo(container.volumeMounts))
                                }

                                it("populates the port mappings on the request with the port mappings from the container") {
                                    assertThat(request.portMappings, equalTo(container.portMappings))
                                }

                                it("populates the health check configuration on the request with the health check configuration from the container") {
                                    assertThat(request.healthCheckConfig, equalTo(container.healthCheckConfig))
                                }

                                it("populates the user and group configuration on the request with the provided values") {
                                    assertThat(request.userAndGroup, equalTo(userAndGroup))
                                }

                                it("populates the privileged mode with the setting from the container") {
                                    assertThat(request.privileged, equalTo(false))
                                }

                                it("populates the init configuration on the request with the enable init process configuration from the container") {
                                    assertThat(request.init, equalTo(container.enableInitProcess))
                                }

                                it("populates the capabilities to add on the request with the set from the container") {
                                    assertThat(request.capabilitiesToAdd, equalTo(container.capabilitiesToAdd))
                                }

                                it("populates the capabilities to drop on the request with the set from the container") {
                                    assertThat(request.capabilitiesToDrop, equalTo(container.capabilitiesToDrop))
                                }
                            }
                        }

                        given("there are additional port mappings") {
                            val additionalPortMappings = setOf(
                                PortMapping(1000, 2000)
                            )

                            on("creating the request") {
                                val container = Container(
                                    "some-container",
                                    imageSourceDoesNotMatter(),
                                    portMappings = setOf(PortMapping(123, 456))
                                )

                                val request = factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, additionalVolumeMounts, additionalPortMappings, propagateProxyEnvironmentVariables, null, allContainersInNetwork)

                                it("populates the port mappings on the request with the combined set of port mappings from the container and the additional port mappings") {
                                    assertThat(request.portMappings, equalTo(setOf(
                                        PortMapping(123, 456),
                                        PortMapping(1000, 2000)
                                    )))
                                }
                            }
                        }
                    }

                    given("there are additional volume mounts") {
                        val additionalVolumeMounts = setOf(VolumeMount("extra-local", "extra-remote", "extra-mode"))

                        on("creating the request") {
                            val container = Container(
                                "some-container",
                                imageSourceDoesNotMatter(),
                                volumeMounts = setOf(VolumeMount("local", "remote", "mode"))
                            )

                            val request = factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, additionalVolumeMounts, emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork)

                            it("populates the volume mounts on the request with the combined set of volume mounts from the container and the additional volume mounts") {
                                assertThat(request.volumeMounts, equalTo(setOf(
                                    VolumeMount("local", "remote", "mode"),
                                    VolumeMount("extra-local", "extra-remote", "extra-mode")
                                )))
                            }
                        }
                    }
                }

                given("there is no explicit command to run") {
                    val container = Container("some-container", imageSourceDoesNotMatter(), command = null)

                    on("creating the request") {
                        val request = factory.create(container, image, network, null, entrypoint, workingDirectory, emptyMap(), emptySet(), emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork)

                        it("does not populate the command on the request") {
                            assertThat(request.command, equalTo(emptyList()))
                        }
                    }
                }

                given("there is no explicit entrypoint") {
                    val container = Container("some-container", imageSourceDoesNotMatter(), command = null)

                    on("creating the request") {
                        val request = factory.create(container, image, network, command, null, workingDirectory, emptyMap(), emptySet(), emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork)

                        it("does not populate the command on the request") {
                            assertThat(request.entrypoint, equalTo(emptyList()))
                        }
                    }
                }

                given("there are additional environment variables") {
                    val additionalEnvironmentVariables = mapOf(
                        "SOME_HOST_VAR" to LiteralValue("SOME_HOST_VALUE")
                    )

                    given("none of them conflict with environment variables on the container") {
                        val container = Container(
                            "some-container",
                            imageSourceDoesNotMatter(),
                            environment = mapOf("SOME_VAR" to LiteralValue("SOME_VALUE"))
                        )

                        on("creating the request") {
                            val request = factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, emptySet(), emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork)

                            it("populates the environment variables on the request with the environment variables from the container and from the additional environment variables") {
                                assertThat(request.environmentVariables, equalTo(mapOf(
                                    "SOME_VAR" to "SOME_VALUE",
                                    "SOME_HOST_VAR" to "SOME_HOST_VALUE"
                                )))
                            }
                        }
                    }

                    given("one of them conflicts with environment variables on the container") {
                        val container = Container(
                            "some-container",
                            imageSourceDoesNotMatter(),
                            environment = mapOf("SOME_HOST_VAR" to LiteralValue("SOME_CONTAINER_VALUE"))
                        )

                        on("creating the request") {
                            val request = factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, emptySet(), emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork)

                            it("populates the environment variables on the request with the environment variables from the container and from the additional environment variables, with the additional environment variables taking precedence") {
                                assertThat(request.environmentVariables, equalTo(mapOf(
                                    "SOME_HOST_VAR" to "SOME_HOST_VALUE"
                                )))
                            }
                        }
                    }
                }
            }
        }

        given("there are references to host environment variables") {
            val consoleInfo = mock<ConsoleInfo>()
            val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider>()
            val hostEnvironmentVariables = mapOf("SOME_HOST_VARIABLE" to "SOME_HOST_VALUE")
            val factory = DockerContainerCreationRequestFactory(consoleInfo, proxyEnvironmentVariablesProvider, hostEnvironmentVariables)
            val propagateProxyEnvironmentVariables = false

            given("and those references are on the container") {
                val additionalEnvironmentVariables = emptyMap<String, EnvironmentVariableExpression>()

                given("and the reference is valid") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter(),
                        environment = mapOf("SOME_VAR" to ReferenceValue("SOME_HOST_VARIABLE"))
                    )

                    on("creating the request") {
                        val request = factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, emptySet(), emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork)

                        it("populates the environment variables on the request with the environment variables' values from the host") {
                            assertThat(request.environmentVariables, equalTo(mapOf(
                                "SOME_VAR" to "SOME_HOST_VALUE"
                            )))
                        }
                    }
                }

                given("and the reference is to an environment variable that does not exist on the host") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter(),
                        environment = mapOf("SOME_VAR" to ReferenceValue("SOME_HOST_VARIABLE_THAT_ISNT_DEFINED"))
                    )

                    on("creating the request") {
                        it("throws an appropriate exception") {
                            assertThat({ factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, emptySet(), emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork) },
                                throws<ContainerCreationFailedException>(withMessage("The value for the environment variable 'SOME_VAR' cannot be evaluated: The host environment variable 'SOME_HOST_VARIABLE_THAT_ISNT_DEFINED' is not set, and no default value has been provided.")))
                        }
                    }
                }
            }

            given("and those references are in the additional environment variables") {
                given("and the reference is valid") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter()
                    )

                    val additionalEnvironmentVariables = mapOf("SOME_VAR" to ReferenceValue("SOME_HOST_VARIABLE"))

                    on("creating the request") {
                        val request = factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, emptySet(), emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork)

                        it("populates the environment variables on the request with the environment variables' values from the host") {
                            assertThat(request.environmentVariables, equalTo(mapOf(
                                "SOME_VAR" to "SOME_HOST_VALUE"
                            )))
                        }
                    }
                }

                given("and the references is to an environment variable that does not exist on the host") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter()
                    )

                    val additionalEnvironmentVariables = mapOf("SOME_VAR" to ReferenceValue("SOME_HOST_VARIABLE_THAT_ISNT_DEFINED"))

                    on("creating the request") {
                        it("throws an appropriate exception") {
                            assertThat({ factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, emptySet(), emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork) },
                                throws<ContainerCreationFailedException>(withMessage("The value for the environment variable 'SOME_VAR' cannot be evaluated: The host environment variable 'SOME_HOST_VARIABLE_THAT_ISNT_DEFINED' is not set, and no default value has been provided.")))
                        }
                    }
                }

                given("and the reference overrides a container-level environment variable that does not exist on the host") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter(),
                        environment = mapOf("SOME_VAR" to ReferenceValue("SOME_HOST_VARIABLE_THAT_ISNT_DEFINED"))
                    )

                    val additionalEnvironmentVariables = mapOf("SOME_VAR" to ReferenceValue("SOME_HOST_VARIABLE"))

                    on("creating the request") {
                        val request = factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, emptySet(), emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork)

                        it("populates the environment variables on the request with the environment variables' values from the host and does not throw an exception") {
                            assertThat(request.environmentVariables, equalTo(mapOf(
                                "SOME_VAR" to "SOME_HOST_VALUE"
                            )))
                        }
                    }
                }
            }
        }

        given("there are proxy environment variables present on the host") {
            val consoleInfo = mock<ConsoleInfo>()
            val hostEnvironmentVariables = emptyMap<String, String>()
            val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider> {
                on { getProxyEnvironmentVariables(setOf("container-1", "container-2")) } doReturn mapOf(
                    "HTTP_PROXY" to "http://some-proxy",
                    "NO_PROXY" to "dont-proxy-this"
                )
            }

            val factory = DockerContainerCreationRequestFactory(consoleInfo, proxyEnvironmentVariablesProvider, hostEnvironmentVariables)

            given("propagating proxy environment variables is enabled") {
                val propagateProxyEnvironmentVariables = true

                given("neither the container nor the additional environment variables override the proxy settings") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter()
                    )

                    val additionalEnvironmentVariables = emptyMap<String, EnvironmentVariableExpression>()

                    on("creating the request") {
                        val request = factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, emptySet(), emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork)

                        it("populates the environment variables on the request with the proxy environment variables from the host") {
                            assertThat(request.environmentVariables, equalTo(mapOf(
                                "HTTP_PROXY" to "http://some-proxy",
                                "NO_PROXY" to "dont-proxy-this"
                            )))
                        }
                    }
                }

                given("the container overrides the proxy settings") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter(),
                        environment = mapOf(
                            "HTTP_PROXY" to LiteralValue("http://some-other-proxy")
                        )
                    )

                    val additionalEnvironmentVariables = emptyMap<String, EnvironmentVariableExpression>()

                    on("creating the request") {
                        val request = factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, emptySet(), emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork)

                        it("populates the environment variables on the request with the proxy environment variables from the host, with overrides from the container") {
                            assertThat(request.environmentVariables, equalTo(mapOf(
                                "HTTP_PROXY" to "http://some-other-proxy",
                                "NO_PROXY" to "dont-proxy-this"
                            )))
                        }
                    }
                }

                given("the additional environment variables override the proxy settings") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter(),
                        environment = mapOf(
                            "HTTP_PROXY" to LiteralValue("http://some-other-proxy")
                        )
                    )

                    val additionalEnvironmentVariables = mapOf(
                        "HTTP_PROXY" to LiteralValue("http://some-additional-proxy")
                    )

                    on("creating the request") {
                        val request = factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, emptySet(), emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork)

                        it("populates the environment variables on the request with the proxy environment variables from the host, with overrides from the container and additional environment variables") {
                            assertThat(request.environmentVariables, equalTo(mapOf(
                                "HTTP_PROXY" to "http://some-additional-proxy",
                                "NO_PROXY" to "dont-proxy-this"
                            )))
                        }
                    }
                }
            }

            given("propagating proxy environment variables is disabled") {
                val propagateProxyEnvironmentVariables = false

                on("creating the request") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter()
                    )

                    val additionalEnvironmentVariables = emptyMap<String, EnvironmentVariableExpression>()
                    val request = factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, emptySet(), emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork)

                    it("does not propagate the proxy environment variables") {
                        assertThat(request.environmentVariables, isEmptyMap())
                    }
                }
            }
        }

        given("the console's type is available") {
            val consoleInfo = mock<ConsoleInfo> {
                on { terminalType } doReturn "some-term"
            }

            val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider>()
            val hostEnvironmentVariables = emptyMap<String, String>()
            val factory = DockerContainerCreationRequestFactory(consoleInfo, proxyEnvironmentVariablesProvider, hostEnvironmentVariables)
            val propagateProxyEnvironmentVariables = false

            given("a container with no override for the TERM environment variable") {
                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    environment = mapOf("SOME_VAR" to LiteralValue("SOME_VALUE"))
                )

                val additionalEnvironmentVariables = emptyMap<String, EnvironmentVariableExpression>()

                on("creating the request") {
                    val request = factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, emptySet(), emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork)

                    it("populates the environment variables on the request with the environment variables from the container and the TERM environment variable from the host") {
                        assertThat(request.environmentVariables, equalTo(mapOf(
                            "SOME_VAR" to "SOME_VALUE",
                            "TERM" to "some-term"
                        )))
                    }
                }
            }

            given("a container with an override for the TERM environment variable") {
                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    environment = mapOf(
                        "SOME_VAR" to LiteralValue("SOME_VALUE"),
                        "TERM" to LiteralValue("some-other-term")
                    )
                )

                val additionalEnvironmentVariables = emptyMap<String, EnvironmentVariableExpression>()

                on("creating the request") {
                    val request = factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, emptySet(), emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork)

                    it("populates the environment variables on the request with the environment variables from the container and the TERM environment variable from the container") {
                        assertThat(request.environmentVariables, equalTo(mapOf(
                            "SOME_VAR" to "SOME_VALUE",
                            "TERM" to "some-other-term"
                        )))
                    }
                }
            }

            given("some additional environment variables with an override for the TERM environment variable") {
                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    environment = mapOf(
                        "SOME_VAR" to LiteralValue("SOME_VALUE")
                    )
                )

                val additionalEnvironmentVariables = mapOf("TERM" to LiteralValue("some-additional-term"))

                on("creating the request") {
                    val request = factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, emptySet(), emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork)

                    it("populates the environment variables on the request with the environment variables from the container and the TERM environment variable from the additional environment variables") {
                        assertThat(request.environmentVariables, equalTo(mapOf(
                            "SOME_VAR" to "SOME_VALUE",
                            "TERM" to "some-additional-term"
                        )))
                    }
                }
            }

            given("both the container and the additional environment variables have an override for the TERM environment variable") {
                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    environment = mapOf(
                        "SOME_VAR" to LiteralValue("SOME_VALUE"),
                        "TERM" to LiteralValue("some-container-term")
                    )
                )

                val additionalEnvironmentVariables = mapOf("TERM" to LiteralValue("some-additional-term"))

                on("creating the request") {
                    val request = factory.create(container, image, network, command, entrypoint, workingDirectory, additionalEnvironmentVariables, emptySet(), emptySet(), propagateProxyEnvironmentVariables, null, allContainersInNetwork)

                    it("populates the environment variables on the request with the environment variables from the container and the TERM environment variable from the additional environment variables") {
                        assertThat(request.environmentVariables, equalTo(mapOf(
                            "SOME_VAR" to "SOME_VALUE",
                            "TERM" to "some-additional-term"
                        )))
                    }
                }
            }
        }
    }
})
