/*
    Copyright 2017-2022 Charles Korn.

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

import batect.testutils.createForEachTest
import batect.testutils.doesNotThrow
import batect.testutils.given
import batect.testutils.withMessage
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files

object DockerTLSConfigSpec : Spek({
    describe("when TLS is enabled") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val certificatesDirectory by createForEachTest { Files.createDirectory(fileSystem.getPath("/certs/")) }
        val caCertificatePath by createForEachTest { certificatesDirectory.resolve("ca-cert") }
        val clientCertificatePath by createForEachTest { certificatesDirectory.resolve("client-cert") }
        val clientKeyPath by createForEachTest { certificatesDirectory.resolve("client-key") }

        given("all files exist") {
            beforeEachTest {
                Files.createFile(caCertificatePath)
                Files.createFile(clientCertificatePath)
                Files.createFile(clientKeyPath)
            }

            it("does not throw an exception") {
                assertThat(
                    { DockerTLSConfig.EnableTLS(true, caCertificatePath, clientCertificatePath, clientKeyPath) },
                    doesNotThrow()
                )
            }
        }

        given("the CA certificates file does not exist") {
            beforeEachTest {
                Files.createFile(clientCertificatePath)
                Files.createFile(clientKeyPath)
            }

            it("throws an appropriate exception") {
                assertThat(
                    { DockerTLSConfig.EnableTLS(true, caCertificatePath, clientCertificatePath, clientKeyPath) },
                    throws<InvalidDockerTLSConfigurationException>(withMessage("The CA certificate file '/certs/ca-cert' does not exist."))
                )
            }
        }

        given("the client certificate file does not exist") {
            beforeEachTest {
                Files.createFile(caCertificatePath)
                Files.createFile(clientKeyPath)
            }

            it("throws an appropriate exception") {
                assertThat(
                    { DockerTLSConfig.EnableTLS(true, caCertificatePath, clientCertificatePath, clientKeyPath) },
                    throws<InvalidDockerTLSConfigurationException>(withMessage("The client certificate file '/certs/client-cert' does not exist."))
                )
            }
        }

        given("the client key file does not exist") {
            beforeEachTest {
                Files.createFile(caCertificatePath)
                Files.createFile(clientCertificatePath)
            }

            it("throws an appropriate exception") {
                assertThat(
                    { DockerTLSConfig.EnableTLS(true, caCertificatePath, clientCertificatePath, clientKeyPath) },
                    throws<InvalidDockerTLSConfigurationException>(withMessage("The client key file '/certs/client-key' does not exist."))
                )
            }
        }
    }
})
