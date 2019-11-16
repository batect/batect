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

package batect.docker.api

import batect.docker.DockerHttpConfig
import batect.docker.DockerNetwork
import batect.os.SystemInfo
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.mockDelete
import batect.testutils.mockPost
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.content
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object NetworksAPISpec : Spek({
    describe("a Docker networks API client") {
        val dockerHost = "the-docker-daemon"
        val dockerBaseUrl = "http://$dockerHost"
        val httpClient by createForEachTest { mock<OkHttpClient>() }
        val httpConfig by createForEachTest {
            mock<DockerHttpConfig> {
                on { client } doReturn httpClient
                on { baseUrl } doReturn HttpUrl.get(dockerBaseUrl)
            }
        }

        val systemInfo by createForEachTest {
            mock<SystemInfo> {
                on { lineSeparator } doReturn "SYSTEM_LINE_SEPARATOR"
            }
        }

        val logger by createLoggerForEachTest()
        val api by createForEachTest { NetworksAPI(httpConfig, systemInfo, logger) }

        val errorResponse = """{"message": "Something went wrong.\nMore details on next line."}"""
        val errorMessageWithCorrectLineEndings = "Something went wrong.SYSTEM_LINE_SEPARATORMore details on next line."

        describe("creating a network") {
            val expectedUrl = "$dockerBaseUrl/v1.35/networks/create"

            on("a successful creation") {
                val call by createForEachTest { httpClient.mockPost(expectedUrl, """{"Id": "the-network-ID"}""", 201) }
                val result by runForEachTest { api.create() }

                it("creates the network") {
                    verify(call).execute()
                }

                it("creates the network with the expected settings") {
                    verify(httpClient).newCall(requestWithJsonBody { body ->
                        assertThat(body.getValue("Name").content, isUUID)
                        assertThat(body.getValue("CheckDuplicate").boolean, equalTo(true))
                        assertThat(body.getValue("Driver").content, equalTo("bridge"))
                    })
                }

                it("returns the ID of the created network") {
                    assertThat(result.id, equalTo("the-network-ID"))
                }
            }

            on("an unsuccessful creation") {
                beforeEachTest { httpClient.mockPost(expectedUrl, errorResponse, 418) }

                it("throws an appropriate exception") {
                    assertThat({ api.create() }, throws<NetworkCreationFailedException>(withMessage("Creation of network failed: $errorMessageWithCorrectLineEndings")))
                }
            }
        }

        describe("deleting a network") {
            given("an existing network") {
                val network = DockerNetwork("abc123")
                val expectedUrl = "$dockerBaseUrl/v1.35/networks/abc123"

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
    }
})
