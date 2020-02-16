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

package batect.execution

import batect.config.LiteralValue
import batect.config.VariableExpressionEvaluationException
import batect.config.VolumeMount
import batect.docker.DockerVolumeMount
import batect.os.HostEnvironmentVariables
import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.utils.mapToSet

class VolumeMountResolver(
    private val pathResolver: PathResolver,
    private val hostEnvironmentVariables: HostEnvironmentVariables,
    private val configVariablesProvider: ConfigVariablesProvider
) {
    fun resolve(mounts: Set<VolumeMount>): Set<DockerVolumeMount> = mounts.mapToSet {
        val evaluatedLocalPath = evaluateLocalPath(it)

        when (val resolvedLocalPath = pathResolver.resolve(evaluatedLocalPath)) {
            is PathResolutionResult.Resolved -> DockerVolumeMount(resolvedLocalPath.absolutePath.toString(), it.containerPath, it.options)
            else -> {
                val expressionDisplay = if (it.localPath is LiteralValue) {
                    "'${it.localPath.value}'"
                } else {
                    "expression '${it.localPath.originalExpression}' (evaluated as '$evaluatedLocalPath')"
                }

                throw VolumeMountResolutionException("Could not resolve volume mount path: $expressionDisplay is not a valid path.")
            }
        }
    }

    private fun evaluateLocalPath(mount: VolumeMount): String {
        try {
            return mount.localPath.evaluate(hostEnvironmentVariables, configVariablesProvider.configVariableValues)
        } catch (e: VariableExpressionEvaluationException) {
            throw VolumeMountResolutionException("Could not resolve volume mount path: expression '${mount.localPath.originalExpression}' could not be evaluated: ${e.message}", e)
        }
    }
}

class VolumeMountResolutionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
