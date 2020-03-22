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

package batect.config.io

data class ConfigurationException(
    override val message: String,
    val fileName: String?,
    val lineNumber: Int?,
    val column: Int?,
    override val cause: Throwable? = null
) : RuntimeException(message, cause) {

    constructor(message: String) : this(message, null, null, null, null)
    constructor(message: String, fileName: String) : this(message, fileName, null, null, null)
    constructor(message: String, lineNumber: Int, column: Int, cause: Throwable? = null) : this(message, null, lineNumber, column, cause)

    override fun toString(): String {
        val location = locationString()

        return if (location != "") {
            "$location: $message"
        } else {
            message
        }
    }

    private fun locationString() = when {
        fileName != null && lineNumber != null && column != null -> "$fileName (line $lineNumber, column $column)"
        fileName != null && lineNumber != null -> "$fileName (line $lineNumber)"
        fileName != null -> fileName
        else -> ""
    }
}
