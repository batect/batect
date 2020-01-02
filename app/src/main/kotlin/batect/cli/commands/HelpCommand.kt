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
import java.io.PrintStream

class HelpCommand(val optionsParser: CommandLineOptionsParser, val outputStream: PrintStream) : Command {
    override fun run(): Int {
        outputStream.println("Usage: batect [options] task [-- additional arguments to pass to task]")
        outputStream.println()
        outputStream.println("Options:")

        val options = formatListOfOptions(optionsParser.optionParser.getOptions())
        printInColumns(options)

        outputStream.println()
        outputStream.println(CommandLineOptionsParser.helpBlurb)
        outputStream.println()

        return 1
    }

    private fun formatListOfOptions(options: Iterable<OptionDefinition>): Map<String, String> {
        return options.sortedBy { it.longName }
            .associate { nameFor(it) to it.descriptionForHelp }
    }

    private fun nameFor(option: OptionDefinition): String {
        val longNamePart = if (option.acceptsValue) "${option.longOption}=${option.valueFormatForHelp}" else option.longOption

        return when {
            option.shortName == null -> "    $longNamePart"
            else -> "${option.shortOption}, $longNamePart"
        }
    }

    private fun printInColumns(items: Map<String, String>) {
        val alignToColumn = items.keys.map { it.length }.max() ?: 0

        items.forEach { (firstColumn, secondColumn) ->
            val indentationCount = 4 + alignToColumn - firstColumn.length
            val indentation = " ".repeat(indentationCount)
            outputStream.println("  $firstColumn$indentation$secondColumn")
        }
    }
}
