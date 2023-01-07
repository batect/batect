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

package batect.ui.interleaved

import batect.config.Container
import batect.ui.Console
import batect.ui.ConsoleColor
import batect.ui.text.Text
import batect.ui.text.TextRun
import kotlin.math.max

data class InterleavedOutput(
    private val taskName: String,
    private val containers: Set<Container>,
    private val console: Console,
) {
    private val lock = Object()

    private val colours = ConsoleColor.values().filter { it != ConsoleColor.White && it != ConsoleColor.Red }
    private val longestNameLength = max(containers.maxOf { it.name.length }, taskName.length)
    val prefixWidth = longestNameLength + 3

    private val containerPrefixes = containers
        .mapIndexed { index, container ->
            container to prefixFor(container.name, colours[index % colours.size])
        }.toMap()

    private val containerErrorPrefixes = containers
        .mapIndexed { index, container ->
            container to errorPrefixFor(container.name, colours[index % colours.size])
        }.toMap()

    private val taskPrefix = prefixFor(taskName, ConsoleColor.White)
    private val taskErrorPrefix = errorPrefixFor(taskName, ConsoleColor.White)
    private val multilineErrorPrefix = errorPrefixFor("", null)

    fun printForContainer(container: Container, output: TextRun) = printWithPrefix(containerPrefixes.getValue(container), output)
    fun printForTask(output: TextRun) = printWithPrefix(taskPrefix, output)

    fun printErrorForContainer(container: Container, output: TextRun) = printWithPrefix(containerErrorPrefixes.getValue(container), output, multilineErrorPrefix)
    fun printErrorForTask(output: TextRun) = printWithPrefix(taskErrorPrefix, output, multilineErrorPrefix)

    private fun printWithPrefix(prefix: Text, text: TextRun) {
        synchronized(lock) {
            text.lines.forEach { line ->
                console.println(prefix + line)
            }
        }
    }

    private fun printWithPrefix(prefixForFirstLine: TextRun, text: TextRun, prefixForSubsequentLines: TextRun = prefixForFirstLine) {
        var havePrintedFirstLine = false

        synchronized(lock) {
            text.lines.forEach { line ->
                if (!havePrintedFirstLine) {
                    console.println(prefixForFirstLine + line)
                    havePrintedFirstLine = true
                } else {
                    console.println(prefixForSubsequentLines + line)
                }
            }
        }
    }

    private fun prefixFor(name: String, color: ConsoleColor?): Text {
        val padding = " ".repeat(longestNameLength - name.length)

        return Text.bold(Text("$name$padding | ", color))
    }

    private fun errorPrefixFor(name: String, color: ConsoleColor?): TextRun {
        val padding = " ".repeat(longestNameLength - name.length)

        return Text.bold(Text("$name$padding ", color) + Text.red("! "))
    }
}
