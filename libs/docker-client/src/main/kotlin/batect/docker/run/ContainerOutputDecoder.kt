/*
    Copyright 2017-2022 Charles Korn.

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

import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.Options
import okio.Sink
import okio.Source
import okio.Timeout
import okio.buffer
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.math.min

data class ContainerOutputDecoder(val source: BufferedSource) : BufferedSource {
    private val decoded = object : Source {
        private var remainingBytesInCurrentFrame = 0L

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (remainingBytesInCurrentFrame == 0L) {
                if (!readFrame()) {
                    return -1
                }
            }

            val bytesToRead = min(byteCount, remainingBytesInCurrentFrame)
            val bytesRead = source.read(sink, bytesToRead)

            if (bytesRead == -1L) {
                return -1
            }

            remainingBytesInCurrentFrame -= bytesRead

            return bytesRead
        }

        private fun readFrame(): Boolean {
            if (!source.request(8)) {
                return false
            }

            source.skip(4) // The first byte of the header indicates the stream (stdout or stderr), but we don't (currently) need to differentiate between the two.
            remainingBytesInCurrentFrame = source.readInt().toLong()

            return true
        }

        override fun close() = source.close()
        override fun timeout(): Timeout = source.timeout()
    }

    private val buffered = decoded.buffer()

    override val buffer: Buffer = buffered.buffer

    // Okio has deprecated this method, so so do we.
    @Deprecated("moved to val: use getBuffer() instead", replaceWith = ReplaceWith("buffer"), level = DeprecationLevel.WARNING)
    override fun buffer(): Buffer = buffered.buffer

    override fun close() = buffered.close()
    override fun exhausted(): Boolean = buffered.exhausted()
    override fun indexOf(b: Byte): Long = buffered.indexOf(b)
    override fun indexOf(b: Byte, fromIndex: Long): Long = buffered.indexOf(b, fromIndex)
    override fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long = buffered.indexOf(b, fromIndex, toIndex)
    override fun indexOf(bytes: ByteString): Long = buffered.indexOf(bytes)
    override fun indexOf(bytes: ByteString, fromIndex: Long): Long = buffered.indexOf(bytes, fromIndex)
    override fun indexOfElement(targetBytes: ByteString): Long = buffered.indexOfElement(targetBytes)
    override fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long = buffered.indexOfElement(targetBytes, fromIndex)
    override fun inputStream(): InputStream = buffered.inputStream()
    override fun isOpen(): Boolean = buffered.isOpen()
    override fun peek(): BufferedSource = buffered.peek()
    override fun rangeEquals(offset: Long, bytes: ByteString): Boolean = buffered.rangeEquals(offset, bytes)
    override fun rangeEquals(offset: Long, bytes: ByteString, bytesOffset: Int, byteCount: Int): Boolean = buffered.rangeEquals(offset, bytes, bytesOffset, byteCount)
    override fun read(sink: ByteArray): Int = buffered.read(sink)
    override fun read(sink: ByteArray, offset: Int, byteCount: Int): Int = buffered.read(sink, offset, byteCount)
    override fun read(sink: Buffer, byteCount: Long): Long = buffered.read(sink, byteCount)
    override fun read(dst: ByteBuffer?): Int = buffered.read(dst)
    override fun readAll(sink: Sink): Long = buffered.readAll(sink)
    override fun readByte(): Byte = buffered.readByte()
    override fun readByteArray(): ByteArray = buffered.readByteArray()
    override fun readByteArray(byteCount: Long): ByteArray = buffered.readByteArray(byteCount)
    override fun readByteString(): ByteString = buffered.readByteString()
    override fun readByteString(byteCount: Long): ByteString = buffered.readByteString(byteCount)
    override fun readDecimalLong(): Long = buffered.readDecimalLong()
    override fun readFully(sink: ByteArray) = buffered.readFully(sink)
    override fun readFully(sink: Buffer, byteCount: Long) = buffered.readFully(sink, byteCount)
    override fun readHexadecimalUnsignedLong(): Long = buffered.readHexadecimalUnsignedLong()
    override fun readInt(): Int = buffered.readInt()
    override fun readIntLe(): Int = buffered.readIntLe()
    override fun readLong(): Long = buffered.readLong()
    override fun readLongLe(): Long = buffered.readLongLe()
    override fun readShort(): Short = buffered.readShort()
    override fun readShortLe(): Short = buffered.readShortLe()
    override fun readString(charset: Charset): String = buffered.readString(charset)
    override fun readString(byteCount: Long, charset: Charset): String = buffered.readString(byteCount, charset)
    override fun readUtf8(): String = buffered.readUtf8()
    override fun readUtf8(byteCount: Long): String = buffered.readUtf8(byteCount)
    override fun readUtf8CodePoint(): Int = buffered.readUtf8CodePoint()
    override fun readUtf8Line(): String? = buffered.readUtf8Line()
    override fun readUtf8LineStrict(): String = buffered.readUtf8LineStrict()
    override fun readUtf8LineStrict(limit: Long): String = buffered.readUtf8LineStrict(limit)
    override fun request(byteCount: Long): Boolean = buffered.request(byteCount)
    override fun require(byteCount: Long) = buffered.require(byteCount)
    override fun select(options: Options): Int = buffered.select(options)
    override fun skip(byteCount: Long) = buffered.skip(byteCount)
    override fun timeout(): Timeout = buffered.timeout()
}
