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

package batect.sockets.unix

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.on
import batect.testutils.onlyOn
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import jnr.enxio.channels.NativeSelectorProvider
import jnr.ffi.Platform
import jnr.unixsocket.UnixServerSocketChannel
import jnr.unixsocket.UnixSocketAddress
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.SelectionKey
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread

object UnixSocketFactorySpec : Spek({
    onlyOn(setOf(Platform.OS.LINUX, Platform.OS.DARWIN)) {
        describe("a Unix socket factory") {
            val factory = UnixSocketFactory()

            describe("creating a socket") {
                val socket by createForEachTest { factory.createSocket() }

                afterEachTest { socket.close() }

                on("using that socket to connect to a Unix socket") {
                    val dataRead by runForEachTest {
                        val socketPath = getTemporarySocketFileName()

                        createSocketServer(socketPath).use { serverChannel ->
                            val port = 1234
                            val encodedPath = UnixSocketDns.encodePath(socketPath.toString())
                            val address = InetSocketAddress.createUnresolved(encodedPath, port)
                            socket.connect(address)
                            socket.soTimeout = 1000

                            val dataRead = socket.getInputStream().bufferedReader().readLine()
                            serverChannel.close()

                            dataRead
                        }
                    }

                    it("connects to the socket and can receive data") {
                        assertThat(dataRead, equalTo("Hello from the other side"))
                    }
                }

                on("using that socket to connect to a Unix socket that doesn't exist") {
                    val encodedPath = UnixSocketDns.encodePath("/var/run/does-not-exist.sock")
                    val address = InetSocketAddress.createUnresolved(encodedPath, 1234)

                    it("throws an appropriate exception") {
                        assertThat(
                            { socket.connect(address) },
                            throws<IOException>(withMessage("Cannot connect to '/var/run/does-not-exist.sock': No such file or directory"))
                        )
                    }
                }
            }

            on("creating a socket with a particular host and port") {
                it("throws an appropriate exception when using primitive values") {
                    assertThat({ factory.createSocket("somehost", 123) }, throws<UnsupportedOperationException>())
                }

                it("throws an appropriate exception when using non-primitive values") {
                    assertThat(
                        { factory.createSocket(InetAddress.getLocalHost(), 123) },
                        throws<UnsupportedOperationException>()
                    )
                }
            }

            on("creating a socket with both a local and remote host and port") {
                it("throws an appropriate exception when using primitive values") {
                    assertThat(
                        { factory.createSocket("somehost", 123, InetAddress.getLocalHost(), 123) },
                        throws<UnsupportedOperationException>()
                    )
                }

                it("throws an appropriate exception when using non-primitive values") {
                    assertThat(
                        {
                            factory.createSocket(
                                InetAddress.getLocalHost(),
                                123,
                                InetAddress.getLocalHost(),
                                123
                            )
                        },
                        throws<UnsupportedOperationException>()
                    )
                }
            }
        }
    }
})

private fun getTemporarySocketFileName(): Path {
    val socketPath = Files.createTempFile("batect-unit-tests-", "-socket")
    Files.delete(socketPath)
    socketPath.toFile().deleteOnExit()

    return socketPath
}

private fun createSocketServer(socketPath: Path): AutoCloseable {
    val address = UnixSocketAddress(socketPath.toString())
    val channel = UnixServerSocketChannel.open()
    channel.configureBlocking(false)
    channel.socket().bind(address)

    val selector = NativeSelectorProvider.getInstance().openSelector()
    channel.register(selector, SelectionKey.OP_ACCEPT)

    thread(isDaemon = true, name = ::createSocketServer.name) {
        try {
            while (selector.isOpen()) {
                selector.select()

                val iterator = selector.selectedKeys().iterator()

                while (iterator.hasNext()) {
                    val key = iterator.next()
                    iterator.remove()

                    if (key.isAcceptable()) {
                        val client = (key.channel() as UnixServerSocketChannel).accept()
                        client.write(ByteBuffer.wrap("Hello from the other side\n".toByteArray()))
                    }
                }
            }
        } catch (e: AsynchronousCloseException) {
            // Ignore - we closed the server from the test thread.
        }
    }

    return AutoCloseable {
        channel.close()
        selector.close()
    }
}
