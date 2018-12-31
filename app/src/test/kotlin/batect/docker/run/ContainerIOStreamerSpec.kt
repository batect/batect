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

import batect.testutils.CloseableByteArrayOutputStream
import batect.testutils.equalTo
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.mock
import okhttp3.Response
import okio.Okio
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PrintStream
import java.util.concurrent.CountDownLatch

object ContainerIOStreamerSpec : Spek({
    describe("a container I/O streamer") {
        describe("streaming input and output for a container") {
            on("all of the input and output being available immediately") {
                val stdin = ByteArrayInputStream("This is the input".toByteArray())
                val stdout = ByteArrayOutputStream()
                val streamer = ContainerIOStreamer(PrintStream(stdout), stdin)

                val response = mock<Response>()
                val containerOutputStream = ByteArrayInputStream("This is the output".toByteArray())
                val containerInputStream = CloseableByteArrayOutputStream()

                val outputStream = ContainerOutputStream(response, Okio.buffer(Okio.source(containerOutputStream)))
                val inputStream = ContainerInputStream(response, Okio.buffer(Okio.sink(containerInputStream)))

                streamer.stream(outputStream, inputStream)

                it("writes all of the input from stdin to the input stream") {
                    assertThat(containerInputStream.toString(), equalTo("This is the input"))
                }

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
                val streamer = ContainerIOStreamer(PrintStream(stdout), stdin)

                val response = mock<Response>()

                val containerOutputStream = object : ByteArrayInputStream("This is the output".toByteArray()) {
                    val allInputWritten = CountDownLatch(1)

                    override fun read(): Int {
                        allInputWritten.await()
                        return super.read()
                    }

                    override fun read(b: ByteArray?, off: Int, len: Int): Int {
                        allInputWritten.await()
                        return super.read(b, off, len)
                    }

                    override fun read(b: ByteArray?): Int {
                        allInputWritten.await()
                        return super.read(b)
                    }
                }

                val containerInputStream = object : CloseableByteArrayOutputStream() {
                    override fun write(b: Int) {
                        super.write(b)
                        checkIfInputComplete()
                    }

                    override fun write(b: ByteArray?, off: Int, len: Int) {
                        super.write(b, off, len)
                        checkIfInputComplete()
                    }

                    override fun write(b: ByteArray?) {
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

                streamer.stream(outputStream, inputStream)

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
                val streamer = ContainerIOStreamer(PrintStream(stdout), stdin)

                val response = mock<Response>()

                val containerOutputStream = object : ByteArrayInputStream("This is the output".toByteArray()) {
                    override fun read(): Int {
                        stdin.readStarted.await()

                        return super.read()
                    }

                    override fun read(b: ByteArray?, off: Int, len: Int): Int {
                        stdin.readStarted.await()

                        return super.read(b, off, len)
                    }

                    override fun read(b: ByteArray?): Int {
                        stdin.readStarted.await()

                        return super.read(b)
                    }
                }

                val containerInputStream = CloseableByteArrayOutputStream()

                val outputStream = ContainerOutputStream(response, Okio.buffer(Okio.source(containerOutputStream)))
                val inputStream = ContainerInputStream(response, Okio.buffer(Okio.sink(containerInputStream)))

                streamer.stream(outputStream, inputStream)

                it("writes all of the output from the output stream to stdout") {
                    assertThat(stdout.toString(), equalTo("This is the output"))
                }

                it("closes the container's input stream") {
                    assertThat(containerInputStream.isClosed, equalTo(true))
                }
            }
        }
    }
})
