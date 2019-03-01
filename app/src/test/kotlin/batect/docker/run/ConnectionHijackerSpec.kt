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

package batect.docker.run

import batect.testutils.equalTo
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.connection.RealConnection
import okhttp3.internal.connection.StreamAllocation
import okhttp3.internal.ws.RealWebSocket
import okio.BufferedSink
import okio.BufferedSource
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.lang.ref.WeakReference

object ConnectionHijackerSpec : Spek({
    describe("a connection hijacker") {
        val hijacker = ConnectionHijacker()

        on("being invoked") {
            val source = mock<BufferedSource>()
            val sink = mock<BufferedSink>()
            val connection = createConnectionWithStreams(source, sink)
            val request = mock<Request>()
            val expectedResponse = mock<Response>()

            val chain = mock<Interceptor.Chain> {
                on { connection() } doReturn connection
                on { request() } doReturn request
                on { proceed(request) } doReturn expectedResponse
            }

            val returnedResponse = hijacker.intercept(chain)

            it("captures the source from the connection") {
                assertThat(hijacker.source, equalTo(source))
            }

            it("captures the sink from the connection") {
                assertThat(hijacker.sink, equalTo(sink))
            }

            it("proceeds with processing the request") {
                assertThat(returnedResponse, equalTo(expectedResponse))
            }
        }
    }
})

private fun createConnectionWithStreams(source: BufferedSource, sink: BufferedSink): Connection {
    // Why do we go to all this effort? We can't mock RealConnection.allocations as it is a Java field, and if we
    // don't have something in the list, the tests fail (because intercept() can't call single() on an empty array).
    val streamAllocation = mock<StreamAllocation>()
    val innerConnection = RealConnection(null, null)
    innerConnection.allocations.add(WeakReference(streamAllocation))

    return mock<RealConnection>(spiedInstance = innerConnection) {
        on { newWebSocketStreams(streamAllocation) } doReturn object : RealWebSocket.Streams(true, source, sink) {
            override fun close() = throw UnsupportedOperationException()
        }
    }
}
