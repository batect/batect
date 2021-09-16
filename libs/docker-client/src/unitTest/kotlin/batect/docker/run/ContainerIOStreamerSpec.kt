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

package batect.docker.run

import batect.docker.DockerException
import batect.primitives.CancellationCallback
import batect.primitives.CancellationContext
import batect.testutils.CloseableByteArrayInputStream
import batect.testutils.CloseableByteArrayOutputStream
import batect.testutils.createForEachTest
import batect.testutils.doesNotThrow
import batect.testutils.equalTo
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.lessThan
import com.natpryce.hamkrest.throws
import okhttp3.Response
import okio.buffer
import okio.sink
import okio.source
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import kotlin.system.measureTimeMillis

object ContainerIOStreamerSpec : Spek({
    describe("a container I/O streamer") {
        val streamer by createForEachTest { ContainerIOStreamer() }

        describe("streaming both input and output for a container") {
            on("all of the output being available immediately") {
                val stdin by createForEachTest { CloseableByteArrayInputStream(ByteArray(0)) }
                val stdout by createForEachTest { CloseableByteArrayOutputStream() }

                val response by createForEachTest { mock<Response>() }
                val containerOutputStream by createForEachTest { ByteArrayInputStream("This is the output".toByteArray()) }
                val containerInputStream by createForEachTest { CloseableByteArrayOutputStream() }

                val outputStream by createForEachTest { ContainerOutputStream(response, containerOutputStream.source().buffer()) }
                val inputStream by createForEachTest { ContainerInputStream(response, containerInputStream.sink().buffer()) }
                val cancellationContext by createForEachTest { mock<CancellationContext>() }

                beforeEachTest {
                    streamer.stream(
                        OutputConnection.Connected(outputStream, stdout.sink()),
                        InputConnection.Connected(stdin.source(), inputStream),
                        cancellationContext
                    )
                }

                it("writes all of the output from the output stream to stdout") {
                    assertThat(stdout.toString(), equalTo("This is the output"))
                }

                it("closes the container's input stream") {
                    assertThat(containerInputStream.isClosed, equalTo(true))
                }

                it("closes the stdin stream") {
                    assertThat(stdin.isClosed, equalTo(true))
                }

                it("closes the stdout stream") {
                    assertThat(stdout.isClosed, equalTo(true))
                }
            }

            on("stdin closing before the output from the container has finished") {
                val stdin by createForEachTest { CloseableByteArrayInputStream("This is the input".toByteArray()) }
                val stdout by createForEachTest { CloseableByteArrayOutputStream() }

                val response by createForEachTest { mock<Response>() }

                val containerOutputStream by createForEachTest {
                    object : ByteArrayInputStream("This is the output".toByteArray()) {
                        val allInputWritten = CountDownLatch(1)

                        override fun read(): Int {
                            allInputWritten.await()
                            return super.read()
                        }

                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            allInputWritten.await()
                            return super.read(b, off, len)
                        }

                        override fun read(b: ByteArray): Int {
                            allInputWritten.await()
                            return super.read(b)
                        }
                    }
                }

                val containerInputStream by createForEachTest {
                    object : CloseableByteArrayOutputStream() {
                        override fun write(b: Int) {
                            super.write(b)
                            checkIfInputComplete()
                        }

                        override fun write(b: ByteArray, off: Int, len: Int) {
                            super.write(b, off, len)
                            checkIfInputComplete()
                        }

                        override fun write(b: ByteArray) {
                            super.write(b)
                            checkIfInputComplete()
                        }

                        private fun checkIfInputComplete() {
                            if (this.toString() == "This is the input") {
                                containerOutputStream.allInputWritten.countDown()
                            }
                        }
                    }
                }

                val outputStream by createForEachTest { ContainerOutputStream(response, containerOutputStream.source().buffer()) }
                val inputStream by createForEachTest { ContainerInputStream(response, containerInputStream.sink().buffer()) }
                val cancellationContext by createForEachTest { mock<CancellationContext>() }

                beforeEachTest {
                    streamer.stream(
                        OutputConnection.Connected(outputStream, stdout.sink()),
                        InputConnection.Connected(stdin.source(), inputStream),
                        cancellationContext
                    )
                }

                it("writes all of the input from stdin to the input stream") {
                    assertThat(containerInputStream.toString(), equalTo("This is the input"))
                }

                it("writes all of the output from the container to stdout") {
                    assertThat(stdout.toString(), equalTo("This is the output"))
                }

                it("closes the container's input stream") {
                    assertThat(containerInputStream.isClosed, equalTo(true))
                }

                it("closes the stdin stream") {
                    assertThat(stdin.isClosed, equalTo(true))
                }

                it("closes the stdout stream") {
                    assertThat(stdout.isClosed, equalTo(true))
                }
            }

            on("the output from the container finishing before stdin is closed by the user") {
                val stdin by createForEachTest {
                    object : InputStream() {
                        val readStarted = CountDownLatch(1)
                        val closed = CountDownLatch(1)

                        override fun read(): Int {
                            readStarted.countDown()

                            closed.await()

                            return -1
                        }

                        override fun close() {
                            closed.countDown()
                        }
                    }
                }

                val stdout by createForEachTest { CloseableByteArrayOutputStream() }
                val response by createForEachTest { mock<Response>() }

                val containerOutputStream by createForEachTest {
                    object : ByteArrayInputStream("This is the output".toByteArray()) {
                        override fun read(): Int {
                            stdin.readStarted.await()

                            return super.read()
                        }

                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            stdin.readStarted.await()

                            return super.read(b, off, len)
                        }

                        override fun read(b: ByteArray): Int {
                            stdin.readStarted.await()

                            return super.read(b)
                        }
                    }
                }

                val containerInputStream by createForEachTest { CloseableByteArrayOutputStream() }

                val outputStream by createForEachTest { ContainerOutputStream(response, containerOutputStream.source().buffer()) }
                val inputStream by createForEachTest { ContainerInputStream(response, containerInputStream.sink().buffer()) }
                val cancellationContext by createForEachTest { mock<CancellationContext>() }

                beforeEachTest {
                    streamer.stream(
                        OutputConnection.Connected(outputStream, stdout.sink()),
                        InputConnection.Connected(stdin.source(), inputStream),
                        cancellationContext
                    )
                }

                it("writes all of the output from the output stream to stdout") {
                    assertThat(stdout.toString(), equalTo("This is the output"))
                }

                it("closes the container's input stream") {
                    assertThat(containerInputStream.isClosed, equalTo(true))
                }

                it("closes the stdout stream") {
                    assertThat(stdout.isClosed, equalTo(true))
                }
            }

            on("streaming being cancelled") {
                val stdin by createForEachTest {
                    object : InputStream() {
                        var isClosed: Boolean = false
                            private set

                        override fun read(): Int {
                            Thread.sleep(10_000)
                            return -1
                        }

                        override fun close() {
                            isClosed = true
                            super.close()
                        }
                    }
                }

                val stdout by createForEachTest { CloseableByteArrayOutputStream() }
                val response by createForEachTest { mock<Response>() }

                val containerOutputStream by createForEachTest {
                    object : ByteArrayInputStream("This is the output".toByteArray()) {
                        override fun read(): Int {
                            Thread.sleep(10_000)
                            return -1
                        }

                        override fun read(b: ByteArray, off: Int, len: Int): Int = read()
                        override fun read(b: ByteArray): Int = read()
                    }
                }

                val containerInputStream by createForEachTest { CloseableByteArrayOutputStream() }

                val outputStream by createForEachTest { ContainerOutputStream(response, containerOutputStream.source().buffer()) }
                val inputStream by createForEachTest { ContainerInputStream(response, containerInputStream.sink().buffer()) }
                val cancellationContext by createForEachTest {
                    mock<CancellationContext> {
                        on { addCancellationCallback(any()) } doAnswer { invocation ->
                            @Suppress("UNCHECKED_CAST")
                            val callback = invocation.arguments[0] as CancellationCallback

                            callback()

                            AutoCloseable { }
                        }
                    }
                }

                val timeTakenToCancel by runForEachTest {
                    measureTimeMillis {
                        streamer.stream(
                            OutputConnection.Connected(outputStream, stdout.sink()),
                            InputConnection.Connected(stdin.source(), inputStream),
                            cancellationContext
                        )
                    }
                }

                it("returns quickly after being cancelled") {
                    assertThat(timeTakenToCancel, lessThan(10_000L))
                }

                it("closes the container's input stream") {
                    assertThat(containerInputStream.isClosed, equalTo(true))
                }

                it("closes the stdin stream") {
                    assertThat(stdin.isClosed, equalTo(true))
                }

                it("closes the stdout stream") {
                    assertThat(stdout.isClosed, equalTo(true))
                }
            }
        }

        describe("streaming just output for a container") {
            val stdout by createForEachTest { CloseableByteArrayOutputStream() }
            val response by createForEachTest { mock<Response>() }
            val containerOutputStream by createForEachTest { ByteArrayInputStream("This is the output".toByteArray()) }
            val outputStream by createForEachTest { ContainerOutputStream(response, containerOutputStream.source().buffer()) }
            val cancellationContext by createForEachTest { mock<CancellationContext>() }

            beforeEachTest {
                streamer.stream(
                    OutputConnection.Connected(outputStream, stdout.sink()),
                    InputConnection.Disconnected,
                    cancellationContext
                )
            }

            it("writes all of the output from the output stream to stdout") {
                assertThat(stdout.toString(), equalTo("This is the output"))
            }

            it("closes the stdout stream") {
                assertThat(stdout.isClosed, equalTo(true))
            }
        }

        describe("streaming just input for a container") {
            val output by createForEachTest { OutputConnection.Disconnected }
            val input by createForEachTest { InputConnection.Connected(mock(), mock()) }
            val cancellationContext by createForEachTest { mock<CancellationContext>() }

            it("throws an appropriate exception") {
                assertThat({ streamer.stream(output, input, cancellationContext) }, throws<DockerException>(withMessage("Cannot stream input to a container when output is disconnected.")))
            }
        }

        describe("streaming neither input nor output for a container") {
            val cancellationContext by createForEachTest { mock<CancellationContext>() }

            it("does not throw an exception") {
                assertThat({ streamer.stream(OutputConnection.Disconnected, InputConnection.Disconnected, cancellationContext) }, doesNotThrow())
            }
        }
    }
})
