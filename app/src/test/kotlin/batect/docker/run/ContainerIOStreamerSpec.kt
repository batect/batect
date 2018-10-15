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

package batect.docker.run

import batect.testutils.equalTo
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockito_kotlin.mock
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
                val containerInputStream = ByteArrayOutputStream()
                val streams = ContainerIOStreams(response, Okio.buffer(Okio.sink(containerInputStream)), Okio.buffer(Okio.source(containerOutputStream)))

                streamer.stream(streams)

                it("writes all of the input from stdin to the input stream") {
                    assertThat(containerInputStream.toString(), equalTo("This is the input"))
                }

                it("writes all of the output from the output stream to stdout") {
                    assertThat(stdout.toString(), equalTo("This is the output"))
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

                val containerInputStream = object : ByteArrayOutputStream() {
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

                val streams = ContainerIOStreams(response, Okio.buffer(Okio.sink(containerInputStream)), Okio.buffer(Okio.source(containerOutputStream)))

                streamer.stream(streams)

                it("writes all of the input from stdin to the input stream") {
                    assertThat(containerInputStream.toString(), equalTo("This is the input"))
                }

                it("writes all of the output from the container to stdout") {
                    assertThat(stdout.toString(), equalTo("This is the output"))
                }
            }

            on("the output from the container finishing before stdin is closed") {
                val stdin = object : InputStream() {
                    override fun read(): Int {
                        // Block forever to emulate the user not typing anything.
                        CountDownLatch(1).await()

                        throw RuntimeException("This should never be reached")
                    }
                }

                val stdout = ByteArrayOutputStream()
                val streamer = ContainerIOStreamer(PrintStream(stdout), stdin)

                val response = mock<Response>()

                val containerOutputStream = ByteArrayInputStream("This is the output".toByteArray())
                val containerInputStream = ByteArrayOutputStream()
                val streams = ContainerIOStreams(response, Okio.buffer(Okio.sink(containerInputStream)), Okio.buffer(Okio.source(containerOutputStream)))

                streamer.stream(streams)

                it("writes all of the output from the output stream to stdout") {
                    assertThat(stdout.toString(), equalTo("This is the output"))
                }
            }
        }
    }
})

private class TwoLineInputStream(
    private val firstLine: String,
    private val secondLine: String,
    private val allowSecondLine: CountDownLatch
) : InputStream() {
    private var nextIndex = 0

    override fun read(): Int {
        val indexInFirstLine = nextIndex

        if (indexInFirstLine < firstLine.length) {
            nextIndex++
            return firstLine[indexInFirstLine].toInt()
        }

        allowSecondLine.await()

        val indexInSecondLine = nextIndex - firstLine.length

        if (indexInSecondLine < secondLine.length) {
            nextIndex++
            return secondLine[indexInSecondLine].toInt()
        }

        return -1
    }

    override fun available(): Int {
        val total = if (allowSecondLine.count == 0L) {
            firstLine.length + secondLine.length
        } else {
            firstLine.length
        }

        return total - nextIndex
    }

    // This emulates the behaviour of read() on a socket - it returns at least one byte (blocking if necessary),
    // but tries to return as much available data as possible immediately without blocking if it can.
    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        val lengthToRead = if (available() == 0) {
            1
        } else {
            Math.min(len, available())
        }

        return super.read(b, off, lengthToRead)
    }
}
