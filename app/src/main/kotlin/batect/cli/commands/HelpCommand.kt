/*
    Copyright 2017-2022 Charles Korn.

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

package batect.cli.commands

import batect.cli.CommandLineOptionsParser
import batect.cli.options.OptionDefinition
import batect.os.ConsoleDimensions
import batect.utils.breakAt
import java.io.PrintStream

class HelpCommand(
    private val optionsParser: CommandLineOptionsParser,
    private val outputStream: PrintStream,
    private val consoleDimensions: ConsoleDimensions
) : Command {
    private val consoleWidth: Int = consoleDimensions.current?.width ?: Int.MAX_VALUE

    override fun run(): Int {
        outputStream.println("Usage: batect [options] task [-- additional arguments to pass to task]")
        outputStream.println()

        val options = optionsParser.optionParser.getOptions()
            .filter { it.showInHelp }
            .associateWith { nameFor(it) }

        val alignToColumn = determineColumnSize(options.values)
        val lines = options.map { (option, name) -> OptionLine(option, name) }

        lines
            .sortedBy { it.option.group.name }
            .groupBy { it.option.group }
            .forEach { (group, options) ->
                outputStream.println(group.name + ":")

                options.sortedBy { it.option.longName }.forEach { print(it, alignToColumn) }
                outputStream.println()
            }

        outputStream.println(CommandLineOptionsParser.helpBlurb)
        outputStream.println()

        return 1
    }

    private fun nameFor(option: OptionDefinition): String {
        val longNamePart = if (option.acceptsValue) "${option.longOption}=${option.valueFormatForHelp}" else option.longOption

        return when (option.shortName) {
            null -> "    $longNamePart"
            else -> "${option.shortOption}, $longNamePart"
        }
    }

    private fun determineColumnSize(optionNames: Iterable<String>): Int = optionNames.map { it.length }.maxOrNull() ?: 0

    private fun print(line: OptionLine, alignToColumn: Int) {
        printInColumn(line.name, line.option.descriptionForHelp, alignToColumn)
    }

    private fun printInColumn(first: String, second: String, alignToColumn: Int) {
        val firstLineIndentationCount = 4 + alignToColumn - first.length
        val firstLineIndentation = " ".repeat(firstLineIndentationCount)

        val secondLineIndentationCount = 5 + alignToColumn
        val secondLineIndentation = " ".repeat(secondLineIndentationCount)

        val secondColumnWidth = consoleWidth - secondLineIndentationCount
        val secondColumnLines = second.breakAt(secondColumnWidth)

        outputStream.print(" ")
        outputStream.print(first)
        outputStream.print(firstLineIndentation)
        outputStream.println(secondColumnLines.first())

        secondColumnLines.drop(1).forEach { line ->
            outputStream.print(secondLineIndentation)
            outputStream.println(line)
        }
    }

    data class OptionLine(val option: OptionDefinition, val name: String)
}
