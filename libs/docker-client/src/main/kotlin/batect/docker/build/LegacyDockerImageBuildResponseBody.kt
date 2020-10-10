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

import batect.docker.DockerImage
import batect.docker.DownloadOperation
import batect.docker.ImageBuildFailedException
import batect.docker.pull.DockerImagePullProgressReporter
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okio.BufferedSink
import okio.Sink
import okio.buffer
import java.io.Reader

class LegacyDockerImageBuildResponseBody : DockerImageBuildResponseBody {
    private var lastStepIndex: Int? = null
    private var imagePullProgressReporter: DockerImagePullProgressReporter? = null

    override fun readFrom(stream: Reader, outputStream: Sink, eventCallback: ImageBuildEventCallback) {
        val outputBuffer = outputStream.buffer()
        lastStepIndex = null

        stream.forEachLine { line -> decodeLine(line, outputBuffer, eventCallback) }
    }

    private fun decodeLine(line: String, outputBuffer: BufferedSink, eventCallback: ImageBuildEventCallback) {
        val json = decodeToJsonObject(line)

        decodeStream(json, outputBuffer, eventCallback)
        decodeError(json, outputBuffer, eventCallback)
        decodeImageID(json, eventCallback)
        decodeProgress(json, eventCallback)
    }

    private fun decodeStream(json: JsonObject, outputBuffer: BufferedSink, eventCallback: ImageBuildEventCallback) {
        val output = json["stream"]?.jsonPrimitive?.content ?: return

        writeOutput(output, outputBuffer)
        checkForNewStepStarting(output, eventCallback)
    }

    private fun writeOutput(output: String, outputBuffer: BufferedSink) {
        outputBuffer.writeString(output, Charsets.UTF_8)
        outputBuffer.flush()
    }

    private fun checkForNewStepStarting(output: String, eventCallback: ImageBuildEventCallback) {
        val buildStepLineMatch = buildStepLineRegex.matchEntire(output) ?: return

        postLastStepCompleted(eventCallback)

        val stepNumber = buildStepLineMatch.groupValues[1].toInt()
        val stepIndex = stepNumber - 1
        val totalSteps = buildStepLineMatch.groupValues[2].toInt()
        val name = buildStepLineMatch.groupValues[3]
        val event = DockerImageBuildEvent.StepStarting(stepIndex, totalSteps, name)
        eventCallback(event)

        lastStepIndex = stepIndex
        imagePullProgressReporter = DockerImagePullProgressReporter()
    }

    private fun decodeError(json: JsonObject, outputBuffer: BufferedSink, eventCallback: ImageBuildEventCallback) {
        val error = json["error"]?.jsonPrimitive?.content ?: return

        writeOutput(error, outputBuffer)
        eventCallback(DockerImageBuildEvent.Error(error))
    }

    private fun decodeImageID(json: JsonObject, eventCallback: ImageBuildEventCallback) {
        val imageID = json["aux"]?.jsonObject?.get("ID")?.jsonPrimitive?.content ?: return

        postLastStepCompleted(eventCallback)
        eventCallback(DockerImageBuildEvent.BuildComplete(DockerImage(imageID)))
    }

    private fun decodeProgress(json: JsonObject, eventCallback: ImageBuildEventCallback) {
        val progress = json["progressDetail"]?.jsonObject

        when {
            progress == null -> return
            json.containsKey("id") -> decodeImagePullProgress(json, eventCallback)
            else -> decodeNonImageDownloadProgress(progress, eventCallback)
        }
    }

    private fun decodeImagePullProgress(json: JsonObject, eventCallback: ImageBuildEventCallback) {
        val update = imagePullProgressReporter!!.processProgressUpdate(json) ?: return

        eventCallback(DockerImageBuildEvent.StepDownloadProgress(lastStepIndex!!, update.currentOperation, update.completedBytes, update.totalBytes))
    }

    private fun decodeNonImageDownloadProgress(progress: JsonObject, eventCallback: ImageBuildEventCallback) {
        val bytesDownloaded = progress["current"]!!.jsonPrimitive.long
        val totalBytes = progress["total"]!!.jsonPrimitive.long
        val totalBytesToReport = if (totalBytes == -1L) null else totalBytes

        eventCallback(DockerImageBuildEvent.StepDownloadProgress(lastStepIndex!!, DownloadOperation.Downloading, bytesDownloaded, totalBytesToReport))
    }

    private fun postLastStepCompleted(eventCallback: ImageBuildEventCallback) {
        if (lastStepIndex != null) {
            eventCallback(DockerImageBuildEvent.StepComplete(lastStepIndex!!))
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

    companion object {
        private val buildStepLineRegex = """^Step (\d+)/(\d+) : (.*)$""".toRegex()
    }
}
