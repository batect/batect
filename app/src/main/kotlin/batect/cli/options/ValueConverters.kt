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

package batect.cli.options

import batect.os.PathResolutionResult
import batect.os.PathResolverFactory
import batect.os.PathType
import java.nio.file.Path
import java.util.Locale

object ValueConverters {
    fun string(value: String): ValueConversionResult<String> = ValueConversionResult.ConversionSucceeded(value)

    fun boolean(value: String): ValueConversionResult<Boolean> {
        if (value in setOf("1", "yes", "true", "YES", "TRUE")) {
            return ValueConversionResult.ConversionSucceeded(true)
        }

        if (value in setOf("0", "no", "false", "NO", "FALSE")) {
            return ValueConversionResult.ConversionSucceeded(false)
        }

        return ValueConversionResult.ConversionFailed("Value is not a recognised boolean value.")
    }

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

    inline fun <reified T : Enum<T>> optionalEnum(): (String) -> ValueConversionResult<T?> {
        val valueMap = enumValues<T>()
            .associate { it.name.toLowerCase(Locale.ROOT) to it }

        return { value ->
            val convertedValue = valueMap.get(value)

            if (convertedValue != null) {
                ValueConversionResult.ConversionSucceeded(convertedValue)
            } else {
                val validOptions = valueMap.keys
                    .sorted()
                    .map { "'$it'" }

                val optionsDescription = validOptions.dropLast(1).joinToString(", ") + " or " + validOptions.last()

                ValueConversionResult.ConversionFailed("Value must be one of $optionsDescription.")
            }
        }
    }

    fun pathToFile(pathResolverFactory: PathResolverFactory, mustExist: Boolean = false): (String) -> ValueConversionResult<Path> {
        val resolver = pathResolverFactory.createResolverForCurrentDirectory()

        return { value ->
            when (val result = resolver.resolve(value)) {
                is PathResolutionResult.Resolved -> when (result.pathType) {
                    PathType.File -> ValueConversionResult.ConversionSucceeded(result.absolutePath)
                    PathType.DoesNotExist -> if (mustExist) {
                        ValueConversionResult.ConversionFailed("The file '$value' (resolved to '${result.absolutePath}') does not exist.")
                    } else {
                        ValueConversionResult.ConversionSucceeded(result.absolutePath)
                    }
                    PathType.Directory -> ValueConversionResult.ConversionFailed("The path '$value' (resolved to '${result.absolutePath}') refers to a directory.")
                    PathType.Other -> ValueConversionResult.ConversionFailed("The path '$value' (resolved to '${result.absolutePath}') refers to something other than a file.")
                }
                is PathResolutionResult.InvalidPath -> ValueConversionResult.ConversionFailed("'$value' is not a valid path.")
            }
        }
    }

    fun pathToDirectory(pathResolverFactory: PathResolverFactory, mustExist: Boolean = false): (String) -> ValueConversionResult<Path> {
        val resolver = pathResolverFactory.createResolverForCurrentDirectory()

        return { value ->
            when (val result = resolver.resolve(value)) {
                is PathResolutionResult.Resolved -> when (result.pathType) {
                    PathType.File -> ValueConversionResult.ConversionFailed("The path '$value' (resolved to '${result.absolutePath}') refers to a file.")
                    PathType.DoesNotExist -> if (mustExist) {
                        ValueConversionResult.ConversionFailed("The directory '$value' (resolved to '${result.absolutePath}') does not exist.")
                    } else {
                        ValueConversionResult.ConversionSucceeded(result.absolutePath)
                    }
                    PathType.Directory -> ValueConversionResult.ConversionSucceeded(result.absolutePath)
                    PathType.Other -> ValueConversionResult.ConversionFailed("The path '$value' (resolved to '${result.absolutePath}') refers to something other than a directory.")
                }
                is PathResolutionResult.InvalidPath -> ValueConversionResult.ConversionFailed("'$value' is not a valid path.")
            }
        }
    }
}
