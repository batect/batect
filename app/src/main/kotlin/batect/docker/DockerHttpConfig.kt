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

package batect.docker

import batect.os.unixsockets.UnixSocketDns
import batect.os.unixsockets.UnixSocketFactory
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.net.Proxy

class DockerHttpConfig(baseClient: OkHttpClient) {
    val client: OkHttpClient = baseClient.newBuilder()
        .proxy(Proxy.NO_PROXY)
        .socketFactory(UnixSocketFactory())
        .dns(UnixSocketDns())
        .build()

    val baseUrl: HttpUrl = HttpUrl.Builder()
        .scheme("http")
        .host(UnixSocketDns.encodePath("/var/run/docker.sock"))
        .build()
}
