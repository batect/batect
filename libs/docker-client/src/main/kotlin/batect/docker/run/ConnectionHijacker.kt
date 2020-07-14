/*
   Copyright 2017-2020 Charles Korn.

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

import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.connection.RealConnection
import okio.BufferedSink
import okio.BufferedSource

class ConnectionHijacker : Interceptor {
    var source: BufferedSource? = null
    var sink: BufferedSink? = null

    // HACK: There's no clean way to get the underlying streams for a connection with OkHttp, so we
    // have to reach into the connection and get them. This could change in a future version of OkHttp.
    // Unfortunately, the web sockets version of the Docker attach API is broken on Mac (see
    // https://github.com/docker/for-mac/issues/1662), so this is the only option.
    override fun intercept(chain: Interceptor.Chain): Response {
        val connection = chain.connection() as RealConnection

        sink = connection.sink
        source = connection.source

        return chain.proceed(chain.request())
    }
}

private val RealConnection.sink: BufferedSink
    get() {
        val property = RealConnection::class.declaredMemberProperties.single { it.name == "sink" }
        property.isAccessible = true
        return property.get(this) as BufferedSink
    }

private val RealConnection.source: BufferedSource
    get() {
        val property = RealConnection::class.declaredMemberProperties.single { it.name == "source" }
        property.isAccessible = true
        return property.get(this) as BufferedSource
    }
