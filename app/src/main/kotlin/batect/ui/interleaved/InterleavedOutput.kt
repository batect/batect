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
    private val console: Console
) {
    private val lock = Object()

    private val longestNameLength = max(containers.map { it.name.length }.max()!!, taskName.length)
    private val containerPrefixes = containers.associateWith { prefixFor(it.name, null) }
    private val taskPrefix = prefixFor(taskName, ConsoleColor.White)

    fun printForContainer(container: Container, output: TextRun) {
        synchronized(lock) {
            console.println(containerPrefixes.getValue(container) + output)
        }
    }

    fun printForTask(output: TextRun) {
        synchronized(lock) {
            console.println(taskPrefix + output)
        }
    }

    private fun prefixFor(name: String, color: ConsoleColor?): Text {
        val padding = " ".repeat(longestNameLength - name.length)

        return Text.bold(Text("$name$padding | ", color))
    }
}
