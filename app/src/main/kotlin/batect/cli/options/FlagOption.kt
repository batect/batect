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

package batect.cli.options

import batect.cli.options.defaultvalues.DefaultValueProvider
import batect.cli.options.defaultvalues.PossibleValue
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class FlagOption(
    group: OptionGroup,
    longName: String,
    description: String,
    val defaultValueProvider: DefaultValueProvider<Boolean>,
    shortName: Char? = null
) : OptionDefinition(group, longName, description, false, shortName), ReadOnlyProperty<OptionParserContainer, Boolean> {
    private var value: PossibleValue<Boolean> = defaultValueProvider.value
    override var valueSource: OptionValueSource = defaultValueProvider.valueSource
        private set

    override fun parseValue(args: Iterable<String>): OptionParsingResult {
        val arg = args.first()

        if (arg.contains('=')) {
            val argName = arg.substringBefore('=')
            return OptionParsingResult.InvalidOption("The option '$argName' does not take a value.")
        }

        value = PossibleValue.Valid(true)
        valueSource = OptionValueSource.CommandLine

        return OptionParsingResult.ReadOption(1)
    }

    override fun checkDefaultValue(): DefaultApplicationResult = when (value) {
        is PossibleValue.Valid -> DefaultApplicationResult.Succeeded
        is PossibleValue.Invalid -> DefaultApplicationResult.Failed((value as PossibleValue.Invalid).message)
    }

    operator fun provideDelegate(thisRef: OptionParserContainer, property: KProperty<*>): FlagOption {
        thisRef.optionParser.addOption(this)
        return this
    }

    override fun getValue(thisRef: OptionParserContainer, property: KProperty<*>): Boolean = when (value) {
        is PossibleValue.Valid -> (value as PossibleValue.Valid<Boolean>).value
        is PossibleValue.Invalid -> throw IllegalStateException("Cannot get the value for this option: ${(value as PossibleValue.Invalid).message}")
    }

    override val descriptionForHelp: String
        get() {
            val defaultDescription = defaultValueProvider.description

            if (defaultDescription == "") {
                return description
            } else {
                return "$description $defaultDescription"
            }
        }
}
