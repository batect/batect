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

import batect.os.PathResolverFactory
import java.util.Locale

object ValueConverters {
    val string: ValueConverter<String> = ValueConverter { value -> ValueConversionResult.ConversionSucceeded(value) }

    val boolean: ValueConverter<Boolean> = ValueConverter { value ->
        when (value) {
            in setOf("1", "yes", "true", "YES", "TRUE") -> ValueConversionResult.ConversionSucceeded(true)
            in setOf("0", "no", "false", "NO", "FALSE") -> ValueConversionResult.ConversionSucceeded(false)
            else -> ValueConversionResult.ConversionFailed("Value is not a recognised boolean value.")
        }
    }

    val invertingBoolean: ValueConverter<Boolean> = ValueConverter { value ->
        when (val booleanResult = boolean.convert(value)) {
            is ValueConversionResult.ConversionSucceeded -> ValueConversionResult.ConversionSucceeded(!booleanResult.value)
            is ValueConversionResult.ConversionFailed -> booleanResult
        }
    }

    val positiveInteger: ValueConverter<Int> = ValueConverter { value ->
        try {
            val parsedValue = Integer.parseInt(value)

            if (parsedValue <= 0) {
                ValueConversionResult.ConversionFailed("Value must be positive.")
            } else {
                ValueConversionResult.ConversionSucceeded(parsedValue)
            }
        } catch (_: NumberFormatException) {
            ValueConversionResult.ConversionFailed("Value is not a valid integer.")
        }
    }

    inline fun <reified T : Enum<T>> enum(): ValueConverter<T> {
        val valueMap = enumValues<T>()
            .associate { it.name.lowercase(Locale.ROOT) to it }

        return EnumValueConverter<T>(valueMap)
    }

    fun pathToFile(pathResolverFactory: PathResolverFactory, mustExist: Boolean = false): FilePathValueConverter {
        val resolver = pathResolverFactory.createResolverForCurrentDirectory()

        return FilePathValueConverter(resolver, mustExist)
    }

    fun pathToDirectory(pathResolverFactory: PathResolverFactory, mustExist: Boolean = false): DirectoryPathValueConverter {
        val resolver = pathResolverFactory.createResolverForCurrentDirectory()

        return DirectoryPathValueConverter(resolver, mustExist)
    }
}
