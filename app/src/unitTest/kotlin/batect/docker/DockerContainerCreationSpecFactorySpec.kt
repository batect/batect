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
import batect.config.BinarySize
import batect.config.BinaryUnit
import batect.config.Capability
import batect.config.Container
import batect.config.DeviceMount
import batect.config.HealthCheckConfig
import batect.config.PortMapping
import batect.dockerclient.ExposedPort
import batect.dockerclient.ExtraHost
import batect.dockerclient.HostMount
import batect.dockerclient.NetworkReference
import batect.dockerclient.UserAndGroup
import batect.dockerclient.VolumeMount
import batect.dockerclient.VolumeReference
import batect.os.Command
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import okio.Path.Companion.toPath
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.time.Duration.Companion.seconds

object DockerContainerCreationSpecFactorySpec : Spek({
    describe("a Docker container creation spec factory") {
        val image = batect.dockerclient.ImageReference("some-image")
        val network = NetworkReference("some-network")
        val command = Command.parse("some-app some-arg")
        val entrypoint = Command.parse("sh")
        val workingDirectory = "some-specific-working-directory"
        val terminalType = "some-term"

        val volumeMounts = setOf(
            HostMount("local".toPath(), "remote-host", "mode"),
            VolumeMount(VolumeReference("some-volume"), "remote-volume", "mode")
        )

        val expectedEnvironmentVariables = mapOf("SOME_VAR" to "some resolved value")

        val environmentVariablesProvider = mock<DockerContainerEnvironmentVariableProvider> {
            on { environmentVariablesFor(any(), eq(terminalType)) } doReturn expectedEnvironmentVariables
        }

        val nameGenerator = mock<DockerResourceNameGenerator> {
            on { generateNameFor(any<Container>()) } doReturn "the-container-name"
        }

        val commandLineOptions = mock<CommandLineOptions> {
            on { disablePortMappings } doReturn false
        }

        val factory = DockerContainerCreationSpecFactory(environmentVariablesProvider, nameGenerator, commandLineOptions)

        given("a container") {
            on("creating a creation request") {
                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    command = command,
                    entrypoint = entrypoint,
                    workingDirectory = workingDirectory,
                    deviceMounts = setOf(DeviceMount("/dev/local", "/dev/container", "options")),
                    portMappings = setOf(PortMapping(123, 456)),
                    healthCheckConfig = HealthCheckConfig(2.seconds, 10, 5.seconds, 7.seconds, "some-health-check-command"),
                    privileged = false,
                    enableInitProcess = true,
                    capabilitiesToAdd = setOf(Capability.NET_ADMIN),
                    capabilitiesToDrop = setOf(Capability.KILL),
                    additionalHostnames = setOf("some-alias"),
                    additionalHosts = mapOf("does.not.exist" to "1.2.3.4"),
                    logDriver = "the-log-driver",
                    logOptions = mapOf("option-1" to "value-1"),
                    shmSize = BinarySize.of(2, BinaryUnit.Megabyte),
                    labels = mapOf("some.key" to "some_value")
                )

                val userAndGroup = UserAndGroup(123, 456)
                val spec = factory.create(
                    container,
                    image,
                    network,
                    volumeMounts,
                    userAndGroup,
                    terminalType,
                    useTTY = false,
                    attachStdin = true
                )

                it("populates the container name on the request") {
                    assertThat(spec.name, equalTo("the-container-name"))
                }

                it("populates the image on the request") {
                    assertThat(spec.image, equalTo(image))
                }

                it("populates the network on the request") {
                    assertThat(spec.network, equalTo(network))
                }

                it("populates the command on the request") {
                    assertThat(spec.command, equalTo(command.parsedCommand))
                }

                it("populates the entrypoint on the request") {
                    assertThat(spec.entrypoint, equalTo(entrypoint.parsedCommand))
                }

                it("populates the hostname on the request with the name of the container") {
                    assertThat(spec.hostname, equalTo(container.name))
                }

                it("populates the network aliases on the request with the name of the container and additional hostnames from the container") {
                    assertThat(spec.networkAliases, equalTo(setOf(container.name, "some-alias")))
                }

                it("populates the extra hosts on the request with the additional hosts from the container") {
                    assertThat(spec.extraHosts, equalTo(setOf(ExtraHost("does.not.exist", "1.2.3.4"))))
                }

                it("populates the environment variables on the request with the environment variables from the environment variable provider") {
                    assertThat(spec.environmentVariables, equalTo(expectedEnvironmentVariables))
                }

                it("populates the working directory on the request with the working directory provided, not from the container") {
                    assertThat(spec.workingDirectory, equalTo(workingDirectory))
                }

                it("populates the volume mounts on the request with the volume mounts provided") {
                    assertThat(
                        spec.bindMounts,
                        equalTo(
                            setOf(
                                HostMount("local".toPath(), "remote-host", "mode"),
                                VolumeMount(VolumeReference("some-volume"), "remote-volume", "mode")
                            )
                        )
                    )
                }

                it("populates the device mounts on the request with the device mounts from the container") {
                    assertThat(spec.deviceMounts, equalTo(setOf(batect.dockerclient.DeviceMount("/dev/local".toPath(), "/dev/container", "options"))))
                }

                it("populates the port mappings on the request with the port mappings from the container") {
                    assertThat(spec.exposedPorts, equalTo(setOf(ExposedPort(123, 456))))
                }

                it("populates the health check configuration on the request with the health check configuration from the container") {
                    assertThat(spec.healthcheckInterval, equalTo(2.seconds))
                    assertThat(spec.healthcheckRetries, equalTo(10))
                    assertThat(spec.healthcheckStartPeriod, equalTo(5.seconds))
                    assertThat(spec.healthcheckTimeout, equalTo(7.seconds))
                    assertThat(spec.healthcheckCommand, equalTo(listOf("some-health-check-command")))
                }

                it("populates the user and group configuration on the request with the provided values") {
                    assertThat(spec.userAndGroup, equalTo(batect.dockerclient.UserAndGroup(123, 456)))
                }

                it("populates the privileged mode with the setting from the container") {
                    assertThat(spec.privileged, equalTo(false))
                }

                it("populates the init configuration on the request with the enable init process configuration from the container") {
                    assertThat(spec.useInitProcess, equalTo(container.enableInitProcess))
                }

                it("populates the capabilities to add on the request with the set from the container") {
                    assertThat(spec.capabilitiesToAdd, equalTo(setOf(batect.dockerclient.Capability.NET_ADMIN)))
                }

                it("populates the capabilities to drop on the request with the set from the container") {
                    assertThat(spec.capabilitiesToDrop, equalTo(setOf(batect.dockerclient.Capability.KILL)))
                }

                it("populates the 'use TTY' setting on the request with the provided value") {
                    assertThat(spec.attachTTY, equalTo(false))
                }

                it("populates the stdin configuration with the provided value") {
                    assertThat(spec.attachStdin, equalTo(true))
                }

                it("populates the log driver with the value from the container") {
                    assertThat(spec.logDriver, equalTo(container.logDriver))
                }

                it("populates the log options with the value from the container") {
                    assertThat(spec.loggingOptions, equalTo(container.logOptions))
                }

                it("populates the shm size with the value from the container") {
                    assertThat(spec.shmSizeInBytes, equalTo(container.shmSize!!.bytes))
                }

                it("populates the labels with the labels from the container") {
                    assertThat(spec.labels, equalTo(container.labels))
                }
            }
        }

        given("global port mappings are disabled") {
            val commandLineOptionsWithDisabledPorts = mock<CommandLineOptions> {
                on { disablePortMappings } doReturn true
            }

            val newFactory = DockerContainerCreationSpecFactory(environmentVariablesProvider, nameGenerator, commandLineOptionsWithDisabledPorts)

            on("creating the request") {
                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    portMappings = setOf(PortMapping(123, 456))
                )

                val spec = newFactory.create(container, image, network, volumeMounts, null, terminalType, useTTY = false, attachStdin = false)

                it("yields an empty port mapping") {
                    assertThat(spec.exposedPorts, equalTo(emptySet()))
                }
            }
        }

        given("there is no explicit command to run") {
            val container = Container("some-container", imageSourceDoesNotMatter(), command = null)

            on("creating the request") {
                val request = factory.create(container, image, network, emptySet(), null, terminalType, false, false)

                it("does not populate the command on the request") {
                    assertThat(request.command, equalTo(emptyList()))
                }
            }
        }

        given("there is no explicit entrypoint") {
            val container = Container("some-container", imageSourceDoesNotMatter(), entrypoint = null)

            on("creating the request") {
                val request = factory.create(container, image, network, emptySet(), null, terminalType, false, false)

                it("does not populate the entrypoint on the request") {
                    assertThat(request.entrypoint, equalTo(emptyList()))
                }
            }
        }
    }
})
