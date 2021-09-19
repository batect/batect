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

import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.os.PathType
import java.nio.file.Path

class FilePathValueConverter(private val resolver: PathResolver, private val mustExist: Boolean = false) : PathValueConverter {
    override fun convert(value: String): ValueConversionResult<Path> =
        when (val result = resolver.resolve(value)) {
            is PathResolutionResult.Resolved -> when (result.pathType) {
                PathType.File -> ValueConversionResult.ConversionSucceeded(result.absolutePath)
                PathType.DoesNotExist ->
                    if (mustExist) {
                        ValueConversionResult.ConversionFailed("The file '$value' (${result.resolutionDescription}) does not exist.")
                    } else {
                        ValueConversionResult.ConversionSucceeded(result.absolutePath)
                    }
                PathType.Directory -> ValueConversionResult.ConversionFailed("The path '$value' (${result.resolutionDescription}) refers to a directory.")
                PathType.Other -> ValueConversionResult.ConversionFailed("The path '$value' (${result.resolutionDescription}) refers to something other than a file.")
            }
            is PathResolutionResult.InvalidPath -> ValueConversionResult.ConversionFailed("'$value' is not a valid path.")
        }
}
