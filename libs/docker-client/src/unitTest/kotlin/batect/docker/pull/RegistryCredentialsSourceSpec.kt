/*
   Copyright 2017-2021 Charles Korn.

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

package batect.docker.pull

import batect.docker.DockerRegistryCredentialsException
import batect.os.ExecutableDoesNotExistException
import batect.os.ProcessOutput
import batect.os.ProcessRunner
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.runNullableForEachTest
import batect.testutils.withCause
import batect.testutils.withMessage
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RegistryCredentialsSourceSpec : Spek({
    describe("a basic credentials source") {
        given("the encoded credentials are empty") {
            val credentialsSource = BasicCredentialsSource("", "someserver.com")

            on("loading the credentials") {
                it("throws an appropriate exception") {
                    assertThat({ credentialsSource.load() }, throws<DockerRegistryCredentialsException>(withMessage("Credentials for 'someserver.com' are empty.")))
                }
            }
        }

        given("the encoded credentials are invalid") {
            val credentialsSource = BasicCredentialsSource("###", "someserver.com")

            on("loading the credentials") {
                it("throws an appropriate exception") {
                    assertThat({ credentialsSource.load() }, throws<DockerRegistryCredentialsException>(withMessage("Could not decode credentials for 'someserver.com'.")))
                }
            }
        }

        given("the encoded credentials do not contain a colon") {
            val credentialsSource = BasicCredentialsSource("c29tZXVzZXI=", "someserver.com")

            on("loading the credentials") {
                it("throws an appropriate exception") {
                    assertThat({ credentialsSource.load() }, throws<DockerRegistryCredentialsException>(withMessage("Decoded credentials for 'someserver.com' are not valid.")))
                }
            }
        }

        given("the encoded credentials are for a username and password") {
            val credentialsSource = BasicCredentialsSource("c29tZXVzZXI6c29tZXBhc3M=", "someserver.com")

            on("loading the credentials") {
                val credentials by runForEachTest { credentialsSource.load() }

                it("returns the decoded username and password") {
                    assertThat(credentials, equalTo(PasswordRegistryCredentials("someuser", "somepass", "someserver.com")))
                }
            }
        }

        given("the encoded credentials are for a token") {
            val credentialsSource = BasicCredentialsSource("PHRva2VuPjpzb21ldG9rZW4=", "someserver.com")

            on("loading the credentials") {
                val credentials by runForEachTest { credentialsSource.load() }

                it("returns the decoded username and password") {
                    assertThat(credentials, equalTo(TokenRegistryCredentials("sometoken", "someserver.com")))
                }
            }
        }
    }

    describe("a helper-based credentials source") {
        val processRunner by createForEachTest { mock<ProcessRunner>() }
        val credentialsSource by createForEachTest { HelperBasedCredentialsSource("docker-credentials-secretstore", "someserver.com", processRunner) }

        fun wheneverTheCredentialHelperIsInvoked() = whenever(processRunner.runAndCaptureOutput(listOf("docker-credentials-secretstore", "get"), "someserver.com"))

        given("the credential helper does not exist or is not installed") {
            val executableDoesNotExistException = ExecutableDoesNotExistException("docker-credentials-secretstore", null)

            beforeEachTest {
                wheneverTheCredentialHelperIsInvoked().thenThrow(executableDoesNotExistException)
            }

            on("loading the credentials") {
                it("throws an appropriate exception") {
                    assertThat(
                        { credentialsSource.load() },
                        throws<DockerRegistryCredentialsException>(
                            withMessage("Could not load credentials for 'someserver.com' because the credential helper executable 'docker-credentials-secretstore' does not exist.") and
                                withCause(executableDoesNotExistException)
                        )
                    )
                }
            }
        }

        given("the credential helper returns a username and password") {
            beforeEachTest {
                wheneverTheCredentialHelperIsInvoked().thenReturn(
                    ProcessOutput(
                        0,
                        """
                            |{
                            |   "ServerURL": "someotherserver.com",
                            |   "Username": "someuser",
                            |   "Secret": "somepass"
                            |}
                        """.trimMargin()
                    )
                )
            }

            on("loading the credentials") {
                val credentials by runNullableForEachTest { credentialsSource.load() }

                it("returns those credentials") {
                    assertThat(credentials, equalTo(PasswordRegistryCredentials("someuser", "somepass", "someotherserver.com")))
                }
            }
        }

        given("the credential helper returns a token") {
            beforeEachTest {
                wheneverTheCredentialHelperIsInvoked().thenReturn(
                    ProcessOutput(
                        0,
                        """
                            |{
                            |   "ServerURL": "someotherserver.com",
                            |   "Username": "<token>",
                            |   "Secret": "sometoken"
                            |}
                        """.trimMargin()
                    )
                )
            }

            on("loading the credentials") {
                val credentials by runNullableForEachTest { credentialsSource.load() }

                it("returns those credentials") {
                    assertThat(credentials, equalTo(TokenRegistryCredentials("sometoken", "someotherserver.com")))
                }
            }
        }

        given("the credential helper returns a GCP-style token") {
            beforeEachTest {
                wheneverTheCredentialHelperIsInvoked().thenReturn(
                    ProcessOutput(
                        0,
                        """
                            |{
                            |   "Username": "_dcgcloud_token",
                            |   "Secret": "sometoken"
                            |}
                        """.trimMargin()
                    )
                )
            }

            on("loading the credentials") {
                val credentials by runNullableForEachTest { credentialsSource.load() }

                it("returns those credentials") {
                    assertThat(credentials, equalTo(PasswordRegistryCredentials("_dcgcloud_token", "sometoken", "someserver.com")))
                }
            }
        }

        given("the credential helper returns a response without a server address") {
            beforeEachTest {
                wheneverTheCredentialHelperIsInvoked().thenReturn(
                    ProcessOutput(
                        0,
                        """
                            |{
                            |   "Username": "someuser",
                            |   "Secret": "somepass"
                            |}
                        """.trimMargin()
                    )
                )
            }

            on("loading the credentials") {
                it("throws an appropriate exception") {
                    assertThat(
                        { credentialsSource.load() },
                        throws<DockerRegistryCredentialsException>(
                            withMessage("The credentials returned for 'someserver.com' by the credential helper executable 'docker-credentials-secretstore' are invalid: there is no 'ServerURL' field.")
                        )
                    )
                }
            }
        }

        given("the credential helper returns a response without a username") {
            beforeEachTest {
                wheneverTheCredentialHelperIsInvoked().thenReturn(
                    ProcessOutput(
                        0,
                        """
                            |{
                            |   "ServerURL": "someotherserver.com",
                            |   "Secret": "somepass"
                            |}
                        """.trimMargin()
                    )
                )
            }

            on("loading the credentials") {
                it("throws an appropriate exception") {
                    assertThat(
                        { credentialsSource.load() },
                        throws<DockerRegistryCredentialsException>(
                            withMessage("The credentials returned for 'someserver.com' by the credential helper executable 'docker-credentials-secretstore' are invalid: there is no 'Username' field.")
                        )
                    )
                }
            }
        }

        given("the credential helper returns a response without a secret") {
            beforeEachTest {
                wheneverTheCredentialHelperIsInvoked().thenReturn(
                    ProcessOutput(
                        0,
                        """
                            |{
                            |   "ServerURL": "someotherserver.com",
                            |   "Username": "someuser"
                            |}
                        """.trimMargin()
                    )
                )
            }

            on("loading the credentials") {
                it("throws an appropriate exception") {
                    assertThat(
                        { credentialsSource.load() },
                        throws<DockerRegistryCredentialsException>(
                            withMessage("The credentials returned for 'someserver.com' by the credential helper executable 'docker-credentials-secretstore' are invalid: there is no 'Secret' field.")
                        )
                    )
                }
            }
        }

        given("the credential helper returns a response that is not valid JSON") {
            beforeEachTest {
                wheneverTheCredentialHelperIsInvoked().thenReturn(ProcessOutput(0, "{]"))
            }

            on("loading the credentials") {
                it("throws an appropriate exception") {
                    assertThat(
                        { credentialsSource.load() },
                        throws<DockerRegistryCredentialsException>(
                            withMessage("The credentials returned for 'someserver.com' by the credential helper executable 'docker-credentials-secretstore' are invalid: Unexpected JSON token at offset 1: Expected end of the object '}', but had 'EOF' instead\nJSON input: {]")
                        )
                    )
                }
            }
        }

        given("the credential helper returns that the credentials cannot be found") {
            beforeEachTest {
                // This error message is fixed and is defined in https://github.com/docker/docker-credential-helpers/blob/master/credentials/error.go
                wheneverTheCredentialHelperIsInvoked().thenReturn(ProcessOutput(1, "credentials not found in native keychain\n"))
            }

            on("loading the credentials") {
                val credentials by runNullableForEachTest { credentialsSource.load() }

                // If the credentials can't be found, we continue with no credentials.
                // This is the behaviour of the Docker client, so we have to mirror that.
                it("returns no credentials") {
                    assertThat(credentials, absent())
                }
            }
        }

        given("the credential helper returns another error") {
            beforeEachTest {
                wheneverTheCredentialHelperIsInvoked().thenReturn(ProcessOutput(1, "Something went wrong.\n"))
            }

            on("loading the credentials") {
                it("throws an appropriate exception") {
                    assertThat(
                        { credentialsSource.load() },
                        throws<DockerRegistryCredentialsException>(
                            withMessage("Could not load credentials for 'someserver.com' because the credential helper executable 'docker-credentials-secretstore' exited with code 1 and output: Something went wrong.")
                        )
                    )
                }
            }
        }
    }
})
