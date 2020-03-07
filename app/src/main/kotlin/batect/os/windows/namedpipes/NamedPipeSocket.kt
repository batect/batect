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

package batect.os.windows.namedpipes

import batect.os.windows.WindowsNativeMethods
import jnr.posix.POSIXFactory
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.channels.SocketChannel

class NamedPipeSocket : Socket() {
    private val nativeMethods = WindowsNativeMethods(POSIXFactory.getNativePOSIX())

    private lateinit var pipe: NamedPipe
    private var isOpen = false

    private var readTimeout = 0

    private val inputStream = object : InputStream() {
        override fun skip(n: Long): Long = throw UnsupportedOperationException()
        override fun available(): Int = 0

        override fun close() = this@NamedPipeSocket.close()

        override fun reset() = throw UnsupportedOperationException()
        override fun mark(readlimit: Int): Unit = throw UnsupportedOperationException()
        override fun markSupported(): Boolean = false

        override fun read(): Int {
            val bytes = ByteArray(1)
            val result = read(bytes)

            if (result == -1) {
                return -1
            }

            return bytes.first().toInt()
        }

        override fun read(b: ByteArray): Int {
            return read(b, 0, b.size)
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            return pipe.read(b, off, len, readTimeout)
        }
    }

    private val outputStream = object : OutputStream() {
        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()))
        }

        override fun write(b: ByteArray) {
            write(b, 0, b.size)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            pipe.write(b, off, len)
        }

        override fun flush() {}
        override fun close() = this@NamedPipeSocket.close()
    }

    override fun connect(addr: SocketAddress?) {
        this.connect(addr, 0)
    }

    override fun connect(addr: SocketAddress?, timeout: Int) {
        val encodedHostName = (addr as InetSocketAddress).hostName
        val path = NamedPipeDns.decodePath(encodedHostName)

        try {
            pipe = nativeMethods.openNamedPipe(path, timeout)
            isOpen = true
        } catch (e: FileNotFoundException) {
            throw IOException("Cannot connect to '$path': the named pipe does not exist", e)
        }
    }

    override fun getInputStream(): InputStream = inputStream
    override fun getOutputStream(): OutputStream = outputStream

    override fun close() {
        if (!isOpen) {
            return
        }

        pipe.close()
        isOpen = false
    }

    override fun getSoTimeout(): Int = readTimeout
    override fun setSoTimeout(timeout: Int) {
        readTimeout = timeout
    }

    override fun shutdownInput() {}
    override fun shutdownOutput() {}
    override fun isInputShutdown(): Boolean = !this.isConnected
    override fun isOutputShutdown(): Boolean = !this.isConnected

    override fun isBound(): Boolean = isOpen
    override fun isConnected(): Boolean = isOpen
    override fun isClosed(): Boolean = !isOpen

    override fun getReuseAddress(): Boolean = throw UnsupportedOperationException()
    override fun setReuseAddress(on: Boolean) = throw UnsupportedOperationException()
    override fun getKeepAlive(): Boolean = throw UnsupportedOperationException()
    override fun setKeepAlive(on: Boolean): Unit = throw UnsupportedOperationException()
    override fun getPort(): Int = throw UnsupportedOperationException()
    override fun getSoLinger(): Int = throw UnsupportedOperationException()
    override fun setSoLinger(on: Boolean, linger: Int): Unit = throw UnsupportedOperationException()
    override fun getTrafficClass(): Int = throw UnsupportedOperationException()
    override fun setTrafficClass(tc: Int): Unit = throw UnsupportedOperationException()
    override fun getTcpNoDelay(): Boolean = throw UnsupportedOperationException()
    override fun setTcpNoDelay(on: Boolean): Unit = throw UnsupportedOperationException()
    override fun getOOBInline(): Boolean = throw UnsupportedOperationException()
    override fun setOOBInline(on: Boolean): Unit = throw UnsupportedOperationException()
    override fun getLocalPort(): Int = throw UnsupportedOperationException()
    override fun getLocalSocketAddress(): SocketAddress = throw UnsupportedOperationException()
    override fun getRemoteSocketAddress(): SocketAddress = throw UnsupportedOperationException()
    override fun getReceiveBufferSize(): Int = throw UnsupportedOperationException()
    override fun getSendBufferSize(): Int = throw UnsupportedOperationException()
    override fun sendUrgentData(data: Int): Unit = throw UnsupportedOperationException()
    override fun setSendBufferSize(size: Int): Unit = throw UnsupportedOperationException()
    override fun setReceiveBufferSize(size: Int): Unit = throw UnsupportedOperationException()
    override fun getLocalAddress(): InetAddress = throw UnsupportedOperationException()
    override fun getChannel(): SocketChannel = throw UnsupportedOperationException()
    override fun setPerformancePreferences(connectionTime: Int, latency: Int, bandwidth: Int): Unit = throw UnsupportedOperationException()
    override fun getInetAddress(): InetAddress = throw UnsupportedOperationException()
    override fun bind(bindpoint: SocketAddress?): Unit = throw UnsupportedOperationException()
}
