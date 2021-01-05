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

import batect.docker.DockerHttpConfig
import batect.docker.DockerNetwork
import batect.os.SystemInfo
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.mockDelete
import batect.testutils.mockGet
import batect.testutils.mockPost
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.TimeUnit

object NetworksAPISpec : Spek({
    describe("a Docker networks API client") {
        val dockerHost = "the-docker-daemon"
        val dockerBaseUrl = "http://$dockerHost"

        val clientWithLongTimeout by createForEachTest { mock<OkHttpClient>() }
        val longTimeoutClientBuilder by createForEachTest {
            mock<OkHttpClient.Builder> { mock ->
                on { readTimeout(any(), any()) } doReturn mock
                on { build() } doReturn clientWithLongTimeout
            }
        }

        val httpClient by createForEachTest {
            mock<OkHttpClient> {
                on { newBuilder() } doReturn longTimeoutClientBuilder
            }
        }

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
        val api by createForEachTest { NetworksAPI(httpConfig, systemInfo, logger) }

        val errorResponse = """{"message": "Something went wrong.\nMore details on next line."}"""
        val errorMessageWithCorrectLineEndings = "Something went wrong.SYSTEM_LINE_SEPARATORMore details on next line."

        describe("creating a network") {
            val expectedUrl = "$dockerBaseUrl/v1.37/networks/create"

            on("a successful creation") {
                val call by createForEachTest { clientWithLongTimeout.mockPost(expectedUrl, """{"Id": "the-network-ID"}""", 201) }
                val result by runForEachTest { api.create("the-network-name", "the-driver") }

                it("creates the network") {
                    verify(call).execute()
                }

                it("creates the network with the expected settings") {
                    verify(clientWithLongTimeout).newCall(
                        requestWithJsonBody { body ->
                            assertThat(body.getValue("Name").jsonPrimitive.content, equalTo("the-network-name"))
                            assertThat(body.getValue("CheckDuplicate").jsonPrimitive.boolean, equalTo(true))
                            assertThat(body.getValue("Driver").jsonPrimitive.content, equalTo("the-driver"))
                        }
                    )
                }

                it("returns the ID of the created network") {
                    assertThat(result.id, equalTo("the-network-ID"))
                }

                it("configures the HTTP client with a longer timeout to allow for the network to be created") {
                    verify(longTimeoutClientBuilder).readTimeout(30, TimeUnit.SECONDS)
                }
            }

            on("an unsuccessful creation") {
                beforeEachTest { clientWithLongTimeout.mockPost(expectedUrl, errorResponse, 418) }

                it("throws an appropriate exception") {
                    assertThat({ api.create("the-network-name", "the-driver") }, throws<NetworkCreationFailedException>(withMessage("Creation of network failed: $errorMessageWithCorrectLineEndings")))
                }
            }
        }

        describe("deleting a network") {
            given("an existing network") {
                val network = DockerNetwork("abc123")
                val expectedUrl = "$dockerBaseUrl/v1.37/networks/abc123"

                on("a successful deletion") {
                    val call by createForEachTest { httpClient.mockDelete(expectedUrl, "", 204) }
                    beforeEachTest { api.delete(network) }

                    it("sends a request to the Docker daemon to delete the network") {
                        verify(call).execute()
                    }
                }

                on("an unsuccessful deletion") {
                    beforeEachTest { httpClient.mockDelete(expectedUrl, errorResponse, 418) }

                    it("throws an appropriate exception") {
                        assertThat({ api.delete(network) }, throws<NetworkDeletionFailedException>(withMessage("Deletion of network 'abc123' failed: $errorMessageWithCorrectLineEndings")))
                    }
                }
            }
        }

        describe("getting a network by name or ID") {
            val identifier = "abc123"
            val expectedUrl = "$dockerBaseUrl/v1.37/networks/abc123"

            given("the network exists") {
                beforeEachTest { httpClient.mockGet(expectedUrl, """{"Id": "7d86d31b1478e7cca9ebed7e73aa0fdeec46c5ca29497431d3007d2d9e15ed99"}""") }
                val network by createForEachTest { api.getByNameOrId(identifier) }

                it("returns the ID of the network") {
                    assertThat(network, equalTo(DockerNetwork("7d86d31b1478e7cca9ebed7e73aa0fdeec46c5ca29497431d3007d2d9e15ed99")))
                }
            }

            given("the network does not exist") {
                beforeEachTest { httpClient.mockGet(expectedUrl, errorResponse, 404) }

                it("throws an appropriate exception") {
                    assertThat({ api.getByNameOrId(identifier) }, throws<NetworkDoesNotExistException>(withMessage("The network 'abc123' does not exist.")))
                }
            }

            given("getting the networks fails") {
                beforeEachTest { httpClient.mockGet(expectedUrl, errorResponse, 418) }

                it("throws an appropriate exception") {
                    assertThat({ api.getByNameOrId(identifier) }, throws<NetworkInspectionFailedException>(withMessage("Getting details of network 'abc123' failed: $errorMessageWithCorrectLineEndings")))
                }
            }
        }
    }
})
