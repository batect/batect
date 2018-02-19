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

package batect.updates

import batect.logging.Logger
import batect.testutils.InMemoryLogSink
import batect.testutils.createForEachTest
import batect.testutils.withCause
import batect.testutils.withMessage
import batect.utils.Version
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argThat
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.IOException
import java.time.ZoneOffset
import java.time.ZonedDateTime

object UpdateInfoDownloaderSpec : Spek({
    describe("an update information downloader") {
        val expectedUrl = "https://api.github.com/repos/charleskorn/batect/releases/latest"
        val call by createForEachTest { mock<Call>() }

        val client by createForEachTest {
            mock<OkHttpClient> {
                on { newCall(argThat { method() == "GET" && url().equals(expectedUrl) }) } doReturn call
            }
        }

        val logger by createForEachTest { Logger("UpdateInfoDownloader", InMemoryLogSink()) }
        val dateTime = ZonedDateTime.of(2017, 10, 3, 11, 2, 0, 0, ZoneOffset.UTC)
        val dateTimeProvider = { dateTime }
        val downloader by createForEachTest { UpdateInfoDownloader(client, logger, dateTimeProvider) }

        on("when the latest release information can be retrieved successfully") {
            val responseBody = """{
                      "url": "https://api.github.com/repos/charleskorn/batect/releases/7936494",
                      "html_url": "https://github.com/charleskorn/batect/releases/tag/0.3",
                      "id": 7936494,
                      "tag_name": "0.3"
                  }""".trimIndent()

            client.mockGet(expectedUrl, responseBody)

            val updateInfo = downloader.getLatestVersionInfo()

            it("returns the version number of the latest version") {
                assertThat(updateInfo.version, equalTo(Version(0, 3, 0)))
            }

            it("returns the URL of the latest version") {
                assertThat(updateInfo.url, equalTo("https://github.com/charleskorn/batect/releases/tag/0.3"))
            }

            it("returns the current date and time as the 'last updated' time") {
                assertThat(updateInfo.lastUpdated, equalTo(dateTime))
            }
        }

        on("when the latest release information endpoint returns a HTTP error") {
            client.mockGet(expectedUrl, "", 404)

            it("throws an appropriate exception") {
                assertThat({ downloader.getLatestVersionInfo() },
                    throws(withMessage("Could not download latest release information from https://api.github.com/repos/charleskorn/batect/releases/latest: The server returned HTTP 404.")))
            }
        }

        on("when getting the latest release information fails") {
            val exception = IOException("Could not do what you asked because stuff happened.")

            whenever(client.newCall(any())).then {
                mock<Call> {
                    on { execute() } doThrow exception
                }
            }

            it("throws an appropriate exception") {
                assertThat({ downloader.getLatestVersionInfo() },
                    throws(withMessage("Could not download latest release information from https://api.github.com/repos/charleskorn/batect/releases/latest: Could not do what you asked because stuff happened.")
                        and withCause(exception)))
            }
        }
    }
})

fun OkHttpClient.mockGet(url: String, body: String, statusCode: Int = 200) {
    val parsedUrl = HttpUrl.parse(url)
    val responseBody = ResponseBody.create(MediaType.parse("application/json; charset=utf-8"), body)

    whenever(this.newCall(argThat { method() == "GET" && url().equals(parsedUrl) })).then { invocation ->
        val request = invocation.getArgument<Request>(0)

        mock<Call> {
            on { execute() } doAnswer {
                Response.Builder()
                    .request(request)
                    .body(responseBody)
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message("Something happened")
                    .build()
            }
        }
    }
}
