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

package batect.sockets.unix

import jnr.unixsocket.UnixSocket
import jnr.unixsocket.UnixSocketAddress
import jnr.unixsocket.UnixSocketChannel
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

// Unix sockets implementation inspired by
// https://github.com/gesellix/okhttp/blob/master/samples/simple-client/src/main/java/okhttp3/sample/OkDocker.java and
// https://github.com/square/okhttp/blob/master/samples/unixdomainsockets/src/main/java/okhttp3/unixdomainsockets/UnixDomainSocketFactory.java
class UnixSocketFactory : SocketFactory() {
    override fun createSocket(): Socket {
        val channel = UnixSocketChannel.create()

        return object : UnixSocket(channel) {
            private var connected = false

            override fun connect(addr: SocketAddress?) {
                this.connect(addr, 0)
            }

            override fun connect(addr: SocketAddress?, timeout: Int) {
                val encodedHostName = (addr as InetSocketAddress).hostName
                val socketPath = UnixSocketDns.decodePath(encodedHostName)

                try {
                    super.connect(UnixSocketAddress(socketPath), timeout)
                    connected = true
                } catch (e: IOException) {
                    throw IOException("Cannot connect to '$socketPath': ${e.message}", e)
                }
            }

            override fun close() {
                if (connected) {
                    // This works around https://github.com/jnr/jnr-unixsocket/issues/60, which causes https://github.com/square/okhttp/issues/4233.
                    channel.shutdownInput()
                    channel.shutdownOutput()
                }

                super.close()
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
