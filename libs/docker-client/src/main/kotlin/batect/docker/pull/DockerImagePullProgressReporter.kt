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

package batect.docker.pull

import batect.docker.DownloadOperation
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class DockerImagePullProgressReporter {
    private val layerStates = mutableMapOf<String, LayerStatus>()
    private var lastProgressUpdate: DockerImagePullProgress? = null

    fun processProgressUpdate(progressUpdate: JsonObject): DockerImagePullProgress? {
        val status = progressUpdate["status"]?.jsonPrimitive?.content ?: return null
        val currentOperation = operationForName(status) ?: return null

        val layerId = progressUpdate.getValue("id").jsonPrimitive.content
        val previousState = layerStates[layerId]
        layerStates[layerId] = computeNewStateForLayer(previousState, currentOperation, progressUpdate)

        val overallProgress = computeOverallProgress()

        if (overallProgress != lastProgressUpdate) {
            lastProgressUpdate = overallProgress
            return overallProgress
        }

        return null
    }

    private fun computeNewStateForLayer(previousState: LayerStatus?, currentOperation: DownloadOperation, progressUpdate: JsonObject): LayerStatus {
        val progressDetail = progressUpdate.getValue("progressDetail").jsonObject
        val completedBytes = progressDetail["current"]?.jsonPrimitive?.long
        val totalBytes = progressDetail["total"]?.jsonPrimitive?.long

        val completedBytesToUse = when {
            completedBytes != null -> completedBytes
            currentOperation.assumeAllBytesCompleted && previousState != null -> previousState.totalBytes
            else -> 0
        }

        val totalBytesToUse = when {
            totalBytes != null -> totalBytes
            previousState != null -> previousState.totalBytes
            else -> 0
        }

        return LayerStatus(currentOperation, completedBytesToUse, totalBytesToUse)
    }

    private fun computeOverallProgress(): DockerImagePullProgress {
        val anyLayerIsExtractingOrComplete = layerStates.values.any { it.currentOperation >= DownloadOperation.Extracting }
        val allLayersHaveFinishedDownloading = layerStates.values.all { it.currentOperation >= DownloadOperation.DownloadComplete }
        val extractionPhase = anyLayerIsExtractingOrComplete && allLayersHaveFinishedDownloading

        val currentOperation = if (extractionPhase) {
            layerStates.values.map { it.currentOperation }.filter { it >= DownloadOperation.Extracting }.minOrNull()!!
        } else {
            layerStates.values.map { it.currentOperation }.minOrNull()!!
        }

        val layersInCurrentOperation = layerStates.values.filter { it.currentOperation == currentOperation }
        val layersFinishedCurrentOperation = layerStates.values.filter { it.currentOperation > currentOperation }

        val overallCompletedBytes = layersInCurrentOperation.sumBy { it.completedBytes } + layersFinishedCurrentOperation.sumBy { it.totalBytes }
        val overallTotalBytes = layerStates.values.sumBy { it.totalBytes }

        return DockerImagePullProgress(currentOperation, overallCompletedBytes, overallTotalBytes)
    }

    private fun operationForName(name: String): DownloadOperation? = when (name) {
        "Downloading" -> DownloadOperation.Downloading
        "Verifying Checksum" -> DownloadOperation.VerifyingChecksum
        "Download complete" -> DownloadOperation.DownloadComplete
        "Extracting" -> DownloadOperation.Extracting
        "Pull complete" -> DownloadOperation.PullComplete
        else -> null
    }

    private val DownloadOperation.assumeAllBytesCompleted: Boolean
        get() = when (this) {
            DownloadOperation.DownloadComplete, DownloadOperation.PullComplete -> true
            else -> false
        }

    private data class LayerStatus(val currentOperation: DownloadOperation, val completedBytes: Long, val totalBytes: Long)

    private fun <T> Iterable<T>.sumBy(selector: (T) -> Long): Long {
        var sum: Long = 0

        for (element in this) {
            sum += selector(element)
        }

        return sum
    }
}
