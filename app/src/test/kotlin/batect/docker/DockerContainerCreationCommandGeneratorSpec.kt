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

import batect.config.HealthCheckConfig
import batect.config.PortMapping
import batect.config.VolumeMount
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DockerContainerCreationCommandGeneratorSpec : Spek({
    describe("a Docker container creation command generator") {
        val generator = DockerContainerCreationCommandGenerator()
        val image = DockerImage("the-image")
        val network = DockerNetwork("the-network")

        given("a creation request with the minimal set of information") {
            val request = DockerContainerCreationRequest(image, network, emptyList(), "the-hostname", "the-alias", emptyMap(), null, emptySet(), emptySet(), HealthCheckConfig())

            on("generating the command") {
                val commandLine = generator.createCommandLine(request)

                it("generates the correct command line") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", "the-hostname",
                        "--network-alias", "the-alias",
                        image.id).asIterable()))
                }
            }
        }

        given("a creation request with an explicit command") {
            val request = DockerContainerCreationRequest(image, network, listOf("doStuff"), "the-hostname", "the-alias", emptyMap(), null, emptySet(), emptySet(), HealthCheckConfig())

            on("generating the command") {
                val commandLine = generator.createCommandLine(request)

                it("generates the correct command line") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", "the-hostname",
                        "--network-alias", "the-alias",
                        image.id,
                        "doStuff").asIterable()))
                }
            }
        }

        given("a creation request with all optional configuration options specified") {
            val request = DockerContainerCreationRequest(
                image,
                network,
                listOf("doStuff"),
                "the-hostname",
                "the-alias",
                mapOf("SOME_VAR" to "SOME_VALUE", "OTHER_VAR" to "OTHER_VALUE"),
                "/workingdir",
                setOf(VolumeMount("/local1", "/container1", null), VolumeMount("/local2", "/container2", "ro")),
                setOf(PortMapping(1000, 2000), PortMapping(3000, 4000)),
                HealthCheckConfig("3s", 5, "1.5s")
            )

            on("generating the command") {
                val commandLine = generator.createCommandLine(request)

                it("generates the correct command line") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", "the-hostname",
                        "--network-alias", "the-alias",
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
                        "doStuff").asIterable()))
                }
            }
        }

        given("a creation request with an override for just the health check interval") {
            val request = DockerContainerCreationRequest(image, network, emptyList(), "the-hostname", "the-alias", emptyMap(), null, emptySet(), emptySet(), HealthCheckConfig(interval = "2s"))

            on("generating the command") {
                val commandLine = generator.createCommandLine(request)

                it("generates the correct command line") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", "the-hostname",
                        "--network-alias", "the-alias",
                        "--health-interval", "2s",
                        image.id).asIterable()))
                }
            }
        }

        given("a creation request with an override for just the number of health check retries") {
            val request = DockerContainerCreationRequest(image, network, emptyList(), "the-hostname", "the-alias", emptyMap(), null, emptySet(), emptySet(), HealthCheckConfig(retries = 2))

            on("generating the command") {
                val commandLine = generator.createCommandLine(request)

                it("generates the correct command line") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", "the-hostname",
                        "--network-alias", "the-alias",
                        "--health-retries", "2",
                        image.id).asIterable()))
                }
            }
        }

        given("a creation request with an override for just the health check start period") {
            val request = DockerContainerCreationRequest(image, network, emptyList(), "the-hostname", "the-alias", emptyMap(), null, emptySet(), emptySet(), HealthCheckConfig(startPeriod = "3s"))

            on("generating the command") {
                val commandLine = generator.createCommandLine(request)

                it("generates the correct command line") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", "the-hostname",
                        "--network-alias", "the-alias",
                        "--health-start-period", "3s",
                        image.id).asIterable()))
                }
            }
        }
    }
})
