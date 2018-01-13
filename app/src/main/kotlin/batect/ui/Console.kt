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

package batect.ui

import java.io.ByteArrayOutputStream
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
class Console(
    private val outputStream: PrintStream,
    private val enableComplexOutput: Boolean,
    private val consoleInfo: ConsoleInfo,
    private val color: ConsoleColor?,
    private val isBold: Boolean,
    private val isRestrictedWidth: Boolean
) {
    constructor(outputStream: PrintStream, enableComplexOutput: Boolean, consoleInfo: ConsoleInfo) :
        this(outputStream, enableComplexOutput, consoleInfo, color = null, isBold = false, isRestrictedWidth = false)

    fun print(text: String) = outputStream.print(text)
    fun println(text: String) = outputStream.println(text)
    fun println() = outputStream.println()

    fun withColor(color: ConsoleColor, printStatements: ConsolePrintStatements) {
        if (color == this.color || !enableComplexOutput) {
            printStatements()
            return
        }

        if (this.color != null) {
            outputStream.print(resetEscapeSequence)

            if (this.isBold) {
                outputStream.print(boldEscapeSequence)
            }
        }

        outputStream.print(colorEscapeSequence(color.code))
        printStatements(Console(outputStream, enableComplexOutput, consoleInfo, color, isBold, isRestrictedWidth))
        returnConsoleToCurrentState()
    }

    fun inBold(printStatements: ConsolePrintStatements) {
        if (this.isBold || !enableComplexOutput) {
            printStatements()
            return
        }

        outputStream.print(boldEscapeSequence)
        printStatements(Console(outputStream, enableComplexOutput, consoleInfo, color, true, isRestrictedWidth))
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
            outputStream.print(colorEscapeSequence(this.color.code))
        }
    }

    fun restrictToConsoleWidth(printStatements: ConsolePrintStatements) {
        val consoleWidth = consoleInfo.dimensions?.width

        if (consoleWidth == null) {
            printStatements()
            return
        }

        val innerOutputStream = ByteArrayOutputStream()
        printStatements(Console(PrintStream(innerOutputStream), enableComplexOutput, consoleInfo, color, isBold, true))
        printWithMaximumWidth(innerOutputStream.toString(), consoleWidth)
    }

    private fun printWithMaximumWidth(output: String, maxWidth: Int) {
        if (output.contains('\n')) {
            throw UnsupportedOperationException("Cannot restrict the width of output containing line breaks.")
        }

        var index = 0
        var charactersPrinted = 0
        val escapeSequenceRegex = """$ESC\[\d*[A-Za-z]""".toRegex()
        val escapeSequences = escapeSequenceRegex.findAll(output)
        val escapeSequenceCharacters = escapeSequences.sumBy { it.value.length }
        val printableCharacters = output.length - escapeSequenceCharacters
        val willNeedToPrintEllipsis = printableCharacters > maxWidth
        val escapeSequenceMap = escapeSequences.associateBy { it.range.first }

        while (index < output.length) {
            val escapeSequenceAtThisIndex = escapeSequenceMap[index]

            if (escapeSequenceAtThisIndex != null) {
                val escapeSequence = escapeSequenceAtThisIndex.value

                if (escapeSequence == resetEscapeSequence && charactersPrinted >= maxWidth) {
                    returnConsoleToCurrentState()
                    return
                }

                outputStream.print(escapeSequence)
                index += escapeSequence.length
            } else {
                val nextCharacter = output[index]

                if (charactersPrinted < maxWidth) {
                    outputStream.print(nextCharacter)
                    charactersPrinted++

                    if (willNeedToPrintEllipsis && charactersPrinted == maxWidth - 3) {
                        outputStream.print("...")
                        charactersPrinted += 3
                    }
                }

                index++
            }
        }
    }

    fun moveCursorUp(lines: Int = 1) {
        if (!enableComplexOutput) {
            throw UnsupportedOperationException("Cannot move the cursor when complex output is disabled.")
        }

        if (isRestrictedWidth) {
            throw UnsupportedOperationException("Cannot move the cursor while restricted to the width of the console.")
        }

        if (lines < 1) {
            throw IllegalArgumentException("Number of lines must be positive.")
        }

        outputStream.print("$ESC[${lines}A")
    }

    fun moveCursorDown(lines: Int = 1) {
        if (!enableComplexOutput) {
            throw UnsupportedOperationException("Cannot move the cursor when complex output is disabled.")
        }

        if (isRestrictedWidth) {
            throw UnsupportedOperationException("Cannot move the cursor while restricted to the width of the console.")
        }

        if (lines < 1) {
            throw IllegalArgumentException("Number of lines must be positive.")
        }

        outputStream.print("$ESC[${lines}B")
    }

    fun moveCursorToStartOfLine() {
        if (!enableComplexOutput) {
            throw UnsupportedOperationException("Cannot move the cursor when complex output is disabled.")
        }

        if (isRestrictedWidth) {
            throw UnsupportedOperationException("Cannot move the cursor while restricted to the width of the console.")
        }

        outputStream.print("\r")
    }

    fun clearCurrentLine() {
        if (!enableComplexOutput) {
            throw UnsupportedOperationException("Cannot clear the current line when complex output is disabled.")
        }

        if (isRestrictedWidth) {
            throw UnsupportedOperationException("Cannot clear the current line while restricted to the width of the console.")
        }

        moveCursorToStartOfLine()
        outputStream.print("$ESC[K")
    }

    private val ESC = '\u001B'
    private fun colorEscapeSequence(code: Int) = "$ESC[${code}m"
    private val resetEscapeSequence = colorEscapeSequence(0)
    private val boldEscapeSequence = colorEscapeSequence(1)
}

typealias ConsolePrintStatements = Console.() -> Unit

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
