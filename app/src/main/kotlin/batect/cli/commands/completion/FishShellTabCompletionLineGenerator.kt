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

package batect.cli.commands.completion

import batect.cli.options.EnumValueConverter
import batect.cli.options.OptionDefinition
import batect.cli.options.PathValueConverter
import batect.cli.options.ValueOption

// https://medium.com/@fabioantunes/a-guide-for-fish-shell-completions-485ac04ac63c is a useful reference for this.
class FishShellTabCompletionLineGenerator {
    fun generate(option: OptionDefinition, registerAs: String): String {
        val builder = StringBuilder()

        builder.append("complete -c ")
        builder.append(registerAs)
        builder.append(" -l ")
        builder.append(option.longName)

        if (option.shortName != null) {
            builder.append(" -o ")
            builder.append(option.shortName)
        }

        builder.append(" --description ")
        builder.append("'")
        builder.append(option.description.replace("'", "\\'"))
        builder.append("'")

        if (option is ValueOption<*, *> && option.valueConverter is PathValueConverter) {
            builder.append(" --force-files")
        } else {
            builder.append(" --no-files")
        }

        if (option.allowMultiple) {
            builder.appendOnlyAllowedBeforeTaskArgumentsSeparator()
        } else {
            builder.appendOnlyAllowedOnceAndBeforeTaskArgumentsSeparator(option)
        }

        if (option.acceptsValue) {
            builder.append(" --require-parameter")
        }

        if (option is ValueOption<*, *>) {
            builder.appendValueOption(option)
        }

        return builder.toString()
    }

    private fun StringBuilder.appendOnlyAllowedBeforeTaskArgumentsSeparator() {
        this.append(" --condition \"$notSeenTaskArgumentsSeparator\"")
    }

    private fun StringBuilder.appendOnlyAllowedOnceAndBeforeTaskArgumentsSeparator(option: OptionDefinition) {
        this.append(" --condition \"not __fish_seen_argument -l ")
        this.append(option.longName)

        if (option.shortName != null) {
            this.append(" -o ")
            this.append(option.shortName)
        }

        this.append("; and $notSeenTaskArgumentsSeparator\"")
    }

    private fun StringBuilder.appendValueOption(option: ValueOption<*, *>) {
        if (option.valueConverter is EnumValueConverter<*>) {
            this.append(" -a \"")

            option.valueConverter.possibleValues.keys.sorted().forEachIndexed { i, value ->
                if (i > 0) {
                    this.append(' ')
                }

                this.append(value)
            }

            this.append('"')
        }
    }

    companion object {
        private const val notSeenTaskArgumentsSeparator = "not __fish_seen_subcommand_from --"
    }
}
