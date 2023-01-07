/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.updates

import batect.primitives.Version
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.mockGet
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withCause
import batect.testutils.withMessage
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.throws
import okhttp3.Call
import okhttp3.OkHttpClient
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.IOException
import java.time.ZoneOffset
import java.time.ZonedDateTime

object UpdateInfoDownloaderSpec : Spek({
    describe("an update information downloader") {
        val downloadUrl = "https://updates.batect.dev/v1/latest"
        val client by createForEachTest { mock<OkHttpClient>() }

        val logger by createLoggerForEachTest()
        val dateTime = ZonedDateTime.of(2017, 10, 3, 11, 2, 0, 0, ZoneOffset.UTC)
        val dateTimeProvider = { dateTime }
        val downloader by createForEachTest { UpdateInfoDownloader(client, logger, dateTimeProvider) }

        on("when the latest release information can be retrieved successfully and includes known wrapper script information") {
            beforeEachTest {
                val responseBody = """{
                      "url": "https://github.com/batect/batect/releases/tag/0.3",
                      "version": "0.3",
                      "files": [
                        {
                          "name": "batect",
                          "url": "https://github.com/batect/batect/releases/download/0.3/batect"
                        },
                        {
                          "name": "batect.cmd",
                          "url": "https://github.com/batect/batect/releases/download/0.3/batect.cmd"
                        }
                      ]
                  }
                """.trimIndent()

                client.mockGet(downloadUrl, responseBody)
            }

            val updateInfo by runForEachTest { downloader.getLatestVersionInfo() }

            it("returns the version number of the latest version") {
                assertThat(updateInfo.version, equalTo(Version(0, 3, 0)))
            }

            it("returns the URL of the latest version") {
                assertThat(updateInfo.url, equalTo("https://github.com/batect/batect/releases/tag/0.3"))
            }

            it("returns the current date and time as the 'last updated' time") {
                assertThat(updateInfo.lastUpdated, equalTo(dateTime))
            }

            it("returns the script information") {
                assertThat(
                    updateInfo.scripts,
                    equalTo(
                        listOf(
                            ScriptInfo("batect", "https://github.com/batect/batect/releases/download/0.3/batect"),
                            ScriptInfo("batect.cmd", "https://github.com/batect/batect/releases/download/0.3/batect.cmd"),
                        ),
                    ),
                )
            }
        }

        on("when the latest release information does not include any assets") {
            beforeEachTest {
                val responseBody = """{
                      "url": "https://github.com/batect/batect/releases/tag/0.3",
                      "version": "0.3",
                      "files": []
                  }
                """.trimIndent()

                client.mockGet(downloadUrl, responseBody)
            }

            val updateInfo by runForEachTest { downloader.getLatestVersionInfo() }

            it("returns no script information") {
                assertThat(updateInfo.scripts, isEmpty)
            }
        }

        on("when the latest release information does not contain an asset with a known script name") {
            beforeEachTest {
                val responseBody = """{
                      "url": "https://github.com/batect/batect/releases/tag/0.3",
                      "version": "0.3",
                      "files": [
                        {
                          "name": "batect.jar",
                          "url": "https://github.com/batect/batect/releases/download/0.3/batect.jar"
                        }
                      ]
                  }
                """.trimIndent()

                client.mockGet(downloadUrl, responseBody)
            }

            val updateInfo by runForEachTest { downloader.getLatestVersionInfo() }

            it("returns no script information") {
                assertThat(updateInfo.scripts, isEmpty)
            }
        }

        on("when the latest release information endpoint returns a HTTP error") {
            beforeEachTest { client.mockGet(downloadUrl, "", 404) }

            it("throws an appropriate exception") {
                assertThat(
                    { downloader.getLatestVersionInfo() },
                    throws(withMessage("Could not download latest release information from https://updates.batect.dev/v1/latest: The server returned HTTP 404.")),
                )
            }
        }

        on("when getting the latest release information fails") {
            val exception = IOException("Could not do what you asked because stuff happened.")

            beforeEachTest {
                whenever(client.newCall(any())).then {
                    mock<Call> {
                        on { execute() } doThrow exception
                    }
                }
            }

            it("throws an appropriate exception") {
                assertThat(
                    { downloader.getLatestVersionInfo() },
                    throws(
                        withMessage("Could not download latest release information from https://updates.batect.dev/v1/latest: Could not do what you asked because stuff happened.")
                            and withCause(exception),
                    ),
                )
            }
        }
    }
})
