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

package batect.execution

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.IOException

object CancellationExtensionsSpec : Spek({
    describe("executing a OkHttp call in a cancellation context") {
        val cancellationContextCleanup by createForEachTest { mock<AutoCloseable>() }
        val cancellationContext by createForEachTest {
            mock<CancellationContext> {
                on { addCancellationCallback(any()) } doReturn cancellationContextCleanup
            }
        }

        given("the request is not cancelled") {
            given("the request succeeds") {
                val call by createForEachTest { createTestCall() }

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
                val call by createForEachTest { createTestCall() }

                beforeEachTest {
                    whenever(cancellationContext.addCancellationCallback(any())).doAnswer { invocation ->
                        @Suppress("UNCHECKED_CAST")
                        val cancellationCallback = invocation.arguments[0] as CancellationCallback

                        cancellationCallback.invoke()

                        AutoCloseable { }
                    }
                }

                it("throws an exception that indicates that the call failed") {
                    assertThat({
                        call.executeInCancellationContext(cancellationContext) { throw RuntimeException("This should not be reached") }
                    }, throws<CancellationException>())
                }
            }

            given("the request is cancelled after the response headers are received") {
                val call by createForEachTest { createTestCall("/bytes/20000") }

                it("throws an exception that indicates that the call failed") {
                    assertThat({
                        call.executeInCancellationContext(cancellationContext) { response ->
                            call.cancel()
                            response.body()!!.bytes()

                            throw RuntimeException("This should not be reached, reading the body above should trigger an exception from OkHttp because the call is cancelled")
                        }
                    }, throws<CancellationException>())
                }
            }
        }
    }
})

private fun createTestCall(path: String = "/get"): Call = OkHttpClient().newCall(Request.Builder().get().url("https://httpbin.org$path").build())
