/*
   Copyright 2017-2021 Charles Korn.

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

package batect.sockets.namedpipes

import batect.sockets.DnsToPathResolver
import okhttp3.Dns
import java.net.InetAddress

class NamedPipeDns : Dns {
    override fun lookup(hostname: String): MutableList<InetAddress> {
        if (!hostname.endsWith(encodingMarker)) {
            throw IllegalArgumentException("Host name '$hostname' was not encoded for use with ${this::class.simpleName}.")
        }

        return mutableListOf(InetAddress.getByAddress(hostname, byteArrayOf(0, 0, 0, 0)))
    }

    companion object : DnsToPathResolver(".namedpipe", NamedPipeDns::class.simpleName!!)
}
