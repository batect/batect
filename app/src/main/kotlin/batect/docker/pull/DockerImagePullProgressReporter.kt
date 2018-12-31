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

package batect.docker.pull

import kotlinx.serialization.json.JsonObject

class DockerImagePullProgressReporter {
    private val layerStates = mutableMapOf<String, LayerStatus>()
    private var lastProgressUpdate: DockerImagePullProgress? = null

    fun processProgressUpdate(progressUpdate: JsonObject): DockerImagePullProgress? {
        val status = progressUpdate.getPrimitiveOrNull("status")?.content
        val currentOperation = LayerOperation.knownOperations[status]

        if (currentOperation == null) {
            return null
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

    private fun computeNewStateForLayer(previousState: LayerStatus?, currentOperation: LayerOperation, progressUpdate: JsonObject): LayerStatus {
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

        val totalBytesToUse = if (totalBytes != null) {
            totalBytes
        } else if (previousState != null) {
            previousState.totalBytes
        } else {
            0
        }

        return LayerStatus(currentOperation, completedBytesToUse, totalBytesToUse)
    }

    private fun computeOverallProgress(): DockerImagePullProgress {
        val earliestOperation = layerStates.values.map { it.currentOperation }.min()!!
        val layersInEarliestOperation = layerStates.values.filter { it.currentOperation == earliestOperation }
        val layersInLaterOperations = layerStates.values.filter { it.currentOperation > earliestOperation }

        val overallCompletedBytes = layersInEarliestOperation.sumBy { it.completedBytes } + layersInLaterOperations.sumBy { it.totalBytes }
        val overallTotalBytes = layerStates.values.sumBy { it.totalBytes }

        return DockerImagePullProgress(earliestOperation.displayName, overallCompletedBytes, overallTotalBytes)
    }

    private enum class LayerOperation(val statusName: String, val assumeAllBytesCompleted: Boolean = false) {
        Downloading("Downloading"),
        VerifyingChecksum("Verifying Checksum"),
        DownloadComplete("Download complete", assumeAllBytesCompleted = true),
        Extracting("Extracting"),
        PullComplete("Pull complete", assumeAllBytesCompleted = true);

        val displayName: String = statusName.toLowerCase()

        companion object {
            val knownOperations = LayerOperation.values().associate { it.statusName to it }
        }
    }

    private data class LayerStatus(val currentOperation: LayerOperation, val completedBytes: Long, val totalBytes: Long)

    private fun <T> Iterable<T>.sumBy(selector: (T) -> Long): Long {
        var sum: Long = 0

        for (element in this) {
            sum += selector(element)
        }

        return sum
    }
}
