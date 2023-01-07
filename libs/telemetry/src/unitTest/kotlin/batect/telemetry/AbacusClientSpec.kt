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

package batect.telemetry

import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.mock
import batect.testutils.withCause
import batect.testutils.withMessage
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.IOException
import java.time.Duration

object AbacusClientSpec : Spek({
    describe("an Abacus client") {
        val httpClient by createForEachTest { mock<OkHttpClient>() }
        val logger by createLoggerForEachTestWithoutCustomSerializers()
        val client by createForEachTest { AbacusClient(httpClient, logger) }

        describe("uploading a session") {
            val uploadUrl = "https://api.abacus.batect.dev/v1/sessions"
            val session = "the session as json"
            val sessionBytes = session.toByteArray(Charsets.UTF_8)

            setOf(200, 304).forEach { statusCode ->
                given("the service returns a HTTP $statusCode response") {
                    beforeEachTest {
                        httpClient.mock("PUT", uploadUrl, "", statusCode)

                        client.upload(sessionBytes)
                    }

                    it("sets the content type to JSON") {
                        verify(httpClient).newCall(argThat { body!!.contentType().toString() == "application/json" })
                    }

                    it("uploads the provided session in the request body") {
                        verify(httpClient).newCall(requestWithBody(session))
                    }
                }
            }

            given("a timeout is provided") {
                val httpClientWithTimeout by createForEachTest { mock<OkHttpClient>() }
                val httpClientBuilder by createForEachTest {
                    mock<OkHttpClient.Builder> {
                        on { build() } doReturn httpClientWithTimeout
                        on { callTimeout(any()) } doReturn mock
                    }
                }

                beforeEachTest {
                    whenever(httpClient.newBuilder()).doReturn(httpClientBuilder)
                    httpClientWithTimeout.mock("PUT", uploadUrl, "", 200)

                    client.upload(sessionBytes, Duration.ofSeconds(5))
                }

                it("sets the timeout to the provided value") {
                    verify(httpClientBuilder).callTimeout(Duration.ofSeconds(5))
                }

                it("sets the content type to JSON") {
                    verify(httpClientWithTimeout).newCall(argThat { body!!.contentType().toString() == "application/json" })
                }

                it("uploads the provided session in the request body") {
                    verify(httpClientWithTimeout).newCall(requestWithBody(session))
                }
            }

            given("the service returns a non-successful response code") {
                beforeEachTest {
                    httpClient.mock("PUT", uploadUrl, "", 418)
                }

                it("throws an appropriate exception") {
                    assertThat({ client.upload(sessionBytes) }, throws<AbacusClientException>(withMessage("HTTP PUT $uploadUrl failed: The server returned HTTP 418.")))
                }
            }

            given("the call to the service fails with an exception") {
                val exception = IOException("Something went wrong.")

                beforeEachTest {
                    whenever(httpClient.newCall(any())).then {
                        mock<Call> {
                            on { execute() } doThrow exception
                        }
                    }
                }

                it("throws an appropriate exception") {
                    assertThat(
                        { client.upload(sessionBytes) },
                        throws<AbacusClientException>(
                            withMessage("HTTP PUT $uploadUrl failed: Something went wrong.")
                                and withCause(exception),
                        ),
                    )
                }
            }
        }
    }
})

internal fun requestWithBody(expectedBody: String) = org.mockito.kotlin.check<Request> { request ->
    val buffer = Buffer()
    request.body!!.writeTo(buffer)
    assertThat(buffer.readString(Charsets.UTF_8), equalTo(expectedBody))
}
