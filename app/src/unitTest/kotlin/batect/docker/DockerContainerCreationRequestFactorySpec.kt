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
import batect.config.HealthCheckConfig
import batect.config.LiteralValue
import batect.config.PortMapping
import batect.config.DeviceMount
import batect.config.VolumeMount
import batect.execution.ContainerRuntimeConfiguration
import batect.os.Command
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
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
        val terminalType = "some-term"
        val allContainersInNetwork = setOf(
            Container("container-1", imageSourceDoesNotMatter()),
            Container("container-2", imageSourceDoesNotMatter())
        )

        val propagateProxyEnvironmentVariables = true
        val additionalEnvironmentVariables = mapOf("SOME_VAR" to LiteralValue("some value"))
        val expectedEnvironmentVariables = mapOf("SOME_VAR" to "some resolved value")

        val environmentVariablesProvider = mock<DockerContainerEnvironmentVariableProvider> {
            on { environmentVariablesFor(any(), any(), eq(propagateProxyEnvironmentVariables), eq(terminalType), eq(allContainersInNetwork)) } doReturn expectedEnvironmentVariables
        }

        val factory = DockerContainerCreationRequestFactory(environmentVariablesProvider)

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
                        deviceMounts = setOf(DeviceMount("/dev/local", "/dev/container", "options")),
                        portMappings = setOf(PortMapping(123, 456)),
                        healthCheckConfig = HealthCheckConfig(Duration.ofSeconds(2), 10, Duration.ofSeconds(5)),
                        privileged = false,
                        enableInitProcess = true,
                        capabilitiesToAdd = setOf(Capability.NET_ADMIN),
                        capabilitiesToDrop = setOf(Capability.KILL),
                        additionalHostnames = setOf("some-alias")
                    )

                    val userAndGroup = UserAndGroup(123, 456)
                    val config = ContainerRuntimeConfiguration(command, entrypoint, workingDirectory, additionalEnvironmentVariables, additionalPortMappings)
                    val request = factory.create(
                        container,
                        image,
                        network,
                        config,
                        additionalVolumeMounts,
                        propagateProxyEnvironmentVariables,
                        userAndGroup,
                        terminalType,
                        allContainersInNetwork
                    )

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

                    it("populates the hostname on the request with the name of the container") {
                        assertThat(request.hostname, equalTo(container.name))
                    }

                    it("populates the network aliases on the request with the name of the container and additional hostnames from the container") {
                        assertThat(request.networkAliases, equalTo(setOf(container.name, "some-alias")))
                    }

                    it("populates the environment variables on the request with the environment variables from the environment variable provider") {
                        assertThat(request.environmentVariables, equalTo(expectedEnvironmentVariables))
                    }

                    it("populates the working directory on the request with the working directory provided, not from the container") {
                        assertThat(request.workingDirectory, equalTo(workingDirectory))
                    }

                    it("populates the volume mounts on the request with the volume mounts from the container") {
                        assertThat(request.volumeMounts, equalTo(container.volumeMounts))
                    }

                    it("populates the device mounts on the request with the device mounts from the container") {
                        assertThat(request.deviceMounts, equalTo(container.deviceMounts))
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

                    val config = ContainerRuntimeConfiguration(command, entrypoint, workingDirectory, additionalEnvironmentVariables, additionalPortMappings)
                    val request = factory.create(container, image, network, config, additionalVolumeMounts, propagateProxyEnvironmentVariables, null, terminalType, allContainersInNetwork)

                    it("populates the port mappings on the request with the combined set of port mappings from the container and the additional port mappings") {
                        assertThat(
                            request.portMappings, equalTo(
                                setOf(
                                    PortMapping(123, 456),
                                    PortMapping(1000, 2000)
                                )
                            )
                        )
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

                val config = ContainerRuntimeConfiguration(command, entrypoint, workingDirectory, additionalEnvironmentVariables, emptySet())
                val request = factory.create(container, image, network, config, additionalVolumeMounts, propagateProxyEnvironmentVariables, null, terminalType, allContainersInNetwork)

                it("populates the volume mounts on the request with the combined set of volume mounts from the container and the additional volume mounts") {
                    assertThat(
                        request.volumeMounts, equalTo(
                            setOf(
                                VolumeMount("local", "remote", "mode"),
                                VolumeMount("extra-local", "extra-remote", "extra-mode")
                            )
                        )
                    )
                }
            }
        }

        given("there is no explicit command to run") {
            val container = Container("some-container", imageSourceDoesNotMatter(), command = null)

            on("creating the request") {
                val config = ContainerRuntimeConfiguration(null, entrypoint, workingDirectory, emptyMap(), emptySet())
                val request = factory.create(container, image, network, config, emptySet(), propagateProxyEnvironmentVariables, null, terminalType, allContainersInNetwork)

                it("does not populate the command on the request") {
                    assertThat(request.command, equalTo(emptyList()))
                }
            }
        }

        given("there is no explicit entrypoint") {
            val container = Container("some-container", imageSourceDoesNotMatter(), entrypoint = null)

            on("creating the request") {
                val config = ContainerRuntimeConfiguration(command, null, workingDirectory, emptyMap(), emptySet())
                val request = factory.create(container, image, network, config, emptySet(), propagateProxyEnvironmentVariables, null, terminalType, allContainersInNetwork)

                it("does not populate the entrypoint on the request") {
                    assertThat(request.entrypoint, equalTo(emptyList()))
                }
            }
        }
    }
})
