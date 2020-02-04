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

import kotlinx.serialization.json.JsonObject

class DockerImageProgressReporter {
    private val layerStates = mutableMapOf<String, LayerStatus>()
    private var lastProgressUpdate: DockerImageProgress? = null

    fun processProgressUpdate(progressUpdate: JsonObject): DockerImageProgress? {
        val status = progressUpdate.getPrimitiveOrNull("status")?.content
        val currentOperation = DownloadOperation.knownOperations[status]

        if (currentOperation == null) {
            return null
        }

        if (!progressUpdate.containsKey("id")) {
            return extractNonLayerUpdate(currentOperation, progressUpdate)
        }

        val layerId = progressUpdate.getPrimitive("id").content
        val previousState = layerStates[layerId]
        layerStates[layerId] = computeNewStateForLayer(previousState, currentOperation, progressUpdate)

        val overallProgress = computeOverallProgress()

        if (overallProgress != lastProgressUpdate) {
            lastProgressUpdate = overallProgress
            return overallProgress
        }

        return null
    }

    private fun extractNonLayerUpdate(currentOperation: DownloadOperation, progressUpdate: JsonObject): DockerImageProgress {
        val progressDetail = progressUpdate.getObject("progressDetail")
        val completedBytes = progressDetail.getPrimitiveOrNull("current")?.long ?: 0
        var totalBytes = progressDetail.getPrimitiveOrNull("total")?.long ?: 0

        if (totalBytes == -1L) {
            totalBytes = 0
        }

        return DockerImageProgress(currentOperation.displayName, completedBytes, totalBytes)
    }

    private fun computeNewStateForLayer(previousState: LayerStatus?, currentOperation: DownloadOperation, progressUpdate: JsonObject): LayerStatus {
        val progressDetail = progressUpdate.getObject("progressDetail")
        val completedBytes = progressDetail.getPrimitiveOrNull("current")?.long
        val totalBytes = progressDetail.getPrimitiveOrNull("total")?.long

        val completedBytesToUse = if (completedBytes != null) {
            completedBytes
        } else if (currentOperation.assumeAllBytesCompleted && previousState != null) {
            previousState.totalBytes
        } else {
            0
        }

        val totalBytesToUse = when {
            totalBytes != null -> totalBytes
            previousState != null -> previousState.totalBytes
            else -> 0
        }

        return LayerStatus(currentOperation, completedBytesToUse, totalBytesToUse)
    }

    private fun computeOverallProgress(): DockerImageProgress {
        // We need this special case for extraction as extraction only happens one layer at a time and other layers remain in the 'download complete' state
        // until it's their turn to be extracted, but we want to show that extraction progress. (Without this, we'd just look at the earliest step, which is
        // 'download complete', and show that for the entire time the extraction is going on.
        val anyLayerIsExtracting = layerStates.values.any { it.currentOperation == DownloadOperation.Extracting }
        val allLayersHaveFinishedDownloading = layerStates.values.all { it.currentOperation >= DownloadOperation.DownloadComplete }
        val extracting = anyLayerIsExtracting && allLayersHaveFinishedDownloading

        val currentOperation = if (extracting) DownloadOperation.Extracting else layerStates.values.map { it.currentOperation }.min()!!
        val layersInCurrentOperation = layerStates.values.filter { it.currentOperation == currentOperation }
        val layersFinishedCurrentOperation = layerStates.values.filter { it.currentOperation > currentOperation }

        val overallCompletedBytes = layersInCurrentOperation.sumBy { it.completedBytes } + layersFinishedCurrentOperation.sumBy { it.totalBytes }
        val overallTotalBytes = layerStates.values.sumBy { it.totalBytes }

        return DockerImageProgress(currentOperation.displayName, overallCompletedBytes, overallTotalBytes)
    }

    private enum class DownloadOperation(val statusName: String, val assumeAllBytesCompleted: Boolean = false) {
        Downloading("Downloading"),
        VerifyingChecksum("Verifying Checksum"),
        DownloadComplete("Download complete", assumeAllBytesCompleted = true),
        Extracting("Extracting"),
        PullComplete("Pull complete", assumeAllBytesCompleted = true);

        val displayName: String = statusName.toLowerCase()

        companion object {
            val knownOperations = values().associate { it.statusName to it }
        }
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
