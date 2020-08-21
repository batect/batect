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

package batect.cli.commands

import batect.cli.CommandLineOptionsParser
import batect.cli.options.OptionDefinition
import batect.cli.options.OptionGroup
import batect.os.ConsoleDimensions
import batect.utils.breakAt
import java.io.PrintStream

class HelpCommand(
    val optionsParser: CommandLineOptionsParser,
    val outputStream: PrintStream,
    val consoleDimensions: ConsoleDimensions
) : Command {
    private val consoleWidth: Int = consoleDimensions.current?.width ?: Int.MAX_VALUE

    override fun run(): Int {
        outputStream.println("Usage: batect [options] task [-- additional arguments to pass to task]")
        outputStream.println()

        val options = optionsParser.optionParser.getOptions().associateWith { nameFor(it) }
        val alignToColumn = determineColumnSize(options.values)
        val lines = options.map { (option, name) -> OptionLine(option.group, option.longName, formatInColumn(name, option.descriptionForHelp, alignToColumn)) }

        lines
            .sortedBy { it.group.name }
            .groupBy { it.group }
            .forEach { (group, options) ->
                outputStream.println(group.name + ":")

                options.sortedBy { it.longName }.forEach { outputStream.print(it.text) }
                outputStream.println()
            }

        outputStream.println(CommandLineOptionsParser.helpBlurb)
        outputStream.println()

        return 1
    }

    private fun nameFor(option: OptionDefinition): String {
        val longNamePart = if (option.acceptsValue) "${option.longOption}=${option.valueFormatForHelp}" else option.longOption

        return when {
            option.shortName == null -> "    $longNamePart"
            else -> "${option.shortOption}, $longNamePart"
        }
    }

    private fun determineColumnSize(optionNames: Iterable<String>): Int = optionNames.map { it.length }.maxOrNull() ?: 0

    private fun formatInColumn(first: String, second: String, alignToColumn: Int): String {
        val firstLineIndentationCount = 4 + alignToColumn - first.length
        val firstLineIndentation = " ".repeat(firstLineIndentationCount)

        val secondLineIndentationCount = 5 + alignToColumn
        val secondLineIndentation = " ".repeat(secondLineIndentationCount)

        val secondColumnWidth = consoleWidth - secondLineIndentationCount
        val secondColumnLines = second.breakAt(secondColumnWidth).lines()
        val secondColumn = alternate(secondColumnLines, secondLineIndentation)

        return " $first$firstLineIndentation$secondColumn"
    }

    private fun alternate(lines: List<String>, separator: String): String {
        val builder = StringBuilder()

        builder.appendln(lines.first())

        lines.drop(1).forEach {
            builder.append(separator)
            builder.appendln(it)
        }

        return builder.toString()
    }

    data class OptionLine(val group: OptionGroup, val longName: String, val text: String)
}
