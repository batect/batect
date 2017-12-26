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

package batect.docker

import batect.config.Container
import batect.config.HealthCheckConfig
import batect.config.PortMapping
import batect.config.VolumeMount
import batect.os.CommandParser
import batect.os.InvalidCommandLineException
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.withMessage
import batect.ui.ConsoleInfo
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DockerContainerCreationCommandGeneratorSpec : Spek({
    describe("a Docker container creation command generator") {
        val hostEnvironmentVariables = mapOf("SOME_HOST_VARIABLE" to "some value from the host")

        val commandParser = mock<CommandParser> {
            on { parse(any()) } doAnswer { listOf("parsed-${it.arguments[0]}") }
            on { parse("invalid") } doThrow InvalidCommandLineException("The command line is not valid")
        }

        val generator = DockerContainerCreationCommandGenerator(commandParser, hostEnvironmentVariables)
        val image = DockerImage("the-image")
        val network = DockerNetwork("the-network")

        given("a simple container definition, a built image and an explicit command to run") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val command = "doStuff"
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, emptyMap(), image, network, consoleInfo)

                it("generates the correct command line, taking the command from the task") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        image.id,
                        "parsed-$command").asIterable()))
                }
            }
        }

        given("a simple container definition, a built image and no explicit command to run") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val command = null
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, emptyMap(), image, network, consoleInfo)

                it("generates the correct command line, taking the command from the container") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        image.id).asIterable()))
                }
            }
        }

        given("a container with some additional environment variables") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val command = null
            val additionalEnvironmentVariables = mapOf("SOME_VAR" to "some value")
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, additionalEnvironmentVariables, image, network, consoleInfo)

                it("generates the correct command line, taking the command from the container") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        "--env", "SOME_VAR=some value",
                        image.id).asIterable()))
                }
            }
        }

        given("a container with some additional environment variables that override values for environment variables for the container") {
            val container = Container("the-container", imageSourceDoesNotMatter(), environment = mapOf("SOME_VAR" to "original value"))
            val command = null
            val additionalEnvironmentVariables = mapOf("SOME_VAR" to "some value")
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, additionalEnvironmentVariables, image, network, consoleInfo)

                it("generates the correct command line, taking the command from the container") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        "--env", "SOME_VAR=some value",
                        image.id).asIterable()))
                }
            }
        }

        given("a container with an environment variable that takes its value from the host environment") {
            val container = Container("the-container", imageSourceDoesNotMatter(), environment = mapOf("SOME_VAR" to "\$SOME_HOST_VARIABLE"))
            val command = null
            val additionalEnvironmentVariables = emptyMap<String, String>()
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, additionalEnvironmentVariables, image, network, consoleInfo)

                it("generates the correct command line, taking the environment variable value from the host environment variables") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        "--env", "SOME_VAR=some value from the host",
                        image.id).asIterable()))
                }
            }
        }

        given("a container with an environment variable that takes its value from a host variable that doesn't exist") {
            val container = Container("the-container", imageSourceDoesNotMatter(), environment = mapOf("SOME_VAR" to "\$SOME_HOST_VARIABLE_THAT_DOES_NOT_EXIST"))
            val command = null
            val additionalEnvironmentVariables = emptyMap<String, String>()
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                it("throws an appropriate exception") {
                    assertThat({ generator.createCommandLine(container, command, additionalEnvironmentVariables, image, network, consoleInfo) },
                        throws<ContainerCreationFailedException>(withMessage("The environment variable 'SOME_VAR' refers to host environment variable 'SOME_HOST_VARIABLE_THAT_DOES_NOT_EXIST', but it is not set.")))
                }
            }
        }

        given("a container with an additional environment variable that takes its value from the host environment") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val command = null
            val additionalEnvironmentVariables = mapOf("SOME_VAR" to "\$SOME_HOST_VARIABLE")
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, additionalEnvironmentVariables, image, network, consoleInfo)

                it("generates the correct command line, taking the environment variable value from the host environment variables") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        "--env", "SOME_VAR=some value from the host",
                        image.id).asIterable()))
                }
            }
        }

        given("a container with an additional environment variable that takes its value from a host variable that doesn't exist") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val command = null
            val additionalEnvironmentVariables = mapOf("SOME_VAR" to "\$SOME_HOST_VARIABLE_THAT_DOES_NOT_EXIST")
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                it("throws an appropriate exception") {
                    assertThat({ generator.createCommandLine(container, command, additionalEnvironmentVariables, image, network, consoleInfo) },
                        throws<ContainerCreationFailedException>(withMessage("The environment variable 'SOME_VAR' refers to host environment variable 'SOME_HOST_VARIABLE_THAT_DOES_NOT_EXIST', but it is not set.")))
                }
            }
        }

        given("a container with an environment variable that takes its value from a host variable that doesn't exist that is overridden by an additional environment variable with a literal value") {
            val container = Container("the-container", imageSourceDoesNotMatter(), environment = mapOf("SOME_VAR" to "\$SOME_HOST_VARIABLE_THAT_DOES_NOT_EXIST"))
            val command = null
            val additionalEnvironmentVariables = mapOf("SOME_VAR" to "some value from the additional environment variables")
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, additionalEnvironmentVariables, image, network, consoleInfo)

                it("generates the correct command line, taking the environment variable value from the additional environment variables") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        "--env", "SOME_VAR=some value from the additional environment variables",
                        image.id).asIterable()))
                }
            }
        }

        given("the host console does not have the TERM environment variable set") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val command = null
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, emptyMap(), image, network, consoleInfo)

                it("generates the correct command line, not setting the TERM environment variable of the container") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        image.id).asIterable()))
                }
            }
        }

        given("the host console has the TERM environment variable set") {
            val command = null
            val consoleInfo = mock<ConsoleInfo> {
                on { terminalType } doReturn "some-terminal"
            }

            given("and the container configuration does not have a definition for the TERM environment variable") {
                val container = Container("the-container", imageSourceDoesNotMatter())

                on("generating the command") {
                    val commandLine = generator.createCommandLine(container, command, emptyMap(), image, network, consoleInfo)

                    it("generates the correct command line, setting the TERM environment variable of the container to the value of the host") {
                        assertThat(commandLine, equalTo(listOf(
                            "docker", "create",
                            "-it",
                            "--network", network.id,
                            "--hostname", container.name,
                            "--network-alias", container.name,
                            "--env", "TERM=some-terminal",
                            image.id).asIterable()))
                    }
                }
            }

            given("and the container configuration has a definition for the TERM environment variable") {
                val container = Container(
                    "the-container",
                    imageSourceDoesNotMatter(),
                    environment = mapOf("TERM" to "container-terminal"))

                on("generating the command") {
                    val commandLine = generator.createCommandLine(container, command, emptyMap(), image, network, consoleInfo)

                    it("generates the correct command line, setting the TERM environment variable of the container to the value from the container configuration") {
                        assertThat(commandLine, equalTo(listOf(
                            "docker", "create",
                            "-it",
                            "--network", network.id,
                            "--hostname", container.name,
                            "--network-alias", container.name,
                            "--env", "TERM=container-terminal",
                            image.id).asIterable()))
                    }
                }
            }
        }

        given("a container configuration with all optional configuration options specified") {
            val container = Container("the-container",
                imageSourceDoesNotMatter(),
                "the-container-command",
                mapOf("SOME_VAR" to "SOME_VALUE", "OTHER_VAR" to "OTHER_VALUE"),
                "/workingdir",
                setOf(VolumeMount("/local1", "/container1", null), VolumeMount("/local2", "/container2", "ro")),
                setOf(PortMapping(1000, 2000), PortMapping(3000, 4000)),
                healthCheckConfig = HealthCheckConfig("3s", 5, "1.5s"))

            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, "some-command", emptyMap(), image, network, consoleInfo)

                it("generates the correct command line") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        "--env", "SOME_VAR=SOME_VALUE",
                        "--env", "OTHER_VAR=OTHER_VALUE",
                        "--workdir", "/workingdir",
                        "--volume", "/local1:/container1",
                        "--volume", "/local2:/container2:ro",
                        "--publish", "1000:2000",
                        "--publish", "3000:4000",
                        "--health-interval", "3s",
                        "--health-retries", "5",
                        "--health-start-period", "1.5s",
                        image.id,
                        "parsed-some-command").asIterable()))
                }
            }
        }

        given("a container with an override for just the health check interval") {
            val container = Container(
                "the-container",
                imageSourceDoesNotMatter(),
                healthCheckConfig = HealthCheckConfig(interval = "2s")
            )

            val command = "doStuff"
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, emptyMap(), image, network, consoleInfo)

                it("generates the correct command line") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        "--health-interval", "2s",
                        image.id,
                        "parsed-$command").asIterable()))
                }
            }
        }

        given("a container with an override for just the number of health check retries") {
            val container = Container(
                "the-container",
                imageSourceDoesNotMatter(),
                healthCheckConfig = HealthCheckConfig(retries = 2)
            )

            val command = "doStuff"
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, emptyMap(), image, network, consoleInfo)

                it("generates the correct command line") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        "--health-retries", "2",
                        image.id,
                        "parsed-$command").asIterable()))
                }
            }
        }

        given("a container with an override for just the health check start period") {
            val container = Container(
                "the-container",
                imageSourceDoesNotMatter(),
                healthCheckConfig = HealthCheckConfig(startPeriod = "3s")
            )

            val command = "doStuff"
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, emptyMap(), image, network, consoleInfo)

                it("generates the correct command line") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        "--health-start-period", "3s",
                        image.id,
                        "parsed-$command").asIterable()))
                }
            }
        }

        given("a container with an invalid command line") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                it("throws an appropriate exception") {
                    assertThat({ generator.createCommandLine(container, "invalid", emptyMap(), image, network, consoleInfo) },
                        throws<ContainerCreationFailedException>(withMessage("The command line is not valid")))
                }
            }
        }
    }
})
