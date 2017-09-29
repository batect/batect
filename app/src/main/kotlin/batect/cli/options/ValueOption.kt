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

package batect.cli.options

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// Why the two generic parameters here?
// StorageType defines how the value is stored - for example, for a String value with a null default, StorageType would be String?.
// ValueType defines what type user-provided values should be converted to - for example, String.
// This allows us to provide type safety but also differentiate between 'not provided' (null) and 'provided' where needed.
class ValueOption<StorageType, ValueType : StorageType>(longName: String,
                                                        description: String,
                                                        val defaultValueProvider: DefaultValueProvider<StorageType>,
                                                        val valueConverter: (String) -> ValueConversionResult<ValueType>,
                                                        shortName: Char? = null
) : OptionDefinition(longName, description, shortName), ReadOnlyProperty<OptionParserContainer, StorageType> {

    var value: StorageType = defaultValueProvider.value
        private set

    override fun parseValue(args: Iterable<String>): OptionParsingResult {
        val arg = args.first()
        val argName = arg.substringBefore("=")

        val useNextArgumentForValue = !arg.contains("=")
        val argValue = if (useNextArgumentForValue) {
            if (args.count() == 1) return OptionParsingResult.InvalidOption("Option '$arg' requires a value to be provided, either in the form '$argName=<value>' or '$argName <value>'.")
            args.elementAt(1)
        } else {
            val value = arg.drop(2).substringAfter("=", "")
            if (value == "") return OptionParsingResult.InvalidOption("Option '$arg' is in an invalid format, you must provide a value after '='.")
            value
        }

        val conversionResult = valueConverter(argValue)

        return when (conversionResult) {
            is ValueConversionResult.ConversionFailed -> OptionParsingResult.InvalidOption("The value '$argValue' for option '$arg' is invalid: ${conversionResult.message}")
            is ValueConversionResult.ConversionSucceeded -> {
                value = conversionResult.value

                if (useNextArgumentForValue) {
                    OptionParsingResult.ReadOption(2)
                } else {
                    OptionParsingResult.ReadOption(1)
                }
            }
        }
    }

    operator fun provideDelegate(thisRef: OptionParserContainer, property: KProperty<*>): ValueOption<StorageType, ValueType> {
        thisRef.optionParser.addOption(this)
        return this
    }

    override fun getValue(thisRef: OptionParserContainer, property: KProperty<*>): StorageType = value

    override val descriptionForHelp: String
        get() {
            val defaultDescription = defaultValueProvider.description

            if (defaultDescription == "") {
                return description
            } else {
                return "$description ($defaultDescription)"
            }
        }
}

sealed class ValueConversionResult<V> {
    data class ConversionSucceeded<V>(val value: V) : ValueConversionResult<V>()
    data class ConversionFailed<V>(val message: String) : ValueConversionResult<V>()
}
