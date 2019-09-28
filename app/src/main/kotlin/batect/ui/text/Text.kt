/*
   Copyright 2017-2019 Charles Korn.

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

import batect.ui.ConsoleColor

data class Text(val content: String, val color: ConsoleColor? = null, val bold: Boolean? = null) {
    operator fun plus(other: Text) = TextRun(this, other)
    operator fun plus(other: TextRun) = TextRun(listOf(this) + other.text)

    companion object {
        fun bold(content: String) = Text(content, bold = true)
        fun bold(content: Text) = content.copy(bold = true)
        fun bold(content: TextRun) = content.map { it.copy(bold = it.bold ?: true) }

        private fun colored(content: TextRun, color: ConsoleColor) = content.map { it.copy(color = it.color ?: color) }

        fun black(content: String) = Text(content, ConsoleColor.Black)
        fun black(content: Text) = content.copy(color = ConsoleColor.Black)
        fun black(content: TextRun): TextRun = colored(content, ConsoleColor.Black)

        fun red(content: String) = Text(content, ConsoleColor.Red)
        fun red(content: Text) = content.copy(color = ConsoleColor.Red)
        fun red(content: TextRun): TextRun = colored(content, ConsoleColor.Red)

        fun green(content: String) = Text(content, ConsoleColor.Green)
        fun green(content: Text) = content.copy(color = ConsoleColor.Green)
        fun green(content: TextRun): TextRun = colored(content, ConsoleColor.Green)

        fun yellow(content: String) = Text(content, ConsoleColor.Yellow)
        fun yellow(content: Text) = content.copy(color = ConsoleColor.Yellow)
        fun yellow(content: TextRun): TextRun = colored(content, ConsoleColor.Yellow)

        fun blue(content: String) = Text(content, ConsoleColor.Blue)
        fun blue(content: Text) = content.copy(color = ConsoleColor.Blue)
        fun blue(content: TextRun): TextRun = colored(content, ConsoleColor.Blue)

        fun magenta(content: String) = Text(content, ConsoleColor.Magenta)
        fun magenta(content: Text) = content.copy(color = ConsoleColor.Magenta)
        fun magenta(content: TextRun): TextRun = colored(content, ConsoleColor.Magenta)

        fun cyan(content: String) = Text(content, ConsoleColor.Cyan)
        fun cyan(content: Text) = content.copy(color = ConsoleColor.Cyan)
        fun cyan(content: TextRun): TextRun = colored(content, ConsoleColor.Cyan)

        fun white(content: String) = Text(content, ConsoleColor.White)
        fun white(content: Text) = content.copy(color = ConsoleColor.White)
        fun white(content: TextRun): TextRun = colored(content, ConsoleColor.White)
    }
}
