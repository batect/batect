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
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class DockerImagePullProgress(val currentOperation: DownloadOperation, val completedBytes: Long, val totalBytes: Long) {
    fun toStringForDisplay(): String {
        if (totalBytes == 0L) {
            return when (completedBytes) {
                0L -> currentOperation.displayName
                else -> "${currentOperation.displayName}: ${humaniseBytes(completedBytes)}"
            }
        }

        val percentage = (completedBytes.toDouble() / totalBytes * 100).roundToInt()

        return "${currentOperation.displayName}: ${humaniseBytes(completedBytes)} of ${humaniseBytes(totalBytes)} ($percentage%)"
    }

    private fun humaniseBytes(bytes: Long): String = when {
        bytes < oneKilobyte -> "$bytes B"
        bytes < oneMegabyte -> "${formatFraction(bytes.toDouble() / oneKilobyte)} KB"
        bytes < oneGigabyte -> "${formatFraction(bytes.toDouble() / oneMegabyte)} MB"
        bytes < oneTerabyte -> "${formatFraction(bytes.toDouble() / oneGigabyte)} GB"
        else -> "${formatFraction(bytes.toDouble() / oneTerabyte)} TB"
    }

    private fun formatFraction(value: Double) = String.format("%.1f", value)

    private val DownloadOperation.displayName: String
        get() = when (this) {
            DownloadOperation.Downloading -> "downloading"
            DownloadOperation.VerifyingChecksum -> "verifying checksum"
            DownloadOperation.DownloadComplete -> "download complete"
            DownloadOperation.Extracting -> "extracting"
            DownloadOperation.PullComplete -> "pull complete"
        }

    companion object {
        // The Docker client uses decimal prefixes (1 MB = 1000 KB), so so do we.
        private const val oneKilobyte: Long = 1000
        private const val oneMegabyte: Long = 1000 * oneKilobyte
        private const val oneGigabyte: Long = 1000 * oneMegabyte
        private const val oneTerabyte: Long = 1000 * oneGigabyte
    }
}
