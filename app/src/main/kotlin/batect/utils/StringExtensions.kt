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

package batect.utils

fun String.breakAt(width: Int): String {
    val builder = StringBuilder(this.length)
    var currentLineLength = 0

    this.split(' ').forEach { word ->
        if (currentLineLength + word.length > width && builder.isNotEmpty()) {
            builder.appendln()
            currentLineLength = 0
        } else if (currentLineLength > 0) {
            builder.append(' ')
        }

        builder.append(word)

        currentLineLength += word.length + 1
    }

    return builder.trimEnd().toString()
}

fun pluralize(count: Int, singular: String, plural: String = singular + "s"): String =
    if (count == 1) {
        "$count $singular"
    } else {
        "$count $plural"
    }
