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

import batect.docker.DockerException
import batect.docker.DockerImage
import batect.docker.ImageBuildFailedException
import com.google.protobuf.Timestamp
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moby.buildkit.v1.ControlOuterClass.StatusResponse
import moby.buildkit.v1.ControlOuterClass.Vertex
import moby.buildkit.v1.ControlOuterClass.VertexLog
import moby.buildkit.v1.ControlOuterClass.VertexStatus
import okio.BufferedSink
import okio.ByteString.Companion.decodeBase64
import okio.Sink
import okio.buffer
import java.io.Reader
import java.time.Duration
import java.time.Instant

class BuildKitImageBuildResponseBody : ImageBuildResponseBody {
    private val startedVertices = mutableMapOf<String, VertexInfo>()
    private val activeVertices = mutableSetOf<String>()

    private var pendingCompletedVertex: Vertex? = null
    private var lastVertex: String? = null

    override fun readFrom(stream: Reader, outputStream: Sink, eventCallback: ImageBuildEventCallback) {
        val outputBuffer = outputStream.buffer()

        stream.forEachLine { line -> decodeLine(line, outputBuffer, eventCallback) }

        writePendingCompletedVertex(outputBuffer)
    }

    private fun decodeLine(line: String, outputBuffer: BufferedSink, eventCallback: ImageBuildEventCallback) {
        val json = decodeToJsonObject(line)

        decodeError(json, eventCallback)
        decodeImageID(json, eventCallback)
        decodeTrace(json, outputBuffer, eventCallback)
    }

    private fun decodeError(json: JsonObject, eventCallback: ImageBuildEventCallback) {
        val error = json["error"]?.jsonPrimitive?.content ?: return

        eventCallback(BuildError(error))
    }

    private fun decodeImageID(json: JsonObject, eventCallback: ImageBuildEventCallback) {
        if (json["id"]?.jsonPrimitive?.content != "moby.image.id") {
            return
        }

        val imageID = json["aux"]?.jsonObject?.get("ID")?.jsonPrimitive?.content

        if (imageID == null) {
            throw DockerException("Image ID build message does not contain an image ID: $json")
        }

        eventCallback(BuildComplete(DockerImage(imageID)))
    }

    private fun decodeTrace(json: JsonObject, outputBuffer: BufferedSink, eventCallback: ImageBuildEventCallback) {
        if (json["id"]?.jsonPrimitive?.content != "moby.buildkit.trace") {
            return
        }

        val auxString = json["aux"]?.jsonPrimitive?.content

        if (auxString == null) {
            throw DockerException("Image build trace message does not contain 'aux' field: $json")
        }

        val auxBytes = auxString.decodeBase64()

        if (auxBytes == null) {
            throw DockerException("Image build trace message does not contain valid Base64-encoded data in 'aux' field: $json")
        }

        val status = StatusResponse.parseFrom(auxBytes.toByteArray())
        writeStatusUpdate(status, outputBuffer)
        postProgressEventIfRequired(status, eventCallback)
    }

    private fun writeStatusUpdate(
        status: StatusResponse,
        outputBuffer: BufferedSink
    ) {
        val remainingLogs = status.logsList.toMutableList()
        val remainingCompletedStatuses = status.statusesList.filter { it.hasCompleted() }.toMutableList()

        status.vertexesList.forEach { vertex ->
            val vertexLogs = remainingLogs.filter { it.vertex == vertex.digest }
            remainingLogs.removeAll(vertexLogs)

            val completedStatuses = remainingCompletedStatuses.filter { it.vertex == vertex.digest }
            remainingCompletedStatuses.removeAll(completedStatuses)

            writeVertexUpdates(vertex, vertexLogs, completedStatuses, outputBuffer)
        }

        writeLogs(remainingLogs, outputBuffer)
        writeCompletedStatuses(remainingCompletedStatuses, outputBuffer)
    }

    private fun writeVertexUpdates(
        vertex: Vertex,
        logs: List<VertexLog>,
        completedStatuses: List<VertexStatus>,
        outputBuffer: BufferedSink
    ) {
        if (!vertex.hasStarted()) {
            if (logs.isNotEmpty()) {
                throw RuntimeException("Expected vertex that has not started to not have any logs.")
            }

            if (completedStatuses.isNotEmpty()) {
                throw RuntimeException("Expected vertex that has not started to not have any completed statuses.")
            }

            return
        }

        writeTransitionTo(vertex.digest, outputBuffer)

        if (!startedVertices.keys.contains(vertex.digest)) {
            val stepNumber = startedVertices.size + 1
            startedVertices[vertex.digest] = VertexInfo(vertex.started.toInstant(), stepNumber, vertex.name)

            outputBuffer.writeString("#$stepNumber ${vertex.name}\n")
        }

        writeLogs(logs, outputBuffer)
        writeCompletedStatuses(completedStatuses, outputBuffer)

        if (vertex.hasCompleted()) {
            handleCompletedVertexUpdate(vertex, outputBuffer)
        }
    }

    private fun writeTransitionTo(vertexDigest: String, outputBuffer: BufferedSink) {
        if (lastVertex == null || lastVertex == vertexDigest) {
            lastVertex = vertexDigest
            return
        }

        if (pendingCompletedVertex != null) {
            writePendingCompletedVertex(outputBuffer)
        } else {
            val stepNumber = startedVertices.getValue(lastVertex!!).stepNumber
            outputBuffer.writeString("#$stepNumber ...\n")
        }

        outputBuffer.writeString("\n")

        lastVertex = vertexDigest
    }

    private fun writeLogs(logs: Iterable<VertexLog>, outputBuffer: BufferedSink) {
        logs.forEach { log ->
            // TODO: handle transition from old vertex to new vertex

            val vertex = startedVertices.getValue(log.vertex)
            val timestamp = Duration.between(vertex.started, log.timestamp.toInstant()).toShortString()

            // TODO: handle multi-line message
            val message = log.msg.toString(Charsets.UTF_8)

            outputBuffer.writeString("#${vertex.stepNumber} $timestamp $message")

            // TODO: update lastVertex
        }
    }

    private fun writeCompletedStatuses(statuses: Iterable<VertexStatus>, outputBuffer: BufferedSink) {
        statuses.forEach { status ->
            // TODO: handle transition from old vertex to new vertex
            val stepNumber = startedVertices.getValue(status.vertex).stepNumber

            outputBuffer.writeString("#$stepNumber ${status.id}: done\n")

            // TODO: update lastVertex
        }
    }

    private fun handleCompletedVertexUpdate(vertex: Vertex, outputBuffer: BufferedSink) {
        if (!vertex.error.isNullOrEmpty()) {
            val stepNumber = startedVertices.getValue(lastVertex!!).stepNumber
            outputBuffer.writeString("#$stepNumber ERROR: ${vertex.error}\n")
        } else {
            // Why not just write 'DONE' as soon as we see the vertex is completed?
            // Because for some reason, vertices can receive updates (and new completion times) after they're marked as completed.
            // So, instead, we only print that the vertex is done once the daemon has moved on to another vertex.
            pendingCompletedVertex = vertex
        }
    }

    private fun writePendingCompletedVertex(outputBuffer: BufferedSink) {
        if (pendingCompletedVertex == null) {
            return
        }

        val stepNumber = startedVertices.getValue(pendingCompletedVertex!!.digest).stepNumber
        val description = if (pendingCompletedVertex!!.cached) "CACHED" else "DONE"

        outputBuffer.writeString("#$stepNumber $description\n")
        pendingCompletedVertex = null
    }

    private fun postProgressEventIfRequired(status: StatusResponse, eventCallback: ImageBuildEventCallback) {
        var statusChanged = false

        status.vertexesList.forEach { vertex ->
            if (vertex.hasCompleted()) {
                statusChanged = activeVertices.remove(vertex.digest) || statusChanged
            } else if (vertex.hasStarted()) {
                statusChanged = activeVertices.add(vertex.digest) || statusChanged
            }
        }

        if (!statusChanged) {
            return
        }

        val activeSteps = activeVertices
            .map { digest -> startedVertices.getValue(digest) }
            .map { vertex -> ActiveImageBuildStep.NotDownloading(vertex.stepNumber - 1, vertex.name) }
            .toSet()

        if (activeSteps.isNotEmpty()) {
            eventCallback(BuildProgress(activeSteps))
        }
    }

    private fun decodeToJsonObject(line: String): JsonObject {
        try {
            return Json.parseToJsonElement(line).jsonObject
        } catch (e: SerializationException) {
            val formattedLine = Json.encodeToString(String.serializer(), line)

            throw ImageBuildFailedException("Received malformed response from Docker daemon during build: $formattedLine", e)
        }
    }

    private fun BufferedSink.writeString(text: String) {
        this.writeString(text, Charsets.UTF_8)
        this.flush()
    }

    private fun Timestamp.toInstant(): Instant {
        return Instant.ofEpochSecond(this.seconds, this.nanos.toLong())
    }

    private fun Duration.toShortString(): String {
        val formattedFraction = String.format("%03d", this.nano / 1_000_000)

        return "$seconds.$formattedFraction"
    }

    private data class VertexInfo(val started: Instant, val stepNumber: Int, val name: String)
}
