/*
    Copyright 2017-2022 Charles Korn.

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

package batect.execution

import batect.config.CacheMount
import batect.config.ExpressionEvaluationContext
import batect.config.ExpressionEvaluationException
import batect.config.LiteralValue
import batect.config.LocalMount
import batect.config.ProjectPaths
import batect.config.VolumeMount
import batect.dockerclient.BindMount
import batect.dockerclient.HostMount
import batect.dockerclient.VolumeReference
import batect.os.PathResolutionResult
import batect.os.PathResolverFactory
import batect.primitives.mapToSet
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import java.nio.file.Files

class VolumeMountResolver(
    private val pathResolverFactory: PathResolverFactory,
    private val expressionEvaluationContext: ExpressionEvaluationContext,
    private val cacheManager: CacheManager,
    private val projectPaths: ProjectPaths
) {
    fun resolve(mounts: Set<VolumeMount>): Set<BindMount> = mounts.mapToSet {
        when (it) {
            is LocalMount -> resolve(it)
            is CacheMount -> resolve(it)
        }
    }

    fun resolve(mount: LocalMount): HostMount {
        val evaluatedLocalPath = evaluateLocalPath(mount)

        if (evaluatedLocalPath == "/var/run/docker.sock") {
            return HostMount(evaluatedLocalPath.toPath(), mount.containerPath, mount.options)
        }

        val pathResolver = pathResolverFactory.createResolver(mount.pathResolutionContext)

        return when (val resolvedLocalPath = pathResolver.resolve(evaluatedLocalPath)) {
            is PathResolutionResult.Resolved -> HostMount(resolvedLocalPath.absolutePath.toOkioPath(), mount.containerPath, mount.options)
            else -> {
                val expressionDisplay = if (mount.localPath is LiteralValue) {
                    "'${mount.localPath.value}'"
                } else {
                    "expression '${mount.localPath.originalExpression}' (evaluated as '$evaluatedLocalPath')"
                }

                throw VolumeMountResolutionException("Could not resolve volume mount path: $expressionDisplay is not a valid path.")
            }
        }
    }

    private fun evaluateLocalPath(mount: LocalMount): String {
        try {
            return mount.localPath.evaluate(expressionEvaluationContext)
        } catch (e: ExpressionEvaluationException) {
            throw VolumeMountResolutionException("Could not resolve volume mount path: expression '${mount.localPath.originalExpression}' could not be evaluated: ${e.message}", e)
        }
    }

    fun resolve(mount: CacheMount): BindMount = when (cacheManager.cacheType) {
        CacheType.Volume -> batect.dockerclient.VolumeMount(
            VolumeReference("batect-cache-${cacheManager.projectCacheKey}-${mount.name}"),
            mount.containerPath,
            mount.options
        )
        CacheType.Directory -> {
            val path = projectPaths.cacheDirectory.resolve(mount.name)
            Files.createDirectories(path)

            HostMount(path.toOkioPath(), mount.containerPath, mount.options)
        }
    }
}

class VolumeMountResolutionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
