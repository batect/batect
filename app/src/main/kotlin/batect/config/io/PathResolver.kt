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

package batect.config.io

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

class PathResolverFactory {
    fun createResolver(relativeTo: Path): PathResolver = PathResolver(relativeTo)
}

data class PathResolver(val relativeTo: Path) {
    fun resolve(path: String): PathResolutionResult {
        try {
            val resolvedPath = relativeTo.resolve(path).normalize()

            if (!Files.exists(resolvedPath)) {
                return PathResolutionResult.NotFound(resolvedPath.toString())
            }

            if (Files.isDirectory(resolvedPath)) {
                return PathResolutionResult.ResolvedToDirectory(resolvedPath.toString())
            }

            if (Files.isRegularFile(resolvedPath)) {
                return PathResolutionResult.ResolvedToFile(resolvedPath.toString())
            }

            throw RuntimeException("Path '$path' represents neither a directory nor a file.")
        } catch (e: InvalidPathException) {
            return PathResolutionResult.InvalidPath
        }
    }
}

sealed class PathResolutionResult {
    data class ResolvedToFile(val path: String) : PathResolutionResult()
    data class ResolvedToDirectory(val path: String) : PathResolutionResult()
    data class NotFound(val path: String) : PathResolutionResult()
    object InvalidPath : PathResolutionResult()
}
