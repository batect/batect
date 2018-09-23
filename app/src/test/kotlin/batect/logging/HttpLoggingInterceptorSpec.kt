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

package batect.logging

import batect.testutils.InMemoryLogSink
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.hasMessage
import batect.testutils.withAdditionalData
import batect.testutils.withLogMessage
import batect.testutils.withSeverity
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object HttpLoggingInterceptorSpec : Spek({
    describe("a HTTP logging interceptor") {
        val logSink by createForEachTest { InMemoryLogSink() }
        val logger by createForEachTest { Logger("http", logSink) }
        val interceptor by createForEachTest { HttpLoggingInterceptor(logger) }

        on("receiving a request with no body that results in a response with no body") {
            val request = Request.Builder()
                .url("http://www.awesomestuff.com/thing")
                .build()

            val expectedResponse = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(418)
                .message("I'm a teapot")
                .build()

            val chain = mock<Interceptor.Chain> {
                on { request() } doReturn request
                on { proceed(request) } doReturn expectedResponse
            }

            val response = interceptor.intercept(chain)

            it("returns the response from the rest of the chain") {
                assertThat(response, equalTo(expectedResponse))
            }

            it("logs details of the request before it starts") {
                assertThat(logSink, hasMessage(
                    withLogMessage("HTTP request starting.") and
                        withSeverity(Severity.Debug) and
                        withAdditionalData("url", "http://www.awesomestuff.com/thing") and
                        withAdditionalData("method", "GET") and
                        withAdditionalData("contentLength", 0) and
                        withAdditionalData("contentType", null)
                ))
            }

            it("logs details of the response after the request finishes") {
                assertThat(logSink, hasMessage(
                    withLogMessage("HTTP response received.") and
                        withSeverity(Severity.Debug) and
                        withAdditionalData("url", "http://www.awesomestuff.com/thing") and
                        withAdditionalData("method", "GET") and
                        withAdditionalData("code", 418) and
                        withAdditionalData("message", "I'm a teapot") and
                        withAdditionalData("contentLength", 0) and
                        withAdditionalData("contentType", null)
                ))
            }
        }

        on("receiving a request with a body") {
            val request = Request.Builder()
                .url("http://www.awesomestuff.com/thing")
                .post(RequestBody.create(MediaType.get("text/plain; charset=utf-8"), "Some body content"))
                .build()

            val expectedResponse = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(418)
                .message("I'm a teapot")
                .build()

            val chain = mock<Interceptor.Chain> {
                on { request() } doReturn request
                on { proceed(request) } doReturn expectedResponse
            }

            interceptor.intercept(chain)

            it("logs details of the request body before it starts") {
                assertThat(logSink, hasMessage(
                    withLogMessage("HTTP request starting.") and
                        withAdditionalData("contentLength", "Some body content".length.toLong()) and
                        withAdditionalData("contentType", "text/plain; charset=utf-8")
                ))
            }
        }

        on("receiving a response with a body") {
            val request = Request.Builder()
                .url("http://www.awesomestuff.com/thing")
                .build()

            val expectedResponse = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(418)
                .message("I'm a teapot")
                .body(ResponseBody.create(MediaType.get("text/plain; charset=utf-8"), "Some body content"))
                .build()

            val chain = mock<Interceptor.Chain> {
                on { request() } doReturn request
                on { proceed(request) } doReturn expectedResponse
            }

            interceptor.intercept(chain)

            it("logs details of the response body") {
                assertThat(logSink, hasMessage(
                    withLogMessage("HTTP response received.") and
                        withAdditionalData("contentLength", "Some body content".length.toLong()) and
                        withAdditionalData("contentType", "text/plain; charset=utf-8")
                ))
            }
        }
    }
})
