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

package batect.cli.options.defaultvalues

import batect.cli.options.OptionValueSource
import batect.cli.options.ValueConversionResult
import batect.cli.options.ValueConverter
import batect.os.HostEnvironmentVariables

class EnvironmentVariableDefaultValueProviderFactory(private val environment: HostEnvironmentVariables) {
    fun <StorageType, ValueType : StorageType> create(
        name: String,
        fallback: StorageType,
        valueConverter: ValueConverter<ValueType>
    ) = create(name, fallback, fallback.toString(), valueConverter)

    fun <StorageType, ValueType : StorageType> create(
        name: String,
        fallback: StorageType,
        fallbackDisplay: String = fallback.toString(),
        valueConverter: ValueConverter<ValueType>
    ): DefaultValueProvider<StorageType> = object : DefaultValueProvider<StorageType> {
        override val value: PossibleValue<StorageType>
            get() {
                val environmentVariableValue = environment[name]

                if (environmentVariableValue == null) {
                    return PossibleValue.Valid(fallback)
                }

                return when (val conversionResult = valueConverter.convert(environmentVariableValue)) {
                    is ValueConversionResult.ConversionSucceeded -> PossibleValue.Valid(conversionResult.value)
                    is ValueConversionResult.ConversionFailed -> PossibleValue.Invalid("The value of the $name environment variable ('$environmentVariableValue') is invalid: ${conversionResult.message}")
                }
            }

        override val description: String
            get() = "Defaults to the value of the $name environment variable (which is currently $currentStateDescription)$fallbackDescription."

        override val valueSource: OptionValueSource
            get() = if (environment.containsKey(name)) OptionValueSource.Environment else OptionValueSource.Default

        private val fallbackDescription = if (fallback == null) {
            when (fallbackDisplay) {
                null.toString() -> ""
                else -> " or $fallbackDisplay if $name is not set"
            }
        } else {
            " or '$fallbackDisplay' if $name is not set"
        }

        private val currentStateDescription = if (environment.containsKey(name)) {
            "'${environment.getValue(name)}'"
        } else {
            "not set"
        }
    }
}
