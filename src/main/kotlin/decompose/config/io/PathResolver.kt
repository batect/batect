package decompose.config.io

import java.nio.file.Path

interface PathResolverFactory {
    fun createResolver(relativeTo: Path): PathResolver
}

interface PathResolver {
    fun resolve(path: String): PathResolutionResult
}

sealed class PathResolutionResult
data class ResolvedToFile(val path: String) : PathResolutionResult()
data class ResolvedToDirectory(val path: String) : PathResolutionResult()
data class NotFound(val path: String) : PathResolutionResult()
object InvalidPath : PathResolutionResult()
