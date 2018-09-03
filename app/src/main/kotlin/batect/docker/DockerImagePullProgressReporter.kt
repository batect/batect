/*
   Copyright 2017-2018 Charles Korn.

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

package batect.docker

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

        if (previousState == null) {
            val completedBytes = progressDetail.getPrimitive("current").int
            val totalBytes = progressDetail.getPrimitive("total").int

            return LayerStatus(currentOperation, completedBytes, totalBytes)
        }

        val completedBytes = progressDetail.getPrimitiveOrNull("current")?.int

        val completedBytesToUse = if (completedBytes != null) {
            completedBytes
        } else if (currentOperation.assumeAllBytesCompleted) {
            previousState.totalBytes
        } else {
            0
        }

        return previousState.copy(currentOperation = currentOperation, completedBytes = completedBytesToUse)
    }

    private fun computeOverallProgress(): DockerImagePullProgress {
        val earliestOperation = layerStates.values.map { it.currentOperation }.min()!!
        val layersInEarliestOperation = layerStates.values.filter { it.currentOperation == earliestOperation }
        val layersInLaterOperations = layerStates.values.filter { it.currentOperation > earliestOperation }

        val overallCompletedBytes = layersInEarliestOperation.sumBy { it.completedBytes } + layersInLaterOperations.sumBy { it.totalBytes }
        val overallTotalBytes = layerStates.values.sumBy { it.totalBytes }

        return DockerImagePullProgress(earliestOperation.displayName, overallCompletedBytes, overallTotalBytes)
    }

    private enum class LayerOperation(val statusName: String, val displayName: String = statusName, val assumeAllBytesCompleted: Boolean = false) {
        Downloading("Downloading"),
        VerifyingChecksum("Verifying Checksum", "Verifying checksum"),
        DownloadComplete("Download complete", assumeAllBytesCompleted = true),
        Extracting("Extracting"),
        PullComplete("Pull complete", assumeAllBytesCompleted = true);

        companion object {
            val knownOperations = LayerOperation.values().associate { it.statusName to it }
        }
    }

    private data class LayerStatus(val currentOperation: LayerOperation, val completedBytes: Int, val totalBytes: Int)
}

data class DockerImagePullProgress(val currentOperation: String, val completedBytes: Int, val totalBytes: Int)
