/*
   Copyright 2017-2018 Charles Korn.

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

object ValueConverters {
    fun string(value: String): ValueConversionResult<String> = ValueConversionResult.ConversionSucceeded(value)

    fun positiveInteger(value: String): ValueConversionResult<Int> {
        try {
            val parsedValue = Integer.parseInt(value)

            if (parsedValue <= 0) {
                return ValueConversionResult.ConversionFailed("Value must be positive.")
            }

            return ValueConversionResult.ConversionSucceeded(parsedValue)
        } catch (_: NumberFormatException) {
            return ValueConversionResult.ConversionFailed("Value is not a valid integer.")
        }
    }
}
