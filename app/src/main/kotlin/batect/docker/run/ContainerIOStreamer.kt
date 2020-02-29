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

import batect.docker.DockerException
import batect.execution.CancellationContext
import okio.Buffer
import okio.BufferedSource
import okio.Sink
import okio.Source
import okio.buffer
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class ContainerIOStreamer() {
    fun stream(output: OutputConnection, input: InputConnection, cancellationContext: CancellationContext) {
        when (output) {
            is OutputConnection.Disconnected -> when (input) {
                is InputConnection.Connected -> throw DockerException("Cannot stream input to a container when output is disconnected.")
                is InputConnection.Disconnected -> return
            }
            is OutputConnection.Connected -> streamWithConnectedOutput(output, input, cancellationContext)
        }
    }

    private fun streamWithConnectedOutput(output: OutputConnection.Connected, input: InputConnection, cancellationContext: CancellationContext) {
        val executor = Executors.newFixedThreadPool(2)

        try {
            val stdinHandler = if (input is InputConnection.Connected) { executor.submit { streamStdin(input) } } else null
            val stdoutHandler = executor.submit { streamStdout(input, output) }

            cancellationContext.addCancellationCallback { cancel(stdinHandler, stdoutHandler) }.use {
                stdinHandler?.get()
                stdoutHandler.get()
            }
        } catch (e: CancellationException) {
            // Do nothing - we aborted the task.
        } finally {
            executor.shutdownNow()

            if (input is InputConnection.Connected) {
                input.source.close()
                input.destination.stream.close()
            }

            output.destination.close()

            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw RuntimeException("Could not terminate all container I/O streaming threads.")
            }
        }
    }

    private fun cancel(stdinHandler: Future<*>?, stdoutHandler: Future<*>) {
        stdinHandler?.cancel(true)
        stdoutHandler.cancel(true)
    }

    private fun streamStdin(input: InputConnection) {
        if (input is InputConnection.Connected) {
            input.source.buffer().copyTo(input.destination.stream)
        }
    }

    private fun streamStdout(input: InputConnection, output: OutputConnection.Connected) {
        output.source.stream.copyTo(output.destination)

        if (input is InputConnection.Connected) {
            input.source.close()
        }
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

sealed class OutputConnection : AutoCloseable {
    data class Connected(val source: ContainerOutputStream, val destination: Sink) : OutputConnection() {
        override fun close() = source.close()
    }

    object Disconnected : OutputConnection() {
        override fun close() {}
    }
}

sealed class InputConnection : AutoCloseable {
    data class Connected(val source: Source, val destination: ContainerInputStream) : InputConnection() {
        override fun close() = destination.close()
    }

    object Disconnected : InputConnection() {
        override fun close() {}
    }
}
