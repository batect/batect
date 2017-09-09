/*
   Copyright 2017 Charles Korn.

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

package batect.ui

import java.io.PrintStream

// Usage example:
//
// console.withColor(White) {
//      print("white text")
//      inBold {
//          print("bold white text")
//      }
//      print("more white text")
// }
// console.print("normal text")
//
// Reference: https://en.wikipedia.org/wiki/ANSI_escape_code
class Console(private val outputStream: PrintStream, private val color: ConsoleColor?, private val isBold: Boolean) {
    constructor(outputStream: PrintStream) : this(outputStream, color = null, isBold = false)

    fun print(text: String) = outputStream.print(text)
    fun println(text: String) = outputStream.println(text)
    fun println() = outputStream.println()

    fun withColor(color: ConsoleColor, printStatements: Console.() -> Unit) {
        if (color == this.color) {
            printStatements()
            return
        }

        if (this.color != null) {
            outputStream.print(resetEscapeSequence)

            if (this.isBold) {
                outputStream.print(boldEscapeSequence)
            }
        }

        outputStream.print(escapeSequence(color.code))
        printStatements(Console(outputStream, color, isBold))
        returnConsoleToCurrentState()
    }

    fun inBold(printStatements: Console.() -> Unit) {
        if (this.isBold) {
            printStatements()
            return
        }

        outputStream.print(boldEscapeSequence)
        printStatements(Console(outputStream, color, true))
        returnConsoleToCurrentState()
    }

    fun printBold(text: String) {
        inBold {
            print(text)
        }
    }

    private fun returnConsoleToCurrentState() {
        outputStream.print(resetEscapeSequence)

        if (this.isBold) {
            outputStream.print(boldEscapeSequence)
        }

        if (this.color != null) {
            outputStream.print(escapeSequence(this.color.code))
        }
    }

    private val ESC = "\u001B"
    private fun escapeSequence(code: Int) = "$ESC[${code}m"
    private val resetEscapeSequence = escapeSequence(0)
    private val boldEscapeSequence = escapeSequence(1)
}

enum class ConsoleColor(val code: Int) {
    Black(30),
    Red(31),
    Green(32),
    Yellow(33),
    Blue(34),
    Magenta(35),
    Cyan(36),
    White(37)
}
