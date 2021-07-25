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
import batect.docker.DockerVolume
import batect.os.SystemInfo
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.mockDelete
import batect.testutils.mockGet
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object VolumesAPISpec : Spek({
    describe("a Docker volumes API client") {
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
        val api by createForEachTest { VolumesAPI(httpConfig, systemInfo, logger) }

        val errorResponse = """{"message": "Something went wrong.\nMore details on next line."}"""
        val errorMessageWithCorrectLineEndings = "Something went wrong.SYSTEM_LINE_SEPARATORMore details on next line."

        describe("listing all volumes") {
            val expectedUrl = "$dockerBaseUrl/v1.37/volumes"

            on("the request succeeding") {
                val responseBody = """
                    {
                      "Volumes": [
                        {
                          "CreatedAt": "2020-02-28T10:14:37Z",
                          "Driver": "local",
                          "Labels": null,
                          "Mountpoint": "/var/lib/docker/volumes/batect-cache-8bkmyf-gradle-cache/_data",
                          "Name": "batect-cache-8bkmyf-gradle-cache",
                          "Options": null,
                          "Scope": "local"
                        },
                        {
                          "CreatedAt": "2020-02-29T02:24:17Z",
                          "Driver": "local",
                          "Labels": null,
                          "Mountpoint": "/var/lib/docker/volumes/batect-cache-rmjxix-bin/_data",
                          "Name": "batect-cache-rmjxix-bin",
                          "Options": null,
                          "Scope": "local"
                        }
                      ],
                      "Warnings": null
                    }
                """.trimIndent()

                beforeEachTest { httpClient.mockGet(expectedUrl, responseBody, 200) }
                val response by runForEachTest { api.getAll() }

                val expectedVolumes = setOf(
                    DockerVolume("batect-cache-8bkmyf-gradle-cache"),
                    DockerVolume("batect-cache-rmjxix-bin")
                )

                it("returns a list of all volumes") {
                    assertThat(response, equalTo(expectedVolumes))
                }
            }

            on("the request failing") {
                beforeEachTest { httpClient.mockGet(expectedUrl, errorResponse, 418) }

                it("throws an appropriate exception") {
                    assertThat({ api.getAll() }, throws<GetAllVolumesFailedException>(withMessage("Getting all volumes failed: $errorMessageWithCorrectLineEndings")))
                }
            }
        }

        describe("deleting a volume") {
            given("an existing volume") {
                val volume = DockerVolume("abc123")
                val expectedUrl = "$dockerBaseUrl/v1.37/volumes/abc123"

                on("a successful deletion") {
                    val call by createForEachTest { httpClient.mockDelete(expectedUrl, "", 204) }
                    beforeEachTest { api.delete(volume) }

                    it("sends a request to the Docker daemon to delete the volume") {
                        verify(call).execute()
                    }
                }

                on("an unsuccessful deletion") {
                    beforeEachTest { httpClient.mockDelete(expectedUrl, errorResponse, 418) }

                    it("throws an appropriate exception") {
                        assertThat({ api.delete(volume) }, throws<VolumeDeletionFailedException>(withMessage("Deletion of volume 'abc123' failed: $errorMessageWithCorrectLineEndings")))
                    }
                }
            }
        }
    }
})
