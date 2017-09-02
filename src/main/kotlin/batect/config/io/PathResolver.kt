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
                return NotFound(resolvedPath.toString())
            }

            if (Files.isDirectory(resolvedPath)) {
                return ResolvedToDirectory(resolvedPath.toString())
            }

            if (Files.isRegularFile(resolvedPath)) {
                return ResolvedToFile(resolvedPath.toString())
            }

            throw RuntimeException("Path represents neither a directory nor a file.")
        } catch (e: InvalidPathException) {
            return InvalidPath
        }
    }
}

sealed class PathResolutionResult
data class ResolvedToFile(val path: String) : PathResolutionResult()
data class ResolvedToDirectory(val path: String) : PathResolutionResult()
data class NotFound(val path: String) : PathResolutionResult()
object InvalidPath : PathResolutionResult()
