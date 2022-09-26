/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.execution.model.steps.runners

import batect.cli.CommandLineOptions
import batect.config.BuildImage
import batect.config.BuildSecret
import batect.config.EnvironmentSecret
import batect.config.Expression
import batect.config.ExpressionEvaluationContext
import batect.config.ExpressionEvaluationException
import batect.config.FileSecret
import batect.config.SSHAgent
import batect.config.TaskSpecialisedConfiguration
import batect.docker.ImageBuildProgressAggregator
import batect.dockerclient.BuilderVersion
import batect.dockerclient.DockerClient
import batect.dockerclient.DockerClientException
import batect.dockerclient.EnvironmentBuildSecret
import batect.dockerclient.FileBuildSecret
import batect.dockerclient.ImageBuildFailedException
import batect.dockerclient.ImageBuildSpec
import batect.dockerclient.io.SinkTextOutput
import batect.execution.model.events.ImageBuildFailedEvent
import batect.execution.model.events.ImageBuildProgressEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.BuildImageStep
import batect.io.Tee
import batect.logging.Logger
import batect.os.PathResolutionContext
import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.os.PathResolverFactory
import batect.os.PathType
import batect.os.SystemInfo
import batect.primitives.CancellationContext
import batect.primitives.runBlocking
import batect.proxies.ProxyEnvironmentVariablesProvider
import batect.telemetry.TelemetryCaptor
import batect.telemetry.addSpan
import batect.ui.containerio.ContainerIOStreamingOptions
import okio.Buffer
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

class BuildImageStepRunner(
    private val config: TaskSpecialisedConfiguration,
    private val dockerClient: DockerClient,
    private val proxyEnvironmentVariablesProvider: ProxyEnvironmentVariablesProvider,
    private val pathResolverFactory: PathResolverFactory,
    private val expressionEvaluationContext: ExpressionEvaluationContext,
    private val cancellationContext: CancellationContext,
    private val ioStreamingOptions: ContainerIOStreamingOptions,
    private val commandLineOptions: CommandLineOptions,
    private val builderVersion: BuilderVersion,
    private val systemInfo: SystemInfo,
    private val telemetryCaptor: TelemetryCaptor,
    private val logger: Logger
) {
    fun run(step: BuildImageStep, eventSink: TaskEventSink) {
        val stdoutBuffer = Buffer()

        try {
            val spec = createImageBuildSpec(step)
            val uiStdout = ioStreamingOptions.stdoutForImageBuild(step.container)
            val combinedStdout = if (uiStdout == null) stdoutBuffer else Tee(uiStdout, stdoutBuffer)

            val image = telemetryCaptor.addSpan("BuildImage") {
                cancellationContext.runBlocking {
                    val reporter = ImageBuildProgressAggregator()

                    dockerClient.buildImage(spec, SinkTextOutput(combinedStdout)) { event ->
                        val progressUpdate = reporter.processProgressUpdate(event)

                        if (progressUpdate != null) {
                            eventSink.postEvent(ImageBuildProgressEvent(step.container, progressUpdate))
                        }
                    }
                }
            }

            eventSink.postEvent(ImageBuiltEvent(step.container, image))
        } catch (e: DockerClientException) {
            val output = stdoutBuffer.readUtf8()
            val messageBuilder = StringBuilder()

            if (e.message != null) {
                messageBuilder.append(e.message)
            }

            if (output.isNotBlank()) {
                if (messageBuilder.isNotEmpty()) {
                    messageBuilder.append(" ")
                }

                messageBuilder.append("Output from Docker was:\n")
                messageBuilder.append(output)
            }

            val message = messageBuilder.replace("\n".toRegex(), systemInfo.lineSeparator)

            logger.error {
                message("Building image failed.")
                exception(e)
            }

            eventSink.postEvent(ImageBuildFailedEvent(step.container, message))
        }
    }

    private fun createImageBuildSpec(step: BuildImageStep): ImageBuildSpec {
        val buildConfig = step.container.imageSource as BuildImage
        val pathResolver = pathResolverFactory.createResolver(buildConfig.pathResolutionContext)
        val buildArgs = buildTimeProxyEnvironmentVariablesForOptions() + substituteBuildArgs(buildConfig.buildArgs)
        val imageTags = commandLineOptions.imageTags.getOrDefault(step.container.name, emptySet()) + imageTagFor(step)
        val buildDirectory = resolveBuildDirectory(buildConfig, pathResolver)
        val dockerfilePath = resolveDockerfilePath(buildDirectory, buildConfig.dockerfilePath, buildConfig.pathResolutionContext)

        val builder = ImageBuildSpec.Builder(buildDirectory.toOkioPath())
            .withBuildArgs(buildArgs)
            .withDockerfile(dockerfilePath.toOkioPath())
            .withImageTags(imageTags)
            .withBuilder(builderVersion)

        if (buildConfig.imagePullPolicy.forciblyPull) {
            builder.withBaseImageAlwaysPulled()
        }

        if (buildConfig.targetStage != null) {
            builder.withTargetBuildStage(buildConfig.targetStage)
        }

        buildConfig.sshAgents.forEach { agent ->
            builder.withSSHAgent(resolveSSHAgent(agent, pathResolver))
        }

        buildConfig.secrets.forEach { id, secret ->
            builder.withSecret(id, resolveSecret(id, secret, pathResolver))
        }

        return builder.build()
    }

    private fun resolveBuildDirectory(source: BuildImage, pathResolver: PathResolver): Path {
        val evaluatedBuildDirectory = evaluateBuildDirectory(source.buildDirectory)

        when (val resolutionResult = pathResolver.resolve(evaluatedBuildDirectory)) {
            is PathResolutionResult.Resolved -> when (resolutionResult.pathType) {
                PathType.Directory -> return resolutionResult.absolutePath
                PathType.DoesNotExist -> throw ImageBuildFailedException("Build directory '${resolutionResult.originalPath}' (${resolutionResult.resolutionDescription}) does not exist.")
                else -> throw ImageBuildFailedException("Build directory '${resolutionResult.originalPath}' (${resolutionResult.resolutionDescription}) is not a directory.")
            }
            is PathResolutionResult.InvalidPath -> throw ImageBuildFailedException("Build directory '${resolutionResult.originalPath}' is not a valid path.")
        }
    }

    private fun evaluateBuildDirectory(expression: Expression): String {
        try {
            return expression.evaluate(expressionEvaluationContext)
        } catch (e: ExpressionEvaluationException) {
            throw ImageBuildFailedException("The value for the build directory cannot be evaluated: ${e.message}", e)
        }
    }

    private fun resolveDockerfilePath(buildDirectory: Path, relativeDockerfilePath: String, pathResolutionContext: PathResolutionContext): Path {
        val resolvedDockerfilePath = buildDirectory.resolve(relativeDockerfilePath)

        if (!Files.exists(resolvedDockerfilePath)) {
            throw ImageBuildFailedException("The Dockerfile '$relativeDockerfilePath' does not exist in the build directory ${pathResolutionContext.getPathForDisplay(buildDirectory)}")
        }

        if (!Files.isRegularFile(resolvedDockerfilePath)) {
            throw ImageBuildFailedException("The Dockerfile '$relativeDockerfilePath' is not a file.")
        }

        if (!resolvedDockerfilePath.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(buildDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS))) {
            throw ImageBuildFailedException("The Dockerfile '$relativeDockerfilePath' is not a child of the build directory ${pathResolutionContext.getPathForDisplay(buildDirectory)}")
        }

        return resolvedDockerfilePath
    }

    private fun substituteBuildArgs(original: Map<String, Expression>): Map<String, String> =
        original.mapValues { (name, value) -> evaluateBuildArgValue(name, value) }

    private fun evaluateBuildArgValue(name: String, expression: Expression): String {
        try {
            return expression.evaluate(expressionEvaluationContext)
        } catch (e: ExpressionEvaluationException) {
            throw ImageBuildFailedException("The value for the build arg '$name' cannot be evaluated: ${e.message}", e)
        }
    }

    private fun resolveSSHAgent(agent: SSHAgent, pathResolver: PathResolver): batect.dockerclient.SSHAgent {
        val resolvedPaths = agent.paths.mapIndexed { index, path -> resolveSSHAgentPath(agent, path, index, pathResolver).toOkioPath() }

        return batect.dockerclient.SSHAgent(agent.id, resolvedPaths.toSet())
    }

    private fun resolveSSHAgentPath(agent: SSHAgent, pathExpression: Expression, index: Int, pathResolver: PathResolver): Path {
        try {
            val evaluated = pathExpression.evaluate(expressionEvaluationContext)

            when (val resolved = pathResolver.resolve(evaluated)) {
                is PathResolutionResult.Resolved -> return resolved.absolutePath
                is PathResolutionResult.InvalidPath -> throw ImageBuildFailedException("The value '$evaluated' for path ${index + 1} for SSH agent '${agent.id}' is not a valid path.")
            }
        } catch (e: ExpressionEvaluationException) {
            throw ImageBuildFailedException("The value for path ${index + 1} for SSH agent '${agent.id}' cannot be evaluated: ${e.message}", e)
        }
    }

    private fun resolveSecret(id: String, secret: BuildSecret, pathResolver: PathResolver): batect.dockerclient.BuildSecret {
        return when (secret) {
            is EnvironmentSecret -> EnvironmentBuildSecret(secret.sourceEnvironmentVariableName)
            is FileSecret -> FileBuildSecret(resolveFileSecretPath(id, secret, pathResolver).toOkioPath())
        }
    }

    private fun resolveFileSecretPath(id: String, secret: FileSecret, pathResolver: PathResolver): Path {
        try {
            val evaluated = secret.sourceFile.evaluate(expressionEvaluationContext)

            when (val resolved = pathResolver.resolve(evaluated)) {
                is PathResolutionResult.Resolved -> when (resolved.pathType) {
                    PathType.File -> return resolved.absolutePath
                    PathType.DoesNotExist -> throw ImageBuildFailedException("Path '${resolved.originalPath}' (${resolved.resolutionDescription}) for secret '$id' does not exist.")
                    else -> throw ImageBuildFailedException("Path '${resolved.originalPath}' (${resolved.resolutionDescription}) for secret '$id' is not a directory.")
                }
                is PathResolutionResult.InvalidPath -> throw ImageBuildFailedException("Path '$evaluated' for secret '$id' is not a valid path.")
            }
        } catch (e: ExpressionEvaluationException) {
            throw ImageBuildFailedException("Path for secret '$id' cannot be evaluated: ${e.message}", e)
        }
    }

    private fun buildTimeProxyEnvironmentVariablesForOptions(): Map<String, String> = if (commandLineOptions.dontPropagateProxyEnvironmentVariables) {
        emptyMap()
    } else {
        proxyEnvironmentVariablesProvider.getProxyEnvironmentVariables(emptySet())
    }

    private fun imageTagFor(step: BuildImageStep): String = "${config.projectName}-${step.container.name}"
}
