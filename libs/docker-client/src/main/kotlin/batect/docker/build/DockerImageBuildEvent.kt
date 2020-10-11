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
import batect.docker.humanReadableStringForDownloadProgress
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class DockerImageBuildEvent

@Serializable
data class BuildProgress(val activeSteps: Set<ActiveImageBuildStep>, val totalSteps: Int) : DockerImageBuildEvent() {
    fun toHumanReadableString(): String {
        if (activeSteps.isEmpty()) {
            return totalSteps.pluralise("step") + " total"
        }

        val firstStep = activeSteps.minByOrNull { it.stepIndex }!!
        val otherStepsRunning = activeSteps.size - 1
        val otherStepsDescription = if (otherStepsRunning == 0) "" else " (+${otherStepsRunning.pluralise("other step")} running)"

        return "step ${firstStep.stepIndex + 1} of $totalSteps: ${firstStep.toHumanReadableString()}$otherStepsDescription"
    }

    private fun Int.pluralise(type: String): String = if (this == 1) "$this $type" else "$this ${type}s"
}

data class BuildError(val message: String) : DockerImageBuildEvent()

data class BuildComplete(val image: DockerImage) : DockerImageBuildEvent()

@Serializable
sealed class ActiveImageBuildStep {
    abstract val stepIndex: Int
    abstract val name: String
    internal abstract fun toHumanReadableString(): String

    @Serializable
    @SerialName("NotDownloading")
    data class NotDownloading(
        override val stepIndex: Int,
        override val name: String
    ) : ActiveImageBuildStep() {
        override fun toHumanReadableString(): String = name
    }

    @Serializable
    @SerialName("Downloading")
    data class Downloading(
        override val stepIndex: Int,
        override val name: String,
        val operation: DownloadOperation,
        val bytesDownloaded: Long,
        val totalBytes: Long?
    ) : ActiveImageBuildStep() {
        override fun toHumanReadableString(): String {
            return "$name: ${humanReadableStringForDownloadProgress(operation, bytesDownloaded, totalBytes)}"
        }
    }
}
