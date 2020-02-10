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

package batect.ui.interleaved

import batect.config.Container
import batect.ui.text.Text
import batect.ui.text.TextRun
import okio.Buffer
import okio.Sink
import okio.Timeout

data class InterleavedContainerOutputSink(val container: Container, val output: InterleavedOutput, val prefix: TextRun = TextRun()) : Sink {
    private val buffer = StringBuilder()

    override fun write(source: Buffer, byteCount: Long) {
        val text = source.readString(byteCount, Charsets.UTF_8)
        buffer.append(text)

        while (buffer.contains('\n')) {
            val endOfLine = buffer.indexOf('\n')
            val line = buffer.substring(0, endOfLine)

            output.printForContainer(container, prefix + Text(line))
            buffer.delete(0, endOfLine + 1)
        }
    }

    override fun close() {
        if (buffer.isNotEmpty()) {
            output.printForContainer(container, prefix + Text(buffer.toString()))
        }
    }

    override fun timeout(): Timeout = Timeout.NONE
    override fun flush() {}
}
