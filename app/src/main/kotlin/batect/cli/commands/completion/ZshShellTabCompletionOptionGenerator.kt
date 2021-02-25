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

package batect.cli.commands.completion

import batect.cli.options.DirectoryPathValueConverter
import batect.cli.options.EnumValueConverter
import batect.cli.options.FilePathValueConverter
import batect.cli.options.OptionDefinition
import batect.cli.options.ValueOption

class ZshShellTabCompletionOptionGenerator {
    fun generate(option: OptionDefinition): Set<String> {
        val repetitionModifier = if (option.allowMultiple) "*" else ""

        return if (option.shortOption != null) {
            setOf(
                "(${option.shortOption})$repetitionModifier${option.longOption}${option.valueDefinition}",
                "(${option.longOption})$repetitionModifier${option.shortOption}${option.valueDefinition}",
            )
        } else {
            setOf("$repetitionModifier${option.longOption}${option.valueDefinition}")
        }
    }

    private val OptionDefinition.valueDefinition: String
        get() = when {
            !acceptsValue -> "[${this.escapedDescription}]"
            this is ValueOption<*, *> && this.valueConverter is EnumValueConverter<*> -> enumValueDefinition(this, this.valueConverter)
            this is ValueOption<*, *> && this.valueConverter is FilePathValueConverter -> "=[${this.escapedDescription}]:file name:_files"
            this is ValueOption<*, *> && this.valueConverter is DirectoryPathValueConverter -> "=[${this.escapedDescription}]:directory name:_files -/"
            else -> "=[${this.escapedDescription}]::( )"
        }

    private fun enumValueDefinition(option: OptionDefinition, valueConverter: EnumValueConverter<*>): String {
        val values = valueConverter.possibleValues.keys.sorted().joinToString(" ")

        return "=[${option.escapedDescription}]:value:($values)"
    }

    private val OptionDefinition.escapedDescription: String
        get() = description.replace("'", "'\\''")
}
