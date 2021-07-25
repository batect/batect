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

package batect.docker.api

import batect.docker.DockerException
import batect.docker.DockerHttpConfig
import batect.docker.DockerVersionInfo
import batect.os.SystemInfo
import batect.primitives.Version
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.mockGet
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SystemInfoAPISpec : Spek({
    describe("a Docker system info API client") {
        val dockerHost = "the-docker-daemon"
        val dockerBaseUrl = "http://$dockerHost"
        val httpClient by createForEachTest { mock<OkHttpClient>() }
        val httpConfig by createForEachTest {
            mock<DockerHttpConfig> {
                on { client } doReturn httpClient
                on { baseUrl } doReturn dockerBaseUrl.toHttpUrl()
            }
        }

        val systemInfo by createForEachTest {
            mock<SystemInfo> {
                on { lineSeparator } doReturn "SYSTEM_LINE_SEPARATOR"
            }
        }

        val logger by createLoggerForEachTestWithoutCustomSerializers()
        val api by createForEachTest { SystemInfoAPI(httpConfig, systemInfo, logger) }

        val errorResponse = """{"message": "Something went wrong.\nMore details on next line."}"""
        val errorMessageWithCorrectLineEndings = "Something went wrong.SYSTEM_LINE_SEPARATORMore details on next line."

        describe("getting server version information") {
            val expectedUrl = "$dockerBaseUrl/v1.37/version"

            given("the Docker version command invocation succeeds") {
                given("the response indicates the daemon has experimental features enabled") {
                    beforeEachTest {
                        httpClient.mockGet(
                            expectedUrl,
                            """
                                |{
                                |  "Version": "17.04.0",
                                |  "Os": "my_cool_os",
                                |  "KernelVersion": "3.19.0-23-generic",
                                |  "GoVersion": "go1.7.5",
                                |  "GitCommit": "deadbee",
                                |  "Arch": "amd64",
                                |  "ApiVersion": "1.27",
                                |  "MinAPIVersion": "1.12",
                                |  "BuildTime": "2016-06-14T07:09:13.444803460+00:00",
                                |  "Experimental": true
                                |}
                            """.trimMargin(),
                            200
                        )
                    }

                    it("returns the version information from Docker") {
                        assertThat(
                            api.getServerVersionInfo(),
                            equalTo(
                                DockerVersionInfo(
                                    Version(17, 4, 0),
                                    "1.27",
                                    "1.12",
                                    "deadbee",
                                    "my_cool_os",
                                    true
                                )
                            )
                        )
                    }
                }

                given("the response does not include information about whether the daemon is running in experimental mode") {
                    beforeEachTest {
                        httpClient.mockGet(
                            expectedUrl,
                            """
                                |{
                                |  "Version": "17.04.0",
                                |  "Os": "my_cool_os",
                                |  "KernelVersion": "3.19.0-23-generic",
                                |  "GoVersion": "go1.7.5",
                                |  "GitCommit": "deadbee",
                                |  "Arch": "amd64",
                                |  "ApiVersion": "1.27",
                                |  "MinAPIVersion": "1.12",
                                |  "BuildTime": "2016-06-14T07:09:13.444803460+00:00"
                                |}
                            """.trimMargin(),
                            200
                        )
                    }

                    it("returns the version information from Docker, and indicates that the daemon is not running in experimental mode") {
                        assertThat(
                            api.getServerVersionInfo(),
                            equalTo(
                                DockerVersionInfo(
                                    Version(17, 4, 0),
                                    "1.27",
                                    "1.12",
                                    "deadbee",
                                    "my_cool_os",
                                    false
                                )
                            )
                        )
                    }
                }
            }

            on("the Docker version command invocation failing") {
                beforeEachTest { httpClient.mockGet(expectedUrl, errorResponse, 418) }

                it("throws an appropriate exception") {
                    assertThat({ api.getServerVersionInfo() }, throws<DockerVersionInfoRetrievalException>(withMessage("The request failed: $errorMessageWithCorrectLineEndings")))
                }
            }
        }

        describe("pinging the server") {
            val expectedUrl = "$dockerBaseUrl/_ping"

            given("the ping succeeds") {
                given("the response does not include builder version information") {
                    beforeEachTest { httpClient.mockGet(expectedUrl, "OK") }

                    val response by runForEachTest { api.ping() }

                    it("returns a response with the legacy builder type") {
                        assertThat(response.builderVersion, equalTo(BuilderVersion.Legacy))
                    }
                }

                given("the response indicates that the daemon supports BuildKit") {
                    val headers = Headers.Builder().add("Builder-Version", "2").build()
                    beforeEachTest { httpClient.mockGet(expectedUrl, "OK", responseHeaders = headers) }

                    val response by runForEachTest { api.ping() }

                    it("returns a response with the BuildKit builder type") {
                        assertThat(response.builderVersion, equalTo(BuilderVersion.BuildKit))
                    }
                }

                given("the response indicates that the daemon does not support BuildKit") {
                    val headers = Headers.Builder().add("Builder-Version", "1").build()
                    beforeEachTest { httpClient.mockGet(expectedUrl, "OK", responseHeaders = headers) }

                    val response by runForEachTest { api.ping() }

                    it("returns a response with the legacy builder type") {
                        assertThat(response.builderVersion, equalTo(BuilderVersion.Legacy))
                    }
                }

                given("the response includes an unknown builder version") {
                    val headers = Headers.Builder().add("Builder-Version", "3").build()
                    beforeEachTest { httpClient.mockGet(expectedUrl, "OK", responseHeaders = headers) }

                    it("throws an appropriate exception") {
                        assertThat({ api.ping() }, throws<DockerException>(withMessage("Docker daemon responded with unknown Builder-Version '3'.")))
                    }
                }

                // See https://github.com/batect/batect/issues/895
                given("the response includes an empty builder version") {
                    val headers = Headers.Builder().add("Builder-Version", "").build()
                    beforeEachTest { httpClient.mockGet(expectedUrl, "OK", responseHeaders = headers) }

                    val response by runForEachTest { api.ping() }

                    it("returns a response with the legacy builder type") {
                        assertThat(response.builderVersion, equalTo(BuilderVersion.Legacy))
                    }
                }
            }

            given("the ping fails") {
                beforeEachTest { httpClient.mockGet(expectedUrl, errorResponse, 418) }

                it("throws an appropriate exception") {
                    assertThat({ api.ping() }, throws<DockerException>(withMessage("Could not ping Docker daemon, daemon responded with HTTP 418: $errorMessageWithCorrectLineEndings")))
                }
            }

            given("the ping returns an unexpected response") {
                beforeEachTest { httpClient.mockGet(expectedUrl, "Something went wrong.", 200) }

                it("throws an appropriate exception") {
                    assertThat({ api.ping() }, throws<DockerException>(withMessage("Could not ping Docker daemon, daemon responded with HTTP 200: Something went wrong.")))
                }
            }
        }
    }
})
