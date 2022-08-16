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

package batect.cli.options

import batect.utils.asHumanReadableList

class EnumValueConverter<T : Enum<T>>(val possibleValues: Map<String, T>) : ValueConverter<T> {
    override fun convert(value: String): ValueConversionResult<T> {
        val convertedValue = possibleValues[value]

        if (convertedValue != null) {
            return ValueConversionResult.ConversionSucceeded(convertedValue)
        }

        val validOptions = possibleValues.keys
            .sorted()
            .map { "'$it'" }

        val optionsDescription = validOptions.asHumanReadableList("or")

        return ValueConversionResult.ConversionFailed("Value must be one of $optionsDescription.")
    }
}
