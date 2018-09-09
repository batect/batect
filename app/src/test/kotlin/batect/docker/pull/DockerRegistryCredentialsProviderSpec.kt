/*
   Copyright 2017-2018 Charles Korn.

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

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DockerRegistryCredentialsProviderSpec : Spek({
    describe("a Docker registry credentials provider") {
        val domainResolver = mock<DockerRegistryDomainResolver> {
            on { resolveDomainForImage("some-image") } doReturn "somedomain.com"
        }

        val indexResolver = mock<DockerRegistryIndexResolver> {
            on { resolveRegistryIndex("somedomain.com") } doReturn "https://somedomain.com/index"
        }

        val configurationFile by createForEachTest { mock<DockerRegistryCredentialsConfigurationFile>() }
        val provider by createForEachTest { DockerRegistryCredentialsProvider(domainResolver, indexResolver, configurationFile) }

        describe("getting credentials for pulling an image") {
            given("the configuration file has a credentials source for the registry") {
                val source by createForEachTest { mock<DockerRegistryCredentialsSource>() }

                beforeEachTest {
                    whenever(configurationFile.getCredentialsForRegistry("https://somedomain.com/index")).thenReturn(source)
                }

                given("the credentials source returns some credentials") {
                    val credentialsFromSource = mock<DockerRegistryCredentials>()

                    beforeEachTest {
                        whenever(source.load()).thenReturn(credentialsFromSource)
                    }

                    on("getting the credentials") {
                        val credentials = provider.getCredentials("some-image")

                        it("returns the credentials from the source") {
                            assertThat(credentials, equalTo(credentialsFromSource))
                        }
                    }
                }

                given("the credentials source does not return some credentials") {
                    beforeEachTest {
                        whenever(source.load()).thenReturn(null)
                    }

                    on("getting the credentials") {
                        val credentials = provider.getCredentials("some-image")

                        it("returns no credentials") {
                            assertThat(credentials, absent())
                        }
                    }
                }
            }

            given("the configuration file does not have a credentials source for the registry") {
                beforeEachTest {
                    whenever(configurationFile.getCredentialsForRegistry("https://somedomain.com/index")).thenReturn(null)
                }

                on("getting the credentials") {
                    val credentials = provider.getCredentials("some-image")

                    it("returns no credentials") {
                        assertThat(credentials, absent())
                    }
                }
            }
        }
    }
})
