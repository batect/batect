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

import batect.dockerclient.BuildComplete
import batect.dockerclient.BuildFailed
import batect.dockerclient.ImageBuildContextUploadProgress
import batect.dockerclient.ImageBuildProgressUpdate
import batect.dockerclient.StepContextUploadProgress
import batect.dockerclient.StepDownloadProgressUpdate
import batect.dockerclient.StepFinished
import batect.dockerclient.StepOutput
import batect.dockerclient.StepPullProgressUpdate
import batect.dockerclient.StepStarting
import batect.primitives.mapToSet

class ImageBuildProgressAggregator {
    private val activeSteps = mutableMapOf<Long, StepState>()

    fun processProgressUpdate(progressUpdate: ImageBuildProgressUpdate): AggregatedImageBuildProgress? {
        return when (progressUpdate) {
            is StepOutput -> null
            is BuildComplete -> null
            is BuildFailed -> null
            is ImageBuildContextUploadProgress -> null // TODO: add support for reporting build context upload progress
            is StepContextUploadProgress -> null // TODO: add support for reporting build context upload progress
            is StepStarting -> processStepStarting(progressUpdate)
            is StepFinished -> processStepFinished(progressUpdate)
            is StepDownloadProgressUpdate -> processStepDownloadProgressUpdate(progressUpdate)
            is StepPullProgressUpdate -> processStepPullProgressUpdate(progressUpdate)
        }
    }

    private fun processStepStarting(progressUpdate: StepStarting): AggregatedImageBuildProgress {
        activeSteps[progressUpdate.stepNumber] = StepState(progressUpdate.stepNumber, progressUpdate.stepName)

        return calculateCurrentProgress()
    }

    private fun processStepFinished(progressUpdate: StepFinished): AggregatedImageBuildProgress {
        activeSteps.remove(progressUpdate.stepNumber)

        return calculateCurrentProgress()
    }

    private fun processStepDownloadProgressUpdate(progressUpdate: StepDownloadProgressUpdate): AggregatedImageBuildProgress {
        val previousValue = activeSteps.getValue(progressUpdate.stepNumber)
        val totalBytes = if (progressUpdate.totalBytes <= 0) null else progressUpdate.totalBytes
        val updatedValue = previousValue.copy(detail = StepDetail.Downloading(progressUpdate.bytesDownloaded, totalBytes))
        activeSteps[progressUpdate.stepNumber] = updatedValue

        return calculateCurrentProgress()
    }

    private fun processStepPullProgressUpdate(progressUpdate: StepPullProgressUpdate): AggregatedImageBuildProgress {
        val previousValue = activeSteps.getValue(progressUpdate.stepNumber)
        val aggregator = if (previousValue.detail is StepDetail.PullingImage) previousValue.detail.aggregator else ImagePullProgressAggregator()
        val updatedAggregation = aggregator.processProgressUpdate(progressUpdate.pullProgress)

        if (updatedAggregation != null) {
            val newValue = previousValue.copy(detail = StepDetail.PullingImage(updatedAggregation.currentOperation, updatedAggregation.completedBytes, updatedAggregation.totalBytes, aggregator))
            activeSteps[progressUpdate.stepNumber] = newValue
        }

        return calculateCurrentProgress()
    }

    private fun calculateCurrentProgress(): AggregatedImageBuildProgress {
        val steps = activeSteps.values.mapToSet { it.toActiveImageBuildStep() }

        return AggregatedImageBuildProgress(steps)
    }

    private data class StepState(val stepNumber: Long, val name: String, val detail: StepDetail? = null) {
        fun toActiveImageBuildStep(): ActiveImageBuildStep {
            return when (detail) {
                null -> ActiveImageBuildStep.NotDownloading(stepNumber, name)
                is StepDetail.Downloading -> ActiveImageBuildStep.Downloading(stepNumber, name, DownloadOperation.Downloading, detail.completedBytes, detail.totalBytes)
                is StepDetail.PullingImage -> ActiveImageBuildStep.Downloading(stepNumber, name, detail.operation, detail.completedBytes, detail.totalBytes)
            }
        }
    }

    private sealed class StepDetail {
        data class Downloading(val completedBytes: Long, val totalBytes: Long?) : StepDetail()
        class PullingImage(val operation: DownloadOperation, val completedBytes: Long, val totalBytes: Long, val aggregator: ImagePullProgressAggregator) : StepDetail()
    }
}
