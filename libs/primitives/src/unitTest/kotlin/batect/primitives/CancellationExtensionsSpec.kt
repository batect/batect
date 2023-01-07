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

package batect.primitives

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

object CancellationExtensionsSpec : Spek({
    describe("executing a OkHttp call in a cancellation context") {
        val cancellationContextCleanup by createForEachTest { mock<AutoCloseable>() }
        val cancellationContext by createForEachTest {
            mock<CancellationContext> {
                on { addCancellationCallback(any()) } doReturn cancellationContextCleanup
            }
        }

        val testServer by createForEachTest { MockWebServer() }
        afterEachTest { testServer.shutdown() }

        given("the request is not cancelled") {
            given("the request succeeds") {
                beforeEachTest {
                    testServer.enqueue(MockResponse())
                    testServer.startSilently()
                }

                val call by createForEachTest { createTestCall(testServer) }

                val expectedReturnValue = 8

                val result by createForEachTest {
                    call.executeInCancellationContext(cancellationContext) { response ->
                        assertThat(response.isSuccessful, equalTo(true))
                        verify(cancellationContextCleanup, never()).close()

                        expectedReturnValue
                    }
                }

                it("registers the call with the cancellation context") {
                    verify(cancellationContext).addCancellationCallback(call::cancel)
                }

                it("returns the response") {
                    assertThat(result, equalTo(expectedReturnValue))
                }

                it("deregisters the call from the cancellation context after the provided callback is executed") {
                    verify(cancellationContextCleanup).close()
                }
            }

            given("the request fails") {
                val call by createForEachTest { mock<Call>() }
                val exception by createForEachTest { IOException("Something went wrong.") }
                beforeEachTest { whenever(call.execute()).doThrow(exception) }

                it("throws the original exception") {
                    assertThat({ call.executeInCancellationContext(cancellationContext, {}) }, throws<Throwable>(equalTo(exception)))
                }
            }
        }

        given("the request is cancelled") {
            given("the request is cancelled before the response headers are received") {
                beforeEachTest {
                    testServer.enqueue(MockResponse())
                    testServer.startSilently()
                }

                val call by createForEachTest { createTestCall(testServer) }

                beforeEachTest {
                    whenever(cancellationContext.addCancellationCallback(any())).doAnswer { invocation ->
                        val cancellationCallback = invocation.getArgument<CancellationCallback>(0)

                        cancellationCallback.invoke()

                        AutoCloseable { }
                    }
                }

                it("throws an exception that indicates that the call failed") {
                    assertThat(
                        {
                            call.executeInCancellationContext(cancellationContext) { throw RuntimeException("This should not be reached") }
                        },
                        throws<CancellationException>(),
                    )
                }
            }

            given("the request is cancelled after the response headers are received") {
                beforeEachTest {
                    testServer.enqueue(
                        MockResponse().setBody("a".repeat(20000)),
                    )

                    testServer.startSilently()
                }

                val call by createForEachTest { createTestCall(testServer) }

                it("throws an exception that indicates that the call failed") {
                    assertThat(
                        {
                            call.executeInCancellationContext(cancellationContext) { response ->
                                call.cancel()
                                response.body!!.bytes()

                                throw RuntimeException("This should not be reached, reading the body above should trigger an exception from OkHttp because the call is cancelled")
                            }
                        },
                        throws<CancellationException>(),
                    )
                }
            }
        }
    }
})

private fun createTestCall(server: MockWebServer): Call = OkHttpClient().newCall(Request.Builder().get().url(server.url("/")).build())

private fun MockWebServer.startSilently() {
    Logger.getLogger(this::class.java.name).level = Level.WARNING
    start()
}
