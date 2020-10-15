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
import batect.docker.DownloadOperation
import batect.docker.ImageBuildFailedException
import batect.docker.humaniseBytes
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
    private var lastVertexDigest: String? = null
    private var lastProgressUpdate: BuildProgress? = null

    override fun readFrom(stream: Reader, outputStream: Sink, eventCallback: ImageBuildEventCallback) {
        val outputBuffer = outputStream.buffer()

        try {
            stream.forEachLine { line -> decodeLine(line, outputBuffer, eventCallback) }

            writePendingCompletedVertex(outputBuffer)
        } finally {
            outputBuffer.flush()
        }
    }

    private fun decodeLine(line: String, outputBuffer: BufferedSink, eventCallback: ImageBuildEventCallback) {
        val json = decodeToJsonObject(line)

        decodeError(json, eventCallback)
        decodeImageID(json, eventCallback)
        decodeTrace(json, outputBuffer, eventCallback)

        outputBuffer.flush()
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
        val remainingCompletedStatuses = status.statusesList.toMutableList()

        status.vertexesList.forEach { vertex ->
            val vertexLogs = remainingLogs.filter { it.vertex == vertex.digest }
            remainingLogs.removeAll(vertexLogs)

            val completedStatuses = remainingCompletedStatuses.filter { it.vertex == vertex.digest }
            remainingCompletedStatuses.removeAll(completedStatuses)

            writeVertexUpdates(vertex, vertexLogs, completedStatuses, outputBuffer)
        }

        writeLogs(remainingLogs, outputBuffer)
        writeStatuses(remainingCompletedStatuses, outputBuffer)
    }

    private fun writeVertexUpdates(
        vertex: Vertex,
        logs: List<VertexLog>,
        statuses: List<VertexStatus>,
        outputBuffer: BufferedSink
    ) {
        if (!vertex.hasStarted()) {
            if (logs.isNotEmpty()) {
                throw RuntimeException("Expected vertex that has not started to not have any logs.")
            }

            if (statuses.isNotEmpty()) {
                throw RuntimeException("Expected vertex that has not started to not have any statuses.")
            }

            return
        }

        if (!startedVertices.keys.contains(vertex.digest)) {
            val stepNumber = startedVertices.size + 1
            startedVertices[vertex.digest] = VertexInfo(vertex.started.toInstant(), stepNumber, vertex.name, emptyMap())

            writeTransitionTo(vertex.digest, outputBuffer)
        }

        writeLogs(logs, outputBuffer)
        writeStatuses(statuses, outputBuffer)

        if (vertex.hasCompleted()) {
            writeTransitionTo(vertex.digest, outputBuffer)
            handleCompletedVertexUpdate(vertex, outputBuffer)
        }
    }

    private fun writeTransitionTo(vertexDigest: String, outputBuffer: BufferedSink) {
        if (lastVertexDigest == vertexDigest) {
            return
        }

        if (lastVertexDigest != null) {
            if (pendingCompletedVertex != null) {
                writePendingCompletedVertex(outputBuffer)
            } else {
                val stepNumber = startedVertices.getValue(lastVertexDigest!!).stepNumber
                outputBuffer.writeString("#$stepNumber ...\n")
            }

            outputBuffer.writeString("\n")
        }

        val vertex = startedVertices.getValue(vertexDigest)
        outputBuffer.writeString("#${vertex.stepNumber} ${vertex.name}\n")

        lastVertexDigest = vertexDigest
    }

    private fun writeLogs(logs: Iterable<VertexLog>, outputBuffer: BufferedSink) {
        logs.forEach { log ->
            writeTransitionTo(log.vertex, outputBuffer)

            val vertex = startedVertices.getValue(log.vertex)
            val timestamp = Duration.between(vertex.started, log.timestamp.toInstant()).toShortString()

            log.msg.toString(Charsets.UTF_8).trimEnd().lineSequence().forEach { line ->
                outputBuffer.writeString("#${vertex.stepNumber} $timestamp $line\n")
            }
        }
    }

    private fun writeStatuses(statuses: Iterable<VertexStatus>, outputBuffer: BufferedSink) {
        statuses.forEach { status -> writeStatus(status, outputBuffer) }
    }

    private fun writeStatus(status: VertexStatus, outputBuffer: BufferedSink) {
        if (status.hasCompleted()) {
            writeCompletedStatus(status, outputBuffer)
        } else {
            writePossibleNewStatus(status, outputBuffer)
        }
    }

    private fun writeCompletedStatus(status: VertexStatus, outputBuffer: BufferedSink) {
        val layerDigest = status.id.substringAfter("extracting ")
        val currentOperation = startedVertices[status.vertex]?.layers?.get(layerDigest)?.currentOperation

        if (status.name == "done" && currentOperation != null && currentOperation >= DownloadOperation.Downloading) {
            // We received a status update for a layer download out of order (eg. the extraction has already started).
            // Don't print anything.
            return
        }

        // TODO: handle transition from old vertex to new vertex + update lastVertex
        val stepNumber = startedVertices.getValue(status.vertex).stepNumber

        outputBuffer.writeString("#$stepNumber $layerDigest: done\n")
    }

    private fun writePossibleNewStatus(status: VertexStatus, outputBuffer: BufferedSink) {
        val layerDigest = status.id.substringAfter("extracting ")
        val currentOperation = startedVertices[status.vertex]?.layers?.get(layerDigest)?.currentOperation

        if (status.name == "downloading" && currentOperation != DownloadOperation.Downloading) {
            // TODO: handle transition from old vertex to new vertex + update lastVertex
            val stepNumber = startedVertices.getValue(status.vertex).stepNumber
            outputBuffer.writeString("#$stepNumber $layerDigest: downloading ${humaniseBytes(status.total)}\n")
        }

        if (status.name == "extract" && currentOperation != null && currentOperation != DownloadOperation.Extracting) {
            // TODO: handle transition from old vertex to new vertex + update lastVertex
            val stepNumber = startedVertices.getValue(status.vertex).stepNumber
            outputBuffer.writeString("#$stepNumber $layerDigest: extracting\n")
        }
    }

    private fun handleCompletedVertexUpdate(vertex: Vertex, outputBuffer: BufferedSink) {
        if (!vertex.error.isNullOrEmpty()) {
            writeTransitionTo(vertex.digest, outputBuffer)

            val stepNumber = startedVertices.getValue(lastVertexDigest!!).stepNumber
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

        val vertex = startedVertices.getValue(pendingCompletedVertex!!.digest)
        val stepNumber = vertex.stepNumber
        val description = if (pendingCompletedVertex!!.cached) "CACHED" else "DONE"

        outputBuffer.writeString("#$stepNumber $description\n")
        pendingCompletedVertex = null
    }

    private fun postProgressEventIfRequired(statusResponse: StatusResponse, eventCallback: ImageBuildEventCallback) {
        statusResponse.vertexesList.forEach { vertex ->
            if (vertex.hasCompleted()) {
                activeVertices.remove(vertex.digest)
            } else if (vertex.hasStarted()) {
                activeVertices.add(vertex.digest)
            }
        }

        statusResponse.statusesList.forEach { status ->
            startedVertices.compute(status.vertex) { _, vertex ->
                if (vertex == null) {
                    throw DockerException("Received status update for vertex ${status.vertex} but the vertex hasn't started.")
                }

                vertex.withStatus(status)
            }
        }

        val activeSteps = activeVertices
            .map { digest -> startedVertices.getValue(digest) }
            .map { vertex -> vertex.toBuildStep() }
            .toSet()

        val progressUpdate = BuildProgress(activeSteps)

        if (activeSteps.isNotEmpty() && progressUpdate != lastProgressUpdate) {
            eventCallback(progressUpdate)
            lastProgressUpdate = progressUpdate
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
    }

    private fun Timestamp.toInstant(): Instant {
        return Instant.ofEpochSecond(this.seconds, this.nanos.toLong())
    }

    private fun Duration.toShortString(): String {
        val formattedFraction = String.format("%03d", this.nano / 1_000_000)

        return "$seconds.$formattedFraction"
    }

    private data class VertexInfo(val started: Instant, val stepNumber: Int, val name: String, val layers: Map<String, LayerInfo>) {
        val stepIndex = stepNumber - 1

        fun toBuildStep(): ActiveImageBuildStep = when {
            layers.isEmpty() -> ActiveImageBuildStep.NotDownloading(stepIndex, name)
            else -> {
                val earliestOperation = layers.values
                    .map { it.currentOperation }
                    .minByOrNull { it.ordinal }!!

                val layersInEarliestOperation = layers.values.filter { it.currentOperation == earliestOperation }
                val layersInLaterOperations = layers.values.filter { it.currentOperation > earliestOperation }
                val completedBytes = layersInEarliestOperation.map { it.completedBytes }.sum() +
                    layersInLaterOperations.map { it.totalBytes }.sum()

                val totalBytes = layers.values.map { it.totalBytes }.sum()

                ActiveImageBuildStep.Downloading(stepIndex, name, earliestOperation, completedBytes, totalBytes)
            }
        }

        fun withStatus(status: VertexStatus): VertexInfo {
            if (status.total == 0L && status.name != "extract") {
                return this
            }

            when (status.name) {
                "downloading" -> {
                    val newLayers = layers + (status.id to LayerInfo(DownloadOperation.Downloading, status.current, status.total))
                    return copy(layers = newLayers)
                }
                "extract" -> {
                    val digest = status.id.substringAfter("extracting ")
                    val oldLayer = layers[digest]
                    val totalBytes = oldLayer?.totalBytes ?: 0
                    val completedBytes = if (status.hasCompleted()) totalBytes else 0
                    val operation = if (status.hasCompleted()) DownloadOperation.PullComplete else DownloadOperation.Extracting
                    val newLayers = layers + (digest to LayerInfo(operation, completedBytes, totalBytes))

                    return copy(layers = newLayers)
                }
                "done" -> {
                    if (layers.keys.contains(status.id)) {
                        val currentOperation = layers[status.id]?.currentOperation

                        if (currentOperation != null && currentOperation > DownloadOperation.DownloadComplete) {
                            // We've already marked the layer as extracting (eg. because the extracting status arrived first).
                            return this
                        }

                        val newLayers = layers + (status.id to LayerInfo(DownloadOperation.DownloadComplete, status.current, status.total))
                        return copy(layers = newLayers)
                    } else {
                        // Layer was cached.
                        val newLayers = layers + (status.id to LayerInfo(DownloadOperation.PullComplete, status.current, status.total))
                        return copy(layers = newLayers)
                    }
                }
                else -> return this
            }
        }
    }

    private data class LayerInfo(val currentOperation: DownloadOperation, val completedBytes: Long, val totalBytes: Long)
}
