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

package batect.docker

import batect.docker.client.DockerSystemInfoClient
import batect.docker.client.DockerVersionInfoRetrievalResult
import batect.os.OperatingSystem
import batect.os.SystemInfo
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.utils.Version
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DockerHostNameResolverSpec : Spek({
    describe("a Docker host name resolver") {
        val systemInfo by createForEachTest { mock<SystemInfo>() }
        val dockerSystemInfoClient by createForEachTest { mock<DockerSystemInfoClient>() }
        val resolver by createForEachTest { DockerHostNameResolver(systemInfo, dockerSystemInfoClient) }

        given("the local system is running OS X") {
            beforeEachTest { whenever(systemInfo.operatingSystem).doReturn(OperatingSystem.Mac) }

            on("the Docker version being less than 17.06") {
                beforeEachTest {
                    whenever(dockerSystemInfoClient.getDockerVersionInfo()).doReturn(
                        DockerVersionInfoRetrievalResult.Succeeded(
                            createDockerVersionInfoWithServerVersion(Version(17, 5, 0))
                        )
                    )
                }

                val result by runForEachTest { resolver.resolveNameOfDockerHost() }

                it("returns that getting the Docker host's name is not supported") {
                    assertThat(result, equalTo(DockerHostNameResolutionResult.NotSupported))
                }
            }

            data class Scenario(val dockerVersion: Version, val expectedHostName: String, val description: String)

            listOf(
                Scenario(Version(17, 6, 0), "docker.for.mac.localhost", "the Docker version being 17.06"),
                Scenario(Version(17, 6, 0, "mac1"), "docker.for.mac.localhost", "the Docker version being above 17.06 but less than 17.12"),
                Scenario(Version(17, 12, 0), "docker.for.mac.host.internal", "the Docker version being 17.12"),
                Scenario(Version(17, 12, 0, "mac2"), "docker.for.mac.host.internal", "the Docker version being above 17.12 but less than 18.03"),
                Scenario(Version(18, 3, 0), "host.docker.internal", "the Docker version being 18.03"),
                Scenario(Version(18, 3, 0, "mac3"), "host.docker.internal", "the Docker version being above 18.03")
            ).forEach { (dockerVersion, expectedHostName, description) ->
                on(description) {
                    beforeEachTest {
                        whenever(dockerSystemInfoClient.getDockerVersionInfo()).doReturn(
                            DockerVersionInfoRetrievalResult.Succeeded(
                                createDockerVersionInfoWithServerVersion(dockerVersion)
                            )
                        )
                    }

                    val result by runForEachTest { resolver.resolveNameOfDockerHost() }

                    it("returns that getting the Docker host's name is '$expectedHostName'") {
                        assertThat(result, equalTo(DockerHostNameResolutionResult.Resolved(expectedHostName)))
                    }
                }
            }

            on("the Docker version not being able to be determined") {
                beforeEachTest {
                    whenever(dockerSystemInfoClient.getDockerVersionInfo()).doReturn(DockerVersionInfoRetrievalResult.Failed("Couldn't get version."))
                }

                val result by runForEachTest { resolver.resolveNameOfDockerHost() }

                it("returns that getting the Docker host's name is not supported") {
                    assertThat(result, equalTo(DockerHostNameResolutionResult.NotSupported))
                }
            }
        }

        given("the local system is running Windows") {
            beforeEachTest { whenever(systemInfo.operatingSystem).doReturn(OperatingSystem.Windows) }

            on("the Docker version being less than 17.06") {
                beforeEachTest {
                    whenever(dockerSystemInfoClient.getDockerVersionInfo()).doReturn(
                        DockerVersionInfoRetrievalResult.Succeeded(
                            createDockerVersionInfoWithServerVersion(Version(17, 5, 0))
                        )
                    )
                }

                val result by runForEachTest { resolver.resolveNameOfDockerHost() }

                it("returns that getting the Docker host's name is not supported") {
                    assertThat(result, equalTo(DockerHostNameResolutionResult.NotSupported))
                }
            }

            data class Scenario(val dockerVersion: Version, val expectedHostName: String, val description: String)

            listOf(
                Scenario(Version(17, 6, 0), "docker.for.win.localhost", "the Docker version being 17.06"),
                Scenario(Version(17, 6, 0, "windows1"), "docker.for.win.localhost", "the Docker version being above 17.06 but less than 17.12"),
                Scenario(Version(17, 12, 0), "docker.for.win.host.internal", "the Docker version being 17.12"),
                Scenario(Version(17, 12, 0, "windows2"), "docker.for.win.host.internal", "the Docker version being above 17.12 but less than 18.03"),
                Scenario(Version(18, 3, 0), "host.docker.internal", "the Docker version being 18.03"),
                Scenario(Version(18, 3, 0, "windows3"), "host.docker.internal", "the Docker version being above 18.03")
            ).forEach { (dockerVersion, expectedHostName, description) ->
                on(description) {
                    beforeEachTest {
                        whenever(dockerSystemInfoClient.getDockerVersionInfo()).doReturn(
                            DockerVersionInfoRetrievalResult.Succeeded(
                                createDockerVersionInfoWithServerVersion(dockerVersion)
                            )
                        )
                    }

                    val result by runForEachTest { resolver.resolveNameOfDockerHost() }

                    it("returns that getting the Docker host's name is '$expectedHostName'") {
                        assertThat(result, equalTo(DockerHostNameResolutionResult.Resolved(expectedHostName)))
                    }
                }
            }

            on("the Docker version not being able to be determined") {
                beforeEachTest {
                    whenever(dockerSystemInfoClient.getDockerVersionInfo()).doReturn(DockerVersionInfoRetrievalResult.Failed("Couldn't get version."))
                }

                val result by runForEachTest { resolver.resolveNameOfDockerHost() }

                it("returns that getting the Docker host's name is not supported") {
                    assertThat(result, equalTo(DockerHostNameResolutionResult.NotSupported))
                }
            }
        }

        on("the local system is running Linux") {
            beforeEachTest {
                whenever(systemInfo.operatingSystem).thenReturn(OperatingSystem.Linux)
            }

            val result by runForEachTest { resolver.resolveNameOfDockerHost() }

            it("returns that getting the Docker host's name is not supported") {
                assertThat(result, equalTo(DockerHostNameResolutionResult.NotSupported))
            }
        }

        on("the local system is running another operating system") {
            beforeEachTest {
                whenever(systemInfo.operatingSystem).thenReturn(OperatingSystem.Other)
            }

            val result by runForEachTest { resolver.resolveNameOfDockerHost() }

            it("returns that getting the Docker host's name is not supported") {
                assertThat(result, equalTo(DockerHostNameResolutionResult.NotSupported))
            }
        }
    }
})

private fun createDockerVersionInfoWithServerVersion(version: Version) = DockerVersionInfo(version, "", "", "", "")
