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

package batect.docker.build.buildkit.services

import batect.docker.build.ImageBuildOutputSink
import batect.docker.pull.PasswordRegistryCredentials
import batect.docker.pull.RegistryCredentialsProvider
import batect.docker.pull.TokenRegistryCredentials
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import moby.filesync.v1.CredentialsRequest
import moby.filesync.v1.CredentialsResponse
import moby.filesync.v1.GetTokenAuthorityRequest
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object AuthServiceSpec : Spek({
    describe("a authentication service") {
        val credentialsProvider by createForEachTest { mock<RegistryCredentialsProvider>() }
        val outputSink by createForEachTest { ImageBuildOutputSink(null) }
        val logger by createLoggerForEachTestWithoutCustomSerializers()
        val service by createForEachTest { AuthService(credentialsProvider, outputSink, logger) }

        describe("when getting the token authority") {
            it("throws an unimplemented exception") {
                assertThat({ service.GetTokenAuthority(GetTokenAuthorityRequest()) }, throws<UnsupportedGrpcMethodException>())
            }
        }

        describe("when getting credentials") {
            given("the request is not for Docker Hub") {
                val registry = "us-central1-docker.pkg.dev"

                given("there are credentials available for the registry") {
                    given("the credentials are a username and password") {
                        beforeEachTest { whenever(credentialsProvider.getCredentials(registry)).doReturn(PasswordRegistryCredentials("my-username", "my-password", registry)) }

                        val response by createForEachTest { service.Credentials(CredentialsRequest(registry)) }

                        it("returns the username and password for the registry") {
                            assertThat(response, equalTo(CredentialsResponse(Username = "my-username", Secret = "my-password")))
                        }

                        it("prints a message indicating that credentials were accessed") {
                            assertThat(outputSink.outputSoFar, equalTo("## [auth] daemon requested credentials for us-central1-docker.pkg.dev\n"))
                        }
                    }

                    given("the credentials are a token") {
                        beforeEachTest { whenever(credentialsProvider.getCredentials(registry)).doReturn(TokenRegistryCredentials("my-token", registry)) }

                        val response by createForEachTest { service.Credentials(CredentialsRequest(registry)) }

                        it("returns the token for the registry") {
                            assertThat(response, equalTo(CredentialsResponse(Secret = "my-token")))
                        }

                        it("prints a message indicating that credentials were accessed") {
                            assertThat(outputSink.outputSoFar, equalTo("## [auth] daemon requested credentials for us-central1-docker.pkg.dev\n"))
                        }
                    }
                }

                given("there are no credentials available for the registry") {
                    beforeEachTest { whenever(credentialsProvider.getCredentials(registry)).doReturn(null) }

                    val response by createForEachTest { service.Credentials(CredentialsRequest(registry)) }

                    it("returns an empty response") {
                        assertThat(response, equalTo(CredentialsResponse()))
                    }

                    it("prints a message indicating that credentials were accessed") {
                        assertThat(outputSink.outputSoFar, equalTo("## [auth] daemon requested credentials for us-central1-docker.pkg.dev, but none are available\n"))
                    }
                }
            }

            given("the request is for Docker Hub") {
                val registry = "registry-1.docker.io"

                beforeEachTest { whenever(credentialsProvider.getCredentials("https://index.docker.io/v1/")).doReturn(TokenRegistryCredentials("my-docker-token", registry)) }

                val response by createForEachTest { service.Credentials(CredentialsRequest(registry)) }

                it("returns the credentials for the 'https://index.docker.io/v1/' registry") {
                    assertThat(response, equalTo(CredentialsResponse(Secret = "my-docker-token")))
                }

                it("prints a message indicating that credentials were accessed") {
                    assertThat(outputSink.outputSoFar, equalTo("## [auth] daemon requested credentials for registry-1.docker.io\n"))
                }
            }
        }
    }
})
