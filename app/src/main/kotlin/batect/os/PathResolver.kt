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

package batect.os

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.Properties

data class PathResolver(val relativeTo: Path, private val systemProperties: Properties = System.getProperties()) {
    private val homeDir = getPath(systemProperties.getProperty("user.home"))

    fun resolve(path: String): PathResolutionResult {
        try {
            val originalPath = resolveHomeDir(getPath(path))
            val resolvedPath = relativeTo.resolve(originalPath).normalize()
            return PathResolutionResult.Resolved(resolvedPath, pathType(resolvedPath))
        } catch (e: InvalidPathException) {
            return PathResolutionResult.InvalidPath
        }
    }

    private fun getPath(path: String): Path = relativeTo.fileSystem.getPath(path)

    private fun resolveHomeDir(path: Path): Path {
        val homeSymbol = getPath("~")

        if (path.startsWith(homeSymbol)) {
            return homeDir.resolve(homeSymbol.relativize(path))
        } else {
            return path
        }
    }

    private fun pathType(path: Path): PathType {
        return when {
            !Files.exists(path) -> PathType.DoesNotExist
            Files.isRegularFile(path) -> PathType.File
            Files.isDirectory(path) -> PathType.Directory
            else -> PathType.Other
        }
    }
}

sealed class PathResolutionResult {
    data class Resolved(val absolutePath: Path, val pathType: PathType) : PathResolutionResult()
    object InvalidPath : PathResolutionResult()
}

enum class PathType {
    DoesNotExist,
    File,
    Directory,
    Other
}
