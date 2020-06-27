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

package batect.sockets.namedpipes

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.onlyOn
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import jnr.ffi.Platform
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit

object NamedPipeSocketFactorySpec : Spek({
    onlyOn(setOf(Platform.OS.WINDOWS)) {
        describe("a named pipe socket factory") {
            val factory = NamedPipeSocketFactory()

            describe("creating a socket") {
                val socket by createForEachTest { factory.createSocket() }

                afterEachTest { socket.close() }

                fun connect(pipePath: String) {
                    val port = 1234
                    val encodedPath = NamedPipeDns.encodePath(pipePath)
                    val address = InetSocketAddress.createUnresolved(encodedPath, port)
                    socket.connect(address)
                }

                on("using that socket to connect to a named pipe that exists") {
                    val pipePath by createForEachTest { getTemporaryNamedPipePath() }
                    val pipeServer by createForEachTest { NamedPipeTestServer(pipePath) }

                    afterEachTest { pipeServer.close() }

                    given("the server responds immediately with some data") {
                        beforeEachTest {
                            pipeServer.dataToSend = "Hello from the other side\n"

                            connect(pipePath)
                            socket.soTimeout = 1000
                        }

                        val dataRead by runForEachTest { socket.getInputStream().bufferedReader().readLine() }

                        it("connects to the socket and can receive data") {
                            assertThat(dataRead, equalTo("Hello from the other side"))
                        }
                    }

                    given("the server responds with some data after a delay") {
                        beforeEachTest {
                            pipeServer.sendDelay = 500
                            pipeServer.dataToSend = "Hello from the delayed side\n"

                            connect(pipePath)
                            socket.soTimeout = 1000
                        }

                        val dataRead by runForEachTest { socket.getInputStream().bufferedReader().readLine() }

                        it("connects to the socket and can receive data") {
                            assertThat(dataRead, equalTo("Hello from the delayed side"))
                        }
                    }

                    given("the client is configured with an infinite timeout") {
                        beforeEachTest {
                            pipeServer.sendDelay = 500
                            pipeServer.dataToSend = "Hello from the infinite side\n"

                            connect(pipePath)
                            socket.soTimeout = 0
                        }

                        val dataRead by runForEachTest { socket.getInputStream().bufferedReader().readLine() }

                        it("connects to the socket and can receive data") {
                            assertThat(dataRead, equalTo("Hello from the infinite side"))
                        }
                    }

                    given("the client sends some data") {
                        beforeEachTest {
                            pipeServer.dataToSend = "\n"
                            pipeServer.expectData = true

                            connect(pipePath)

                            socket.getInputStream().bufferedReader().readLine()

                            socket.getOutputStream().bufferedWriter().use { writer ->
                                writer.write("Hello from the client")
                            }

                            if (!pipeServer.dataReceivedEvent.tryAcquire(5, TimeUnit.SECONDS)) {
                                throw RuntimeException("Named pipe server never received data.")
                            }
                        }

                        it("connects to the socket and can send data") {
                            assertThat(pipeServer.dataReceived, equalTo("Hello from the client"))
                        }
                    }

                    given("the server does not respond within the timeout period") {
                        beforeEachTest {
                            connect(pipePath)
                            socket.soTimeout = 1
                        }

                        it("throws an appropriate exception") {
                            assertThat(
                                { socket.getInputStream().bufferedReader().readLine() },
                                throws<SocketTimeoutException>(withMessage("Operation timed out after 1 ms."))
                            )
                        }
                    }
                }

                on("using that socket to connect to a named pipe that doesn't exist") {
                    val encodedPath = NamedPipeDns.encodePath("""\\.\pipe\does-not-exist""")
                    val address = InetSocketAddress.createUnresolved(encodedPath, 1234)

                    it("throws an appropriate exception") {
                        assertThat(
                            { socket.connect(address) },
                            throws<IOException>(withMessage("""Cannot connect to '\\.\pipe\does-not-exist': the named pipe does not exist"""))
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
                    assertThat({
                        factory.createSocket(
                            InetAddress.getLocalHost(),
                            123,
                            InetAddress.getLocalHost(),
                            123
                        )
                    }, throws<UnsupportedOperationException>())
                }
            }
        }
    }
})

private fun getTemporaryNamedPipePath(): String = """\\.\pipe\batect-${NamedPipeSocketFactorySpec::class.simpleName}-${UUID.randomUUID()}"""
