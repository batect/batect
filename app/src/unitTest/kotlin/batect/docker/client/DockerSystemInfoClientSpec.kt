/*
   Copyright 2017-2019 Charles Korn.

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

package batect.docker.client

import batect.docker.DockerException
import batect.docker.DockerVersionInfo
import batect.docker.api.SystemInfoAPI
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.utils.Version
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.IOException

object DockerSystemInfoClientSpec : Spek({
    describe("a Docker system info client") {
        val api by createForEachTest { mock<SystemInfoAPI>() }
        val logger by createLoggerForEachTest()
        val client by createForEachTest { DockerSystemInfoClient(api, logger) }

        describe("getting Docker version information") {
            on("the Docker version command invocation succeeding") {
                val versionInfo = DockerVersionInfo(Version(17, 4, 0), "1.27", "1.12", "deadbee")

                beforeEachTest { whenever(api.getServerVersionInfo()).doReturn(versionInfo) }

                it("returns the version information from Docker") {
                    assertThat(client.getDockerVersionInfo(), equalTo(DockerVersionInfoRetrievalResult.Succeeded(versionInfo)))
                }
            }

            on("running the Docker version command throwing an exception (for example, because Docker is not installed)") {
                beforeEachTest { whenever(api.getServerVersionInfo()).doThrow(RuntimeException("Something went wrong")) }

                it("returns an appropriate message") {
                    assertThat(client.getDockerVersionInfo(), equalTo(DockerVersionInfoRetrievalResult.Failed("Could not get Docker version information because RuntimeException was thrown: Something went wrong")))
                }
            }
        }

        describe("checking connectivity to the Docker daemon") {
            given("pinging the daemon succeeds") {
                given("getting daemon version info succeeds") {
                    given("the daemon reports an API version that is greater than required") {
                        beforeEachTest {
                            whenever(api.getServerVersionInfo()).thenReturn(DockerVersionInfo(Version(1, 2, 3), "1.38", "xxx", "xxx"))
                        }

                        it("returns success") {
                            assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Succeeded))
                        }
                    }

                    given("the daemon reports an API version that is exactly the required version") {
                        beforeEachTest {
                            whenever(api.getServerVersionInfo()).thenReturn(DockerVersionInfo(Version(1, 2, 3), "1.37", "xxx", "xxx"))
                        }

                        it("returns success") {
                            assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Succeeded))
                        }
                    }

                    given("the daemon reports an API version that is lower than required") {
                        beforeEachTest {
                            whenever(api.getServerVersionInfo()).thenReturn(DockerVersionInfo(Version(1, 2, 3), "1.36", "xxx", "xxx"))
                        }

                        it("returns failure") {
                            assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Failed("batect requires Docker 18.03.1 or later, but version 1.2.3 is installed.")))
                        }
                    }
                }

                given("getting daemon version info fails") {
                    beforeEachTest {
                        whenever(api.getServerVersionInfo()).doThrow(DockerException("Something went wrong."))
                    }

                    it("returns failure") {
                        assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Failed("Something went wrong.")))
                    }
                }
            }

            given("pinging the daemon fails with a general Docker exception") {
                beforeEachTest {
                    whenever(api.ping()).doThrow(DockerException("Something went wrong."))
                }

                it("returns failure") {
                    assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Failed("Something went wrong.")))
                }
            }

            given("pinging the daemon fails due to an I/O issue") {
                beforeEachTest {
                    whenever(api.ping()).doAnswer { throw IOException("Something went wrong.") }
                }

                it("returns failure") {
                    assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Failed("Something went wrong.")))
                }
            }
        }
    }
})
