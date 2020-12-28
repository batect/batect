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

package batect.docker.build.buildkit

import batect.docker.ImageBuildFailedException
import batect.docker.api.SessionStreams
import batect.telemetry.TelemetrySessionBuilder
import batect.telemetry.addUnhandledExceptionEvent
import okhttp3.internal.concurrent.TaskRunner
import okhttp3.internal.http2.Http2Connection
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ThreadFactory

class BuildKitSession(
    val sessionId: String,
    val buildId: String,
    val name: String,
    val sharedKey: String,
    val grpcListener: GrpcListener,
    val telemetrySessionBuilder: TelemetrySessionBuilder
) : AutoCloseable {
    private lateinit var connection: Http2Connection
    private val exceptions = ConcurrentLinkedQueue<Throwable>()

    fun start(streams: SessionStreams) {
        if (::connection.isInitialized) {
            throw UnsupportedOperationException("Connection already started, can't start again.")
        }

        streams.socket.soTimeout = 0

        val connection = Http2Connection.Builder(false, TaskRunner(TaskRunner.RealBackend(threadFactory)))
            .socket(streams.socket, "BuildKit image build (session $sessionId)", streams.source, streams.sink)
            .listener(grpcListener)
            .build()

        connection.start()
    }

    private val threadFactory: ThreadFactory = ThreadFactory { runnable ->
        Thread(runnable, "BuildKit session $sessionId").apply {
            isDaemon = true
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
                exceptions.add(e)
            }
        }
    }

    override fun close() {
        if (::connection.isInitialized) {
            connection.close()
        }

        grpcListener.exceptionsThrownDuringProcessing.forEach { exceptions.add(it) }

        if (exceptions.any()) {
            val builder = StringBuilder()

            builder.appendLine("${exceptions.size} exception(s) thrown during image build:")

            exceptions.forEachIndexed() { i, e ->
                telemetrySessionBuilder.addUnhandledExceptionEvent(e, true)

                builder.appendLine("Exception #${i + 1}: ${e.stackTraceToString()}")
                builder.appendLine()
            }

            throw ImageBuildFailedException(builder.toString())
        }
    }
}
