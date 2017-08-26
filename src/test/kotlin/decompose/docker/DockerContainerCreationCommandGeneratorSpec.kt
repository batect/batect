package decompose.docker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import decompose.config.Container
import decompose.config.PortMapping
import decompose.config.VolumeMount
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DockerContainerCreationCommandGeneratorSpec : Spek({
    describe("a Docker container creation command generator") {
        val generator = DockerContainerCreationCommandGenerator()

        given("a simple container definition, a built image and an explicit command to run for the task") {
            val container = Container("the-container", "/this/does/not/matter")
            val command = "doStuff"
            val image = DockerImage("the-image")
            val network = DockerNetwork("the-network")

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, image, network)

                it("generates the correct command line, taking the command from the task") {
                    assertThat(commandLine, equalTo(listOf(
                            "docker", "create",
                            "--rm", "-it",
                            "--network", network.id,
                            "--hostname", container.name,
                            "--network-alias", container.name,
                            image.id,
                            command).asIterable()))
                }
            }
        }

        given("a simple container definition, a built image and an explicit command to run for the container") {
            val container = Container("the-container", "/this/does/not/matter", "some-command some-argument")
            val command = null
            val image = DockerImage("the-image")
            val network = DockerNetwork("the-network")

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, image, network)

                it("generates the correct command line, taking the command from the container") {
                    assertThat(commandLine, equalTo(listOf(
                            "docker", "create",
                            "--rm", "-it",
                            "--network", network.id,
                            "--hostname", container.name,
                            "--network-alias", container.name,
                            image.id,
                            "some-command", "some-argument").asIterable()))
                }
            }
        }

        given("a simple container definition, a built image and no explicit command to run for the task or container") {
            val container = Container("the-container", "/this/does/not/matter")
            val image = DockerImage("the-image")
            val network = DockerNetwork("the-network")

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, null, image, network)

                it("generates the correct command line, not specifying an explicit command") {
                    assertThat(commandLine, equalTo(listOf(
                            "docker", "create",
                            "--rm", "-it",
                            "--network", network.id,
                            "--hostname", container.name,
                            "--network-alias", container.name,
                            image.id).asIterable()))
                }
            }
        }

        given("a simple container definition, a built image and an explicit command to run for the task and container") {
            val container = Container("the-container", "/this/does/not/matter", "some-command-from-the-container")
            val command = "some-command-from-the-task"
            val image = DockerImage("the-image")
            val network = DockerNetwork("the-network")

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, image, network)

                it("generates the correct command line, taking the command from the task") {
                    assertThat(commandLine, equalTo(listOf(
                            "docker", "create",
                            "--rm", "-it",
                            "--network", network.id,
                            "--hostname", container.name,
                            "--network-alias", container.name,
                            image.id,
                            command).asIterable()))
                }
            }
        }

        given("a container configuration with all optional configuration options specified") {
            val container = Container("the-container",
                    "/this/does/not/matter",
                    "the-container-command",
                    mapOf("SOME_VAR" to "SOME_VALUE", "OTHER_VAR" to "OTHER_VALUE"),
                    "/workingdir",
                    setOf(VolumeMount("/local1", "/container1"), VolumeMount("/local2", "/container2")),
                    setOf(PortMapping(1000, 2000), PortMapping(3000, 4000)))

            val command = null
            val image = DockerImage("the-image")
            val network = DockerNetwork("the-network")

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, image, network)

                it("generates the correct command line") {
                    assertThat(commandLine, equalTo(listOf(
                            "docker", "create",
                            "--rm", "-it",
                            "--network", network.id,
                            "--hostname", container.name,
                            "--network-alias", container.name,
                            "--env", "SOME_VAR=SOME_VALUE",
                            "--env", "OTHER_VAR=OTHER_VALUE",
                            "--workdir", "/workingdir",
                            "--volume", "/local1:/container1",
                            "--volume", "/local2:/container2",
                            "--publish", "1000:2000",
                            "--publish", "3000:4000",
                            image.id,
                            "the-container-command").asIterable()))
                }
            }
        }

        // FIXME This stuff is hard and there are lots of edge cases. Surely there's a better way...
        // References:
        // - https://www.gnu.org/software/bash/manual/html_node/Quoting.html
        // - http://www.grymoire.com/Unix/Quote.html
        mapOf(
                "echo hello" to listOf("echo", "hello"),
                "echo  hello" to listOf("echo", "hello"),
                """echo "hello world"""" to listOf("echo", "hello world"),
                """echo 'hello world'""" to listOf("echo", "hello world"),
                """echo hello\ world""" to listOf("echo", "hello world"),
                """echo 'hello "world"'""" to listOf("echo", """hello "world""""),
                """echo "hello 'world'"""" to listOf("echo", "hello 'world'"),
                """echo "hello \"world\""""" to listOf("echo", """hello "world""""),
                """echo "hello 'world'"""" to listOf("echo", "hello 'world'"),
                """echo 'hello "world"'""" to listOf("echo", """hello "world""""),
                """echo can\'t""" to listOf("echo", "can't"),
                // This next example comes from http://stackoverflow.com/a/28640859/1668119
                """sh -c 'echo "\"un'\''kno\"wn\$\$\$'\'' with \$\"\$\$. \"zzz\""'""" to listOf("sh", "-c", """echo "\"un'kno\"wn\$\$\$' with \$\"\$\$. \"zzz\""""")
        ).forEach { command, expectedSplit ->
            given("a simple container definition, a built image and the command '$command'") {
                val container = Container("the-container", "/this/does/not/matter")
                val image = DockerImage("the-image")
                val network = DockerNetwork("the-network")

                on("generating the command") {
                    val commandLine = generator.createCommandLine(container, command, image, network)
                    val expectedCommandLine = listOf("docker", "create",
                            "--rm", "-it",
                            "--network", network.id,
                            "--hostname", container.name,
                            "--network-alias", container.name,
                            image.id) + expectedSplit

                    it("generates the correct command line") {
                        assertThat(commandLine, equalTo(expectedCommandLine.asIterable()))
                    }
                }
            }
        }

        mapOf(
                """echo "hello""" to "it contains an unbalanced double quote",
                """echo 'hello""" to "it contains an unbalanced single quote",
                """echo hello\""" to """it ends with a backslash (backslashes always escape the following character, for a literal backslash, use '\\')""",
                """echo "hello\""" to """it ends with a backslash (backslashes always escape the following character, for a literal backslash, use '\\')"""
        ).forEach { command, expectedErrorMessage ->
            given("a simple container definition, a built image and the command '$command'") {
                val container = Container("the-container", "/this/does/not/matter")
                val image = DockerImage("the-image")
                val network = DockerNetwork("the-network")

                on("generating the command") {
                    it("throws an exception with the message '$expectedErrorMessage'") {
                        assertThat({ generator.createCommandLine(container, command, image, network) },
                                throws<ContainerCreationFailedException>(withMessage("Command line `$command` is invalid: $expectedErrorMessage")))
                    }
                }
            }
        }
    }
})
