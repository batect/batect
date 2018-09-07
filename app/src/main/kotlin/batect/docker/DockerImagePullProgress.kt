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

import kotlin.math.roundToInt

data class DockerImagePullProgress(val currentOperation: String, val completedBytes: Long, val totalBytes: Long) {
    fun toStringForDisplay(): String {
        if (totalBytes == 0L) {
            return "$currentOperation (0%)"
        }

        val percentage = (completedBytes.toDouble() / totalBytes * 100).roundToInt()

        return "$currentOperation ${humaniseBytes(completedBytes)} of ${humaniseBytes(totalBytes)} ($percentage%)"
    }

    private fun humaniseBytes(bytes: Long): String =
        if (bytes < oneKilobyte) {
            "$bytes B"
        } else if (bytes < oneMegabyte) {
            "${formatFraction(bytes.toDouble() / oneKilobyte)} KB"
        } else if (bytes < oneGigabyte) {
            "${formatFraction(bytes.toDouble() / oneMegabyte)} MB"
        } else if (bytes < oneTerabyte) {
            "${formatFraction(bytes.toDouble() / oneGigabyte)} GB"
        } else {
            "${formatFraction(bytes.toDouble() / oneTerabyte)} TB"
        }

    private fun formatFraction(value: Double) = String.format("%.1f", value)

    companion object {
        // The Docker client uses decimal prefixes (1 MB = 1000 KB), so so do we.
        private const val oneKilobyte: Long = 1000
        private const val oneMegabyte: Long = 1000 * oneKilobyte
        private const val oneGigabyte: Long = 1000 * oneMegabyte
        private const val oneTerabyte: Long = 1000 * oneGigabyte
    }
}
