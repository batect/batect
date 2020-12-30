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

package batect.docker

import batect.cli.CommandLineOptions
import batect.config.BinarySize
import batect.config.BinaryUnit
import batect.config.Container
import batect.config.DeviceMount
import batect.config.HealthCheckConfig
import batect.config.LiteralValue
import batect.config.PortMapping
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
        val volumeMounts = setOf(DockerVolumeMount(DockerVolumeMountSource.LocalPath("local"), "remote", "mode"))
        val additionalEnvironmentVariables = mapOf("SOME_VAR" to LiteralValue("some value"))
        val expectedEnvironmentVariables = mapOf("SOME_VAR" to "some resolved value")

        val environmentVariablesProvider = mock<DockerContainerEnvironmentVariableProvider> {
            on { environmentVariablesFor(any(), any(), eq(terminalType)) } doReturn expectedEnvironmentVariables
        }

        val nameGenerator = mock<DockerResourceNameGenerator> {
            on { generateNameFor(any<Container>()) } doReturn "the-container-name"
        }

        val commandLineOptions = mock<CommandLineOptions> {
            on { disablePortMappings } doReturn false
        }

        val factory = DockerContainerCreationRequestFactory(environmentVariablesProvider, nameGenerator, commandLineOptions)

        given("there are no additional port mappings") {
            val additionalPortMappings = emptySet<PortMapping>()

            on("creating the request") {
                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    command = Command.parse("some-command-that-wont-be-used"),
                    entrypoint = Command.parse("some-command-that-wont-be-used"),
                    workingDirectory = "/some-work-dir",
                    deviceMounts = setOf(DeviceMount("/dev/local", "/dev/container", "options")),
                    portMappings = setOf(PortMapping(123, 456)),
                    healthCheckConfig = HealthCheckConfig(Duration.ofSeconds(2), 10, Duration.ofSeconds(5)),
                    privileged = false,
                    enableInitProcess = true,
                    capabilitiesToAdd = setOf(Capability.NET_ADMIN),
                    capabilitiesToDrop = setOf(Capability.KILL),
                    additionalHostnames = setOf("some-alias"),
                    additionalHosts = mapOf("does.not.exist" to "1.2.3.4"),
                    logDriver = "the-log-driver",
                    logOptions = mapOf("option-1" to "value-1"),
                    shmSize = BinarySize.of(2, BinaryUnit.Megabyte)
                )

                val userAndGroup = UserAndGroup(123, 456)
                val config = ContainerRuntimeConfiguration(command, entrypoint, workingDirectory, additionalEnvironmentVariables, additionalPortMappings)
                val request = factory.create(
                    container,
                    image,
                    network,
                    config,
                    volumeMounts,
                    userAndGroup,
                    terminalType,
                    useTTY = false,
                    attachStdin = true
                )

                it("populates the container name on the request") {
                    assertThat(request.name, equalTo("the-container-name"))
                }

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

                it("populates the extra hosts on the request with the additional hosts from the container") {
                    assertThat(request.extraHosts, equalTo(container.additionalHosts))
                }

                it("populates the environment variables on the request with the environment variables from the environment variable provider") {
                    assertThat(request.environmentVariables, equalTo(expectedEnvironmentVariables))
                }

                it("populates the working directory on the request with the working directory provided, not from the container") {
                    assertThat(request.workingDirectory, equalTo(workingDirectory))
                }

                it("populates the volume mounts on the request with the volume mounts provided") {
                    assertThat(request.volumeMounts, equalTo(volumeMounts))
                }

                it("populates the device mounts on the request with the device mounts from the container") {
                    assertThat(request.deviceMounts, equalTo(setOf(DockerDeviceMount("/dev/local", "/dev/container", "options"))))
                }

                it("populates the port mappings on the request with the port mappings from the container") {
                    assertThat(request.portMappings, equalTo(setOf(DockerPortMapping(123, 456))))
                }

                it("populates the health check configuration on the request with the health check configuration from the container") {
                    assertThat(request.healthCheckConfig, equalTo(batect.docker.HealthCheckConfig(Duration.ofSeconds(2), 10, Duration.ofSeconds(5))))
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

                it("populates the 'use TTY' setting on the request with the provided value") {
                    assertThat(request.useTTY, equalTo(false))
                }

                it("populates the stdin configuration with the provided value") {
                    assertThat(request.attachStdin, equalTo(true))
                }

                it("populates the log driver with the value from the container") {
                    assertThat(request.logDriver, equalTo(container.logDriver))
                }

                it("populates the log options with the value from the container") {
                    assertThat(request.logOptions, equalTo(container.logOptions))
                }

                it("populates the shm size with the value from the container") {
                    assertThat(request.shmSize, equalTo(container.shmSize!!.bytes))
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
                val request = factory.create(
                    container,
                    image,
                    network,
                    config,
                    volumeMounts,
                    null,
                    terminalType,
                    useTTY = false,
                    attachStdin = false
                )

                it("populates the port mappings on the request with the combined set of port mappings from the container and the additional port mappings") {
                    assertThat(
                        request.portMappings,
                        equalTo(
                            setOf(
                                DockerPortMapping(123, 456),
                                DockerPortMapping(1000, 2000)
                            )
                        )
                    )
                }
            }
        }

        given("global port mappings are disabled") {
            val commandLineOptionsWithDisabledPorts = mock<CommandLineOptions> {
                on { disablePortMappings } doReturn true
            }

            val newFactory = DockerContainerCreationRequestFactory(environmentVariablesProvider, nameGenerator, commandLineOptionsWithDisabledPorts)

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
                val request = newFactory.create(container, image, network, config, volumeMounts, null, terminalType, useTTY = false, attachStdin = false)

                it("yields an empty port mapping") {
                    assertThat(request.portMappings, equalTo(emptySet()))
                }
            }
        }

        given("there is no explicit command to run") {
            val container = Container("some-container", imageSourceDoesNotMatter(), command = null)

            on("creating the request") {
                val config = ContainerRuntimeConfiguration(null, entrypoint, workingDirectory, emptyMap(), emptySet())
                val request = factory.create(container, image, network, config, emptySet(), null, terminalType, false, false)

                it("does not populate the command on the request") {
                    assertThat(request.command, equalTo(emptyList()))
                }
            }
        }

        given("there is no explicit entrypoint") {
            val container = Container("some-container", imageSourceDoesNotMatter(), entrypoint = null)

            on("creating the request") {
                val config = ContainerRuntimeConfiguration(command, null, workingDirectory, emptyMap(), emptySet())
                val request = factory.create(container, image, network, config, emptySet(), null, terminalType, false, false)

                it("does not populate the entrypoint on the request") {
                    assertThat(request.entrypoint, equalTo(emptyList()))
                }
            }
        }
    }
})
