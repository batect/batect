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

package batect.execution.model.steps.runners

import batect.config.VariableExpression
import batect.config.VariableExpressionEvaluationException
import batect.docker.ImageBuildFailedException
import batect.docker.client.DockerImageBuildProgress
import batect.docker.client.DockerImagesClient
import batect.execution.CancellationContext
import batect.execution.ConfigVariablesProvider
import batect.execution.RunOptions
import batect.execution.model.events.ImageBuildFailedEvent
import batect.execution.model.events.ImageBuildProgressEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.BuildImageStep
import batect.os.HostEnvironmentVariables
import batect.os.SystemInfo
import batect.os.proxies.ProxyEnvironmentVariablesProvider
import batect.ui.containerio.ContainerIOStreamingOptions

class BuildImageStepRunner(
    private val imagesClient: DockerImagesClient,
    private val proxyEnvironmentVariablesProvider: ProxyEnvironmentVariablesProvider,
    private val configVariablesProvider: ConfigVariablesProvider,
    private val cancellationContext: CancellationContext,
    private val ioStreamingOptions: ContainerIOStreamingOptions,
    private val runOptions: RunOptions,
    private val systemInfo: SystemInfo,
    private val hostEnvironmentVariables: HostEnvironmentVariables
) {
    fun run(step: BuildImageStep, eventSink: TaskEventSink) {
        try {
            val onStatusUpdate = { p: DockerImageBuildProgress ->
                eventSink.postEvent(ImageBuildProgressEvent(step.source, p))
            }

            val buildArgs = buildTimeProxyEnvironmentVariablesForOptions(runOptions) + substituteBuildArgs(step.source.buildArgs)

            val image = imagesClient.build(
                step.source.buildDirectory,
                buildArgs,
                step.source.dockerfilePath,
                step.imageTags,
                ioStreamingOptions.stdoutForImageBuild(step.source),
                cancellationContext,
                onStatusUpdate
            )

            eventSink.postEvent(ImageBuiltEvent(step.source, image))
        } catch (e: ImageBuildFailedException) {
            val message = e.message ?: ""

            eventSink.postEvent(ImageBuildFailedEvent(step.source, message.replace("\n", systemInfo.lineSeparator)))
        }
    }

    private fun substituteBuildArgs(original: Map<String, VariableExpression>): Map<String, String> =
        original.mapValues { (name, value) -> evaluateBuildArgValue(name, value) }

    private fun evaluateBuildArgValue(name: String, expression: VariableExpression): String {
        try {
            return expression.evaluate(hostEnvironmentVariables, configVariablesProvider.configVariableValues)
        } catch (e: VariableExpressionEvaluationException) {
            throw ImageBuildFailedException("The value for the build arg '$name' cannot be evaluated: ${e.message}", e)
        }
    }

    private fun buildTimeProxyEnvironmentVariablesForOptions(runOptions: RunOptions): Map<String, String> = if (runOptions.propagateProxyEnvironmentVariables) {
        proxyEnvironmentVariablesProvider.getProxyEnvironmentVariables(emptySet())
    } else {
        emptyMap()
    }
}
