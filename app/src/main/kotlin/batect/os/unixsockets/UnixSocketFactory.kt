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

package batect.os.unixsockets

import jnr.unixsocket.UnixSocket
import jnr.unixsocket.UnixSocketAddress
import jnr.unixsocket.UnixSocketChannel
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

class UnixSocketFactory() : SocketFactory() {
    override fun createSocket(): Socket {
        val channel = UnixSocketChannel.create()
        return object : UnixSocket(channel) {
            override fun connect(addr: SocketAddress?) {
                this.connect(addr, 0)
            }

            override fun connect(addr: SocketAddress?, timeout: Int) {
                val encodedHostName = (addr as InetSocketAddress).hostName
                val socketPath = UnixSocketDns.decodePath(encodedHostName)

                try {
                    super.connect(UnixSocketAddress(socketPath), timeout as Int?)
                } catch (e: IOException) {
                    throw IOException("Cannot connect to '$socketPath'.", e)
                }
            }
        }
    }

    override fun createSocket(host: String?, port: Int): Socket {
        throw UnsupportedOperationException()
    }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
        throw UnsupportedOperationException()
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        throw UnsupportedOperationException()
    }

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
        throw UnsupportedOperationException()
    }
}
