/*
   Copyright 2017-2019 Charles Korn.

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
import batect.testutils.CloseableByteArrayOutputStream
import batect.testutils.doesNotThrow
import batect.testutils.equalTo
import batect.testutils.on
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.mock
import okhttp3.Response
import okio.Okio
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch

object ContainerIOStreamerSpec : Spek({
    describe("a container I/O streamer") {
        val streamer = ContainerIOStreamer()

        describe("streaming both input and output for a container") {
            on("all of the output being available immediately") {
                val stdin = ByteArrayInputStream(ByteArray(0))
                val stdout = ByteArrayOutputStream()

                val response = mock<Response>()
                val containerOutputStream = ByteArrayInputStream("This is the output".toByteArray())
                val containerInputStream = CloseableByteArrayOutputStream()

                val outputStream = ContainerOutputStream(response, Okio.buffer(Okio.source(containerOutputStream)))
                val inputStream = ContainerInputStream(response, Okio.buffer(Okio.sink(containerInputStream)))

                streamer.stream(
                    OutputConnection.Connected(outputStream, Okio.sink(stdout)),
                    InputConnection.Connected(Okio.source(stdin), inputStream)
                )

                it("writes all of the output from the output stream to stdout") {
                    assertThat(stdout.toString(), equalTo("This is the output"))
                }

                it("closes the container's input stream") {
                    assertThat(containerInputStream.isClosed, equalTo(true))
                }
            }

            on("stdin closing before the output from the container has finished") {
                val stdin = ByteArrayInputStream("This is the input".toByteArray())
                val stdout = ByteArrayOutputStream()

                val response = mock<Response>()

                val containerOutputStream = object : ByteArrayInputStream("This is the output".toByteArray()) {
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

                val containerInputStream = object : CloseableByteArrayOutputStream() {
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

                val outputStream = ContainerOutputStream(response, Okio.buffer(Okio.source(containerOutputStream)))
                val inputStream = ContainerInputStream(response, Okio.buffer(Okio.sink(containerInputStream)))

                streamer.stream(
                    OutputConnection.Connected(outputStream, Okio.sink(stdout)),
                    InputConnection.Connected(Okio.source(stdin), inputStream)
                )

                it("writes all of the input from stdin to the input stream") {
                    assertThat(containerInputStream.toString(), equalTo("This is the input"))
                }

                it("writes all of the output from the container to stdout") {
                    assertThat(stdout.toString(), equalTo("This is the output"))
                }

                it("closes the container's input stream") {
                    assertThat(containerInputStream.isClosed, equalTo(true))
                }
            }

            on("the output from the container finishing before stdin is closed by the user") {
                val stdin = object : InputStream() {
                    val readStarted = CountDownLatch(1)

                    override fun read(): Int {
                        readStarted.countDown()

                        // Block forever to emulate the user not typing anything.
                        CountDownLatch(1).await()

                        throw RuntimeException("This should never be reached")
                    }
                }

                val stdout = ByteArrayOutputStream()

                val response = mock<Response>()

                val containerOutputStream = object : ByteArrayInputStream("This is the output".toByteArray()) {
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

                val containerInputStream = CloseableByteArrayOutputStream()

                val outputStream = ContainerOutputStream(response, Okio.buffer(Okio.source(containerOutputStream)))
                val inputStream = ContainerInputStream(response, Okio.buffer(Okio.sink(containerInputStream)))

                streamer.stream(
                    OutputConnection.Connected(outputStream, Okio.sink(stdout)),
                    InputConnection.Connected(Okio.source(stdin), inputStream)
                )

                it("writes all of the output from the output stream to stdout") {
                    assertThat(stdout.toString(), equalTo("This is the output"))
                }

                it("closes the container's input stream") {
                    assertThat(containerInputStream.isClosed, equalTo(true))
                }
            }
        }

        describe("streaming just output for a container") {
            val stdout = ByteArrayOutputStream()
            val response = mock<Response>()
            val containerOutputStream = ByteArrayInputStream("This is the output".toByteArray())
            val outputStream = ContainerOutputStream(response, Okio.buffer(Okio.source(containerOutputStream)))

            streamer.stream(
                OutputConnection.Connected(outputStream, Okio.sink(stdout)),
                InputConnection.Disconnected
            )

            it("writes all of the output from the output stream to stdout") {
                assertThat(stdout.toString(), equalTo("This is the output"))
            }
        }

        describe("streaming just input for a container") {
            val output = OutputConnection.Disconnected
            val input = InputConnection.Connected(mock(), mock())

            it("throws an appropriate exception") {
                assertThat({ streamer.stream(output, input) }, throws<DockerException>(withMessage("Cannot stream input to a container when output is disconnected.")))
            }
        }

        describe("streaming neither input nor output for a container") {
            it("does not throw an exception") {
                assertThat({ streamer.stream(OutputConnection.Disconnected, InputConnection.Disconnected) }, doesNotThrow())
            }
        }
    }
})
