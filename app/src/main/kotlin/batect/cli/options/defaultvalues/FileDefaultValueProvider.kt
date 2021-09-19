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

package batect.cli.options.defaultvalues

import batect.os.PathResolutionResult
import batect.os.PathResolverFactory
import batect.os.PathType
import java.nio.file.Path

data class FileDefaultValueProvider(val default: String, val pathResolverFactory: PathResolverFactory) : DefaultValueProvider<Path?> {
    private val pathResolver = pathResolverFactory.createResolverForCurrentDirectory()
    private val resolutionResult = pathResolver.resolve(default)

    override val value: PossibleValue<Path?> = when (resolutionResult) {
        is PathResolutionResult.Resolved -> {
            when (resolutionResult.pathType) {
                PathType.File -> PossibleValue.Valid<Path?>(resolutionResult.absolutePath)
                PathType.DoesNotExist -> PossibleValue.Valid<Path?>(null)
                else -> PossibleValue.Invalid("The path '$default' (${resolutionResult.resolutionDescription}) is not a file.")
            }
        }
        is PathResolutionResult.InvalidPath -> PossibleValue.Invalid("The path '$default' is invalid.")
    }

    private val currentStateDescription: String = if (resolutionResult is PathResolutionResult.Resolved && resolutionResult.pathType == PathType.File) {
        "which it currently does"
    } else {
        "which it currently does not"
    }

    override val description: String = "Defaults to '$default' if that file exists ($currentStateDescription)."
}
