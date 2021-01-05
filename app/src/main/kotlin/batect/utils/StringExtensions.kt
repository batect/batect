/*
   Copyright 2017-2021 Charles Korn.

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

fun String.breakAt(width: Int): List<String> {
    val lines = mutableListOf<String>()
    val currentLine = StringBuilder()

    this.split(' ').forEach { word ->
        if (currentLine.length + word.length + 1 > width && currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
            currentLine.clear()
        } else if (currentLine.isNotEmpty()) {
            currentLine.append(' ')
        }

        currentLine.append(word)
    }

    currentLine.trimEnd()
    lines.add(currentLine.toString())

    return lines
}

fun pluralize(count: Int, singular: String, plural: String = singular + "s"): String =
    if (count == 1) {
        "$count $singular"
    } else {
        "$count $plural"
    }

fun Collection<String>.asHumanReadableList(conjunction: String = "and"): String {
    return this.foldIndexed("") { index, acc, current ->
        val secondLastItem = index == this.size - 2
        val beforeSecondLastItem = index < this.size - 2

        val separator = when {
            secondLastItem -> " $conjunction "
            beforeSecondLastItem -> ", "
            else -> ""
        }

        acc + current + separator
    }
}
