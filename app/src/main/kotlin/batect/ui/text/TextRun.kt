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

package batect.ui.text

data class TextRun(val text: List<Text>) {
    constructor(vararg text: Text) : this(text.toList())
    constructor(text: String) : this(Text(text))

    fun simplify(): TextRun {
        val simplified = this.text.fold(emptyList<Text>()) { acc, text ->
            val previous = acc.lastOrNull()

            if (previous != null && previous.bold == text.bold && previous.color == text.color) {
                acc.dropLast(1) + previous.copy(content = previous.content + text.content)
            } else {
                acc + text
            }
        }

        return TextRun(simplified)
    }

    fun limitToLength(maxLength: Int): TextRun {
        if (this.text.any { it.content.contains('\n') }) {
            throw UnsupportedOperationException("Cannot restrict the length of text containing line breaks.")
        }

        if (maxLength == 0) {
            return TextRun()
        }

        val length = this.text.sumOf { it.content.length }

        if (length <= maxLength) {
            return this
        }

        return shortenAndAddEllipsis(maxLength)
    }

    private fun shortenAndAddEllipsis(maxLength: Int): TextRun {
        val ellipsis = "..."

        if (maxLength <= ellipsis.length) {
            val shortenedEllipsis = ellipsis.take(maxLength)

            return TextRun(this.text.first().copy(content = shortenedEllipsis))
        }

        var lengthSoFar = 0
        val targetLength = maxLength - ellipsis.length
        var nextElementToConsider = 0
        val textElementsToKeep = mutableListOf<Text>()

        do {
            val currentElement = this.text[nextElementToConsider]

            textElementsToKeep += currentElement
            lengthSoFar += currentElement.content.length
            nextElementToConsider++
        } while (lengthSoFar < targetLength)

        val lastElement = textElementsToKeep.last()

        val lastElementContent = if (lengthSoFar > targetLength) {
            val excess = lengthSoFar - targetLength

            lastElement.content.dropLast(excess)
        } else {
            lastElement.content
        }

        return TextRun(textElementsToKeep.dropLast(1) + lastElement.copy(content = lastElementContent + ellipsis))
    }

    fun map(transform: (Text) -> Text) = TextRun(this.text.map(transform))

    operator fun plus(other: Text) = TextRun(this.text + other)
    operator fun plus(other: TextRun) = TextRun(this.text + other.text)

    val lines: List<TextRun> by lazy {
        val linesSoFar = mutableListOf<TextRun>()
        val currentLine = mutableListOf<Text>()

        text.forEach { element ->
            if (!element.content.contains('\n')) {
                currentLine += element
            } else {
                var currentStartIndex = 0

                while (currentStartIndex <= element.content.length) {
                    val endOfThisLine = element.content.indexOf('\n', currentStartIndex)

                    if (endOfThisLine == -1) {
                        currentLine += element.copy(content = element.content.substring(currentStartIndex))
                        currentStartIndex = element.content.length + 1
                    } else {
                        currentLine += element.copy(content = element.content.substring(currentStartIndex, endOfThisLine))
                        linesSoFar += TextRun(currentLine.withoutEmptyElements().toList())
                        currentLine.clear()
                        currentStartIndex = endOfThisLine + 1
                    }
                }
            }
        }

        if (currentLine.isNotEmpty()) {
            linesSoFar += TextRun(currentLine.withoutEmptyElements().toList())
        }

        linesSoFar
    }

    private fun Iterable<Text>.withoutEmptyElements(): Iterable<Text> = this.filter { it.content.isNotEmpty() }
}

fun Iterable<TextRun>.join(separator: TextRun? = null): TextRun = this.fold(TextRun()) { acc, current ->
    if (acc.text.isNotEmpty() && separator != null) {
        acc + separator + current
    } else {
        acc + current
    }
}
