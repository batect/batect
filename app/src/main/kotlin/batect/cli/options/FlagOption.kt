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

package batect.cli.options

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class FlagOption(
    longName: String,
    description: String,
    shortName: Char? = null
) : OptionDefinition(longName, description, false, shortName), ReadOnlyProperty<OptionParserContainer, Boolean> {
    var value = false
        private set

    override fun parseValue(args: Iterable<String>): OptionParsingResult {
        val arg = args.first()

        if (arg.contains('=')) {
            val argName = arg.substringBefore('=')
            return OptionParsingResult.InvalidOption("The option '$argName' does not take a value.")
        }

        value = true

        return OptionParsingResult.ReadOption(1)
    }

    operator fun provideDelegate(thisRef: OptionParserContainer, property: KProperty<*>): FlagOption {
        thisRef.optionParser.addOption(this)
        return this
    }

    override fun getValue(thisRef: OptionParserContainer, property: KProperty<*>): Boolean = value
}
