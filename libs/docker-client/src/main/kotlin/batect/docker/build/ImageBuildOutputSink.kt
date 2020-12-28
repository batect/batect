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

package batect.docker.build

import batect.docker.Tee
import okio.BufferedSink
import okio.Sink
import okio.buffer
import okio.sink
import java.io.ByteArrayOutputStream

class ImageBuildOutputSink(val destinationSink: Sink?) {
    private val outputBuffer = ByteArrayOutputStream()
    private val sink = if (destinationSink == null) { outputBuffer.sink() } else { Tee(outputBuffer.sink(), destinationSink) }
    private val buffer = sink.buffer()

    fun <R> use(action: (BufferedSink) -> R): R {
        synchronized(buffer) {
            try {
                return action(buffer)
            } finally {
                buffer.flush()
            }
        }
    }

    val outputSoFar: String
        get() {
            synchronized(buffer) {
                return outputBuffer.toString()
            }
        }
}
