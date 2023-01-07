/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.docker

import batect.dockerclient.ImagePullProgressUpdate

class ImagePullProgressAggregator {
    private val layerStates = mutableMapOf<String, LayerStatus>()
    private var lastProgressUpdate: AggregatedImagePullProgress? = null

    fun processProgressUpdate(progressUpdate: ImagePullProgressUpdate): AggregatedImagePullProgress? {
        val currentOperation = operationForName(progressUpdate.message) ?: return null

        val layerId = progressUpdate.id
        val previousState = layerStates[layerId]
        layerStates[layerId] = computeNewStateForLayer(previousState, currentOperation, progressUpdate)

        if (progressUpdate.message == buildKitExtractionStepName) {
            markOtherExtractingLayersAsComplete(layerId)
        }

        val overallProgress = computeOverallProgress()

        if (overallProgress != lastProgressUpdate) {
            lastProgressUpdate = overallProgress
            return overallProgress
        }

        return null
    }

    private fun computeNewStateForLayer(previousState: LayerStatus?, currentOperation: DownloadOperation, progressUpdate: ImagePullProgressUpdate): LayerStatus {
        val completedBytes = progressUpdate.detail?.current
        val totalBytes = progressUpdate.detail?.total

        val completedBytesToUse = when {
            completedBytes != null -> completedBytes
            currentOperation.assumeAllBytesCompleted && previousState != null -> previousState.totalBytes
            else -> 0
        }

        val totalBytesToUse = when {
            totalBytes != null && totalBytes != 0L -> totalBytes
            previousState != null -> previousState.totalBytes
            else -> 0
        }

        return LayerStatus(currentOperation, completedBytesToUse, totalBytesToUse)
    }

    private fun markOtherExtractingLayersAsComplete(currentlyExtractingLayerID: String) {
        layerStates
            .filterKeys { it != currentlyExtractingLayerID }
            .filterValues { it.currentOperation == DownloadOperation.Extracting }
            .forEach { (key, value) ->
                layerStates[key] = value.copy(
                    currentOperation = DownloadOperation.PullComplete,
                    completedBytes = value.totalBytes,
                    totalBytes = value.totalBytes,
                )
            }
    }

    private fun computeOverallProgress(): AggregatedImagePullProgress {
        val anyLayerIsExtractingOrComplete = layerStates.values.any { it.currentOperation >= DownloadOperation.Extracting }
        val allLayersHaveFinishedDownloading = layerStates.values.all { it.currentOperation >= DownloadOperation.DownloadComplete }
        val extractionPhase = anyLayerIsExtractingOrComplete && allLayersHaveFinishedDownloading

        val currentOperation = if (extractionPhase) {
            layerStates.values.map { it.currentOperation }.filter { it >= DownloadOperation.Extracting }.minOrNull()!!
        } else {
            layerStates.values.minOf { it.currentOperation }
        }

        val layersInCurrentOperation = layerStates.values.filter { it.currentOperation == currentOperation }
        val layersFinishedCurrentOperation = layerStates.values.filter { it.currentOperation > currentOperation }

        val overallCompletedBytes = layersInCurrentOperation.sumBy { it.completedBytes } + layersFinishedCurrentOperation.sumBy { it.totalBytes }
        val overallTotalBytes = layerStates.values.sumBy { it.totalBytes }

        return AggregatedImagePullProgress(currentOperation, overallCompletedBytes, overallTotalBytes)
    }

    private fun operationForName(name: String): DownloadOperation? = when (name) {
        "Downloading", "downloading" -> DownloadOperation.Downloading
        "Verifying Checksum" -> DownloadOperation.VerifyingChecksum
        "Download complete", "done" -> DownloadOperation.DownloadComplete
        "Extracting", buildKitExtractionStepName -> DownloadOperation.Extracting
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

    companion object {
        private const val buildKitExtractionStepName = "extract"
    }
}
