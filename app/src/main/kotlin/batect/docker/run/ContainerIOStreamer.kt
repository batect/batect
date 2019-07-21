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

import okio.Buffer
import okio.BufferedSource
import okio.Okio
import okio.Sink
import java.io.InputStream
import java.io.PrintStream
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.Future

class ContainerIOStreamer(private val stdout: PrintStream, private val stdin: InputStream) {
    fun stream(outputStream: ContainerOutputStream, inputStream: ContainerInputStream) {
        val executor = Executors.newFixedThreadPool(2)

        try {
            val stdinHandler = executor.submit { streamStdin(inputStream) }
            val stdoutHandler = executor.submit { streamStdout(outputStream, stdinHandler) }

            stdinHandler.get()
            stdoutHandler.get()
        } catch (e: CancellationException) {
            // Do nothing - we aborted the task.
        } finally {
            executor.shutdownNow()
            inputStream.stream.close()
        }
    }

    private fun streamStdin(inputStream: ContainerInputStream) {
        val stdinStream = Okio.buffer(Okio.source(stdin))
        stdinStream.copyTo(inputStream.stream)
    }

    private fun streamStdout(outputStream: ContainerOutputStream, stdinHandler: Future<*>) {
        val stdoutSink = Okio.sink(stdout)
        outputStream.stream.copyTo(stdoutSink)
        stdinHandler.cancel(true)
    }

    // This is similar to BufferedSource.readAll, except that we flush after each read from the source,
    // rather than waiting for the sink buffer to fill up.
    private fun BufferedSource.copyTo(sink: Sink) {
        val readBuffer = Buffer()

        while (!this.exhausted()) {
            val bytesRead = this.read(readBuffer, 8192)

            if (bytesRead == -1L) {
                break
            }

            sink.write(readBuffer, bytesRead)
            sink.flush()
        }
    }
}
