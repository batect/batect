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

package batect.docker.api

import batect.docker.DockerException
import batect.docker.DockerHttpConfig
import batect.docker.DockerVersionInfo
import batect.os.SystemInfo
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.doesNotThrow
import batect.testutils.equalTo
import batect.testutils.mockGet
import batect.testutils.on
import batect.testutils.withMessage
import batect.Version
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
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

        val logger by createLoggerForEachTest()
        val api by createForEachTest { SystemInfoAPI(httpConfig, systemInfo, logger) }

        val errorResponse = """{"message": "Something went wrong.\nMore details on next line."}"""
        val errorMessageWithCorrectLineEndings = "Something went wrong.SYSTEM_LINE_SEPARATORMore details on next line."

        describe("getting server version information") {
            val expectedUrl = "$dockerBaseUrl/v1.37/version"

            on("the Docker version command invocation succeeding") {
                beforeEachTest {
                    httpClient.mockGet(
                        expectedUrl, """
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
                        |}""".trimMargin(),
                        200
                    )
                }

                it("returns the version information from Docker") {
                    assertThat(
                        api.getServerVersionInfo(), equalTo(
                            DockerVersionInfo(
                                Version(17, 4, 0), "1.27", "1.12", "deadbee", "my_cool_os"
                            )
                        )
                    )
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

            on("the ping succeeding") {
                beforeEachTest { httpClient.mockGet(expectedUrl, "OK") }

                it("does not throw an exception") {
                    assertThat({ api.ping() }, doesNotThrow())
                }
            }

            on("the ping failing") {
                beforeEachTest { httpClient.mockGet(expectedUrl, errorResponse, 418) }

                it("throws an appropriate exception") {
                    assertThat({ api.ping() }, throws<DockerException>(withMessage("Could not ping Docker daemon, daemon responded with HTTP 418: $errorMessageWithCorrectLineEndings")))
                }
            }

            on("the ping returning an unexpected response") {
                beforeEachTest { httpClient.mockGet(expectedUrl, "Something went wrong.", 200) }

                it("throws an appropriate exception") {
                    assertThat({ api.ping() }, throws<DockerException>(withMessage("Could not ping Docker daemon, daemon responded with HTTP 200: Something went wrong.")))
                }
            }
        }
    }
})
