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

package batect.docker.run

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.connection.RealConnection
import okio.BufferedSink
import okio.BufferedSource

class ConnectionHijacker : Interceptor {
    var source: BufferedSource? = null
    var sink: BufferedSink? = null

    // HACK: There's no clean way to get the underlying streams for a connection with OkHttp, but
    // calling newWebSocketStreams returns the raw underlying streams (with nothing WebSocket-specific
    // attached to them). This could change in a future version of OkHttp.
    override fun intercept(chain: Interceptor.Chain): Response {
        val connection = chain.connection() as RealConnection
        val streams = connection.newWebSocketStreams(connection.allocations.single().get())

        sink = streams.sink
        source = streams.source

        return chain.proceed(chain.request())
    }
}
