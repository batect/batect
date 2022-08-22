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
import batect.dockerclient.DockerClientConfiguration
import batect.dockerclient.DockerClientTLSConfiguration
import batect.dockerclient.TLSVerification
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import okio.Path.Companion.toOkioPath
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths

object DockerClientConfigurationFactorySpec : Spek({
    describe("a Docker client configuration factory") {
        val configDirectory by createForEachTest { Paths.get(".", "src", "unitTest", "resources", "docker-config").toAbsolutePath() }
        val dockerHost = "some://docker/daemon.sock"

        given("TLS is disabled") {
            given("the Docker configuration directory exists") {
                val commandLineOptions by createForEachTest { CommandLineOptions(dockerHost = dockerHost, dockerConfigDirectory = configDirectory) }
                val factory by createForEachTest { DockerClientConfigurationFactory(commandLineOptions) }

                it("configures the client to use that directory") {
                    assertThat(
                        factory.createConfiguration(),
                        equalTo(
                            DockerClientConfiguration(
                                dockerHost,
                                null,
                                TLSVerification.Enabled,
                                configDirectory.toOkioPath()
                            )
                        )
                    )
                }
            }

            given("the Docker configuration directory does not exist") {
                val nonExistentConfigDirectory by createForEachTest { Paths.get(".", "does", "not", "exist").toAbsolutePath() }
                val commandLineOptions by createForEachTest { CommandLineOptions(dockerHost = dockerHost, dockerConfigDirectory = nonExistentConfigDirectory) }
                val factory by createForEachTest { DockerClientConfigurationFactory(commandLineOptions) }

                it("configures the client to use the default directory") {
                    assertThat(
                        factory.createConfiguration(),
                        equalTo(
                            DockerClientConfiguration(
                                dockerHost,
                                null,
                                TLSVerification.Enabled,
                                null
                            )
                        )
                    )
                }
            }
        }

        given("TLS is enabled") {
            val caCertPath by createForEachTest { configDirectory.resolve("ca.pem") }
            val certPath by createForEachTest { configDirectory.resolve("cert.pem") }
            val keyPath by createForEachTest { configDirectory.resolve("key.pem") }

            val expectedCACertContents = "This is a dummy CA certificate used for testing.\n".toByteArray(Charsets.UTF_8)
            val expectedCertContents = "This is a dummy client certificate used for testing.\n".toByteArray(Charsets.UTF_8)
            val expectedKeyContents = "This is a dummy client key used for testing.\n".toByteArray(Charsets.UTF_8)

            given("server identity verification is enabled") {
                val commandLineOptions by createForEachTest {
                    CommandLineOptions(
                        dockerHost = dockerHost,
                        dockerConfigDirectory = configDirectory,
                        dockerUseTLS = true,
                        dockerVerifyTLS = true,
                        dockerTlsCACertificatePath = caCertPath,
                        dockerTLSCertificatePath = certPath,
                        dockerTLSKeyPath = keyPath
                    )
                }

                val factory by createForEachTest { DockerClientConfigurationFactory(commandLineOptions) }

                it("configures the client to perform server identity verification and use the provided certificates") {
                    assertThat(
                        factory.createConfiguration(),
                        equalTo(
                            DockerClientConfiguration(
                                dockerHost,
                                DockerClientTLSConfiguration(expectedCACertContents, expectedCertContents, expectedKeyContents),
                                TLSVerification.Enabled,
                                configDirectory.toOkioPath()
                            )
                        )
                    )
                }
            }

            given("server identity verification is disabled") {
                val commandLineOptions by createForEachTest {
                    CommandLineOptions(
                        dockerHost = dockerHost,
                        dockerConfigDirectory = configDirectory,
                        dockerUseTLS = true,
                        dockerVerifyTLS = false,
                        dockerTlsCACertificatePath = caCertPath,
                        dockerTLSCertificatePath = certPath,
                        dockerTLSKeyPath = keyPath
                    )
                }

                val factory by createForEachTest { DockerClientConfigurationFactory(commandLineOptions) }

                it("configures the client to not perform server identity verification") {
                    assertThat(
                        factory.createConfiguration(),
                        equalTo(
                            DockerClientConfiguration(
                                dockerHost,
                                DockerClientTLSConfiguration(expectedCACertContents, expectedCertContents, expectedKeyContents),
                                TLSVerification.InsecureDisabled,
                                configDirectory.toOkioPath()
                            )
                        )
                    )
                }
            }
        }
    }
})
