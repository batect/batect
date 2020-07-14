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

package batect.docker.run

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import java.io.ByteArrayInputStream
import okio.Buffer
import okio.buffer
import okio.source
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ContainerOutputDecoderSpec : Spek({
    describe("a container output decoder") {
        given("the underlying stream is empty") {
            val source by createForEachTest { ByteArrayInputStream(ByteArray(0)) }
            val decoder by createForEachTest { ContainerOutputDecoder(source.source().buffer()) }

            it("returns EOF when read") {
                assertThat(decoder.read(Buffer(), Long.MAX_VALUE), equalTo(-1))
            }
        }

        given("the underlying stream contains a single frame") {
            val helloBytes = "hello".toByteArray(Charsets.UTF_8)
            val source by createForEachTest { ByteArrayInputStream(byteArrayOf(1, 0, 0, 0, 0, 0, 0, helloBytes.size.toByte(), *helloBytes)) }
            val decoder by createForEachTest { ContainerOutputDecoder(source.source().buffer()) }

            given("the first read requests exactly the same number of bytes as those available in the frame") {
                val buffer by createForEachTest { Buffer() }
                val firstReadResult by createForEachTest { decoder.read(buffer, 5) }
                val secondReadResult by createForEachTest { decoder.read(Buffer(), Long.MAX_VALUE) }

                it("returns the number of bytes read on the first read") {
                    assertThat(firstReadResult, equalTo(5))
                }

                it("reads only the output bytes from the stream") {
                    assertThat(buffer.readString(Charsets.UTF_8), equalTo("hello"))
                }

                it("returns EOF when read again") {
                    assertThat(secondReadResult, equalTo(-1))
                }
            }

            given("the first read requests more bytes than those available in the frame") {
                val buffer by createForEachTest { Buffer() }
                val firstReadResult by createForEachTest { decoder.read(buffer, Long.MAX_VALUE) }
                val secondReadResult by createForEachTest { decoder.read(Buffer(), Long.MAX_VALUE) }

                it("returns the number of bytes read on the first read") {
                    assertThat(firstReadResult, equalTo(5))
                }

                it("reads only the output bytes from the stream") {
                    assertThat(buffer.readString(Charsets.UTF_8), equalTo("hello"))
                }

                it("returns EOF when read again") {
                    assertThat(secondReadResult, equalTo(-1))
                }
            }

            given("the first read requests fewer bytes than those available in the frame") {
                val firstBuffer by createForEachTest { Buffer() }
                val firstReadResult by createForEachTest { decoder.read(firstBuffer, 3) }
                val secondBuffer by createForEachTest { Buffer() }
                val secondReadResult by createForEachTest { decoder.read(secondBuffer, Long.MAX_VALUE) }
                val thirdReadResult by createForEachTest { decoder.read(Buffer(), Long.MAX_VALUE) }

                it("returns the number of bytes read on the first read") {
                    assertThat(firstReadResult, equalTo(3))
                }

                it("reads only the output bytes from the stream up to the maximum number of bytes requested") {
                    assertThat(firstBuffer.readString(Charsets.UTF_8), equalTo("hel"))
                }

                it("returns the number of bytes read on the second read") {
                    assertThat(secondReadResult, equalTo(2))
                }

                it("reads the remaining bytes from the frame when read a second time") {
                    assertThat(secondBuffer.readString(Charsets.UTF_8), equalTo("lo"))
                }

                it("returns EOF when read again") {
                    assertThat(thirdReadResult, equalTo(-1))
                }
            }
        }

        given("the underlying stream contains a single frame larger than Okio's default read size (8192 bytes)") {
            val frameContent = "a".repeat(9000).toByteArray(Charsets.UTF_8)
            val source by createForEachTest { ByteArrayInputStream(byteArrayOf(1, 0, 0, 0, 0, 0, 0b0010_0011, 0b0010_1000, *frameContent)) }

            on("reading the entire stream") {
                val decoder by createForEachTest { ContainerOutputDecoder(source.source().buffer()) }
                val buffer by createForEachTest { Buffer() }
                val bytesRead by createForEachTest { decoder.readAll(buffer) }

                it("returns the number of bytes read") {
                    assertThat(bytesRead, equalTo(9000))
                }

                it("reads the whole stream") {
                    assertThat(buffer.readString(Charsets.UTF_8), equalTo("a".repeat(9000)))
                }
            }
        }

        given("the underlying stream contains multiple frames") {
            val helloBytes = "hello".toByteArray(Charsets.UTF_8)
            val worldBytes = " world".toByteArray(Charsets.UTF_8)
            val source by createForEachTest {
                ByteArrayInputStream(byteArrayOf(
                    1, 0, 0, 0, 0, 0, 0, helloBytes.size.toByte(), *helloBytes,
                    1, 0, 0, 0, 0, 0, 0, worldBytes.size.toByte(), *worldBytes
                ))
            }

            on("reading the entire stream") {
                val decoder by createForEachTest { ContainerOutputDecoder(source.source().buffer()) }
                val buffer by createForEachTest { Buffer() }
                val bytesRead by createForEachTest { decoder.readAll(buffer) }

                it("returns the number of bytes read") {
                    assertThat(bytesRead, equalTo(11))
                }

                it("reads the whole stream") {
                    assertThat(buffer.readString(Charsets.UTF_8), equalTo("hello world"))
                }
            }
        }

        given("the underlying stream contains multiple frames larger than Okio's default read size (8192 bytes)") {
            val frame1Content = "a".repeat(9000).toByteArray(Charsets.UTF_8)
            val frame2Content = "b".repeat(9000).toByteArray(Charsets.UTF_8)

            val source by createForEachTest {
                ByteArrayInputStream(byteArrayOf(
                    1, 0, 0, 0, 0, 0, 0b0010_0011, 0b0010_1000, *frame1Content,
                    1, 0, 0, 0, 0, 0, 0b0010_0011, 0b0010_1000, *frame2Content
                ))
            }

            on("reading the entire stream") {
                val decoder by createForEachTest { ContainerOutputDecoder(source.source().buffer()) }
                val buffer by createForEachTest { Buffer() }
                val bytesRead by createForEachTest { decoder.readAll(buffer) }

                it("returns the number of bytes read") {
                    assertThat(bytesRead, equalTo(18000))
                }

                it("reads the whole stream") {
                    assertThat(buffer.readString(Charsets.UTF_8), equalTo("a".repeat(9000) + "b".repeat(9000)))
                }
            }
        }
    }
})
