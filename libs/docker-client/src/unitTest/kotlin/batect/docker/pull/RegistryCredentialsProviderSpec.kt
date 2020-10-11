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

package batect.docker.pull

import batect.docker.ImageReference
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runNullableForEachTest
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RegistryCredentialsProviderSpec : Spek({
    describe("a Docker registry credentials provider") {
        // Why are we mocking this type instead of just constructing one? It's critical that we use the registryIndex value,
        // not registryDomain, but these are usually the same, so mocking helps make sure that we're using the right value.
        val imageRef = mock<ImageReference> {
            on { registryIndex } doReturn "https://somedomain.com/index"
        }

        val configurationFile by createForEachTest { mock<RegistryCredentialsConfigurationFile>() }
        val provider by createForEachTest { RegistryCredentialsProvider(configurationFile) }

        describe("getting credentials for pulling an image") {
            given("the configuration file has a credentials source for the registry") {
                val source by createForEachTest { mock<RegistryCredentialsSource>() }

                beforeEachTest {
                    whenever(configurationFile.getCredentialsForRegistry("https://somedomain.com/index")).thenReturn(source)
                }

                given("the credentials source returns some credentials") {
                    val credentialsFromSource = mock<RegistryCredentials>()

                    beforeEachTest {
                        whenever(source.load()).thenReturn(credentialsFromSource)
                    }

                    on("getting the credentials") {
                        val credentials by runNullableForEachTest { provider.getCredentials(imageRef) }

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
                        val credentials by runNullableForEachTest { provider.getCredentials(imageRef) }

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
                    val credentials by runNullableForEachTest { provider.getCredentials(imageRef) }

                    it("returns no credentials") {
                        assertThat(credentials, absent())
                    }
                }
            }
        }
    }
})
