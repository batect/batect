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

package batect.docker.client

import batect.docker.DockerImage
import batect.docker.DockerRegistryCredentialsException
import batect.docker.ImageBuildFailedException
import batect.docker.ImagePullFailedException
import batect.docker.ImageReference
import batect.docker.api.BuilderVersion
import batect.docker.api.ImagesAPI
import batect.docker.api.SessionsAPI
import batect.docker.build.BuildKitConfig
import batect.docker.build.BuildProgress
import batect.docker.build.BuilderConfig
import batect.docker.build.ImageBuildOutputSink
import batect.docker.build.LegacyBuilderConfig
import batect.docker.build.buildkit.BuildKitSessionFactory
import batect.docker.build.legacy.DockerfileParser
import batect.docker.build.legacy.ImageBuildContextFactory
import batect.docker.data
import batect.docker.pull.ImagePullProgress
import batect.docker.pull.ImagePullProgressReporter
import batect.docker.pull.RegistryCredentialsProvider
import batect.logging.LogMessageBuilder
import batect.logging.Logger
import batect.os.PathResolutionContext
import batect.primitives.CancellationContext
import okio.Sink
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

class ImagesClient(
    private val imagesAPI: ImagesAPI,
    private val sessionsAPI: SessionsAPI,
    private val credentialsProvider: RegistryCredentialsProvider,
    private val imageBuildContextFactory: ImageBuildContextFactory,
    private val dockerfileParser: DockerfileParser,
    private val buildKitSessionFactory: BuildKitSessionFactory,
    private val logger: Logger,
    private val imagePullProgressReporterFactory: () -> ImagePullProgressReporter = ::ImagePullProgressReporter
) {

    fun build(
        request: ImageBuildRequest,
        builderVersion: BuilderVersion,
        outputSink: Sink?,
        cancellationContext: CancellationContext,
        onProgressUpdate: (BuildProgress) -> Unit
    ): DockerImage {
        logger.info {
            message("Building image.")
            addRequestData(request)
            data("builderVersion", builderVersion)
        }

        try {
            val resolvedDockerfilePath = request.resolveDockerfilePath() // We do this early so we can show a clear error message if the configured Dockerfile doesn't exist.

            val imageBuildOutputSink = ImageBuildOutputSink(outputSink)
            val builderConfig = createBuilderConfig(builderVersion, request.buildDirectory, request.relativeDockerfilePath, resolvedDockerfilePath, imageBuildOutputSink)
            val session = startSession(builderConfig)

            // Why not use a use() block here? use() suppresses any exceptions thrown by close() if an exception has already been thrown, but we want to allow them to bubble up so that
            // any exceptions from other threads can be propagated by BuildKitSession.close().
            val image = try {
                imagesAPI.build(request.buildArgs, request.relativeDockerfilePath, request.imageTags, request.forcePull, imageBuildOutputSink, builderConfig, cancellationContext, onProgressUpdate)
            } finally {
                session.close()
            }

            logger.info {
                message("Image build succeeded.")
                data("image", image)
            }

            return image
        } catch (e: DockerRegistryCredentialsException) {
            throw ImageBuildFailedException("Could not build image: ${e.message}", e)
        }
    }

    private fun createBuilderConfig(
        builderVersion: BuilderVersion,
        buildDirectory: Path,
        relativeDockerfilePath: String,
        resolvedDockerfilePath: Path,
        imageBuildOutputSink: ImageBuildOutputSink
    ): BuilderConfig = when (builderVersion) {
        BuilderVersion.Legacy -> {
            val baseImageNames = dockerfileParser.extractBaseImageNames(resolvedDockerfilePath)
            val credentials = baseImageNames.mapNotNull { credentialsProvider.getCredentials(it) }.toSet()
            val context = imageBuildContextFactory.createFromDirectory(buildDirectory, relativeDockerfilePath)

            LegacyBuilderConfig(credentials, context)
        }
        BuilderVersion.BuildKit -> BuildKitConfig(buildKitSessionFactory.create(buildDirectory, imageBuildOutputSink))
    }

    private fun startSession(builderConfig: BuilderConfig): AutoCloseable = when (builderConfig) {
        is LegacyBuilderConfig -> AutoCloseable {
            // Nothing to do.
        }
        is BuildKitConfig -> {
            val streams = sessionsAPI.create(builderConfig.session)
            builderConfig.session.start(streams)
            builderConfig.session
        }
    }

    fun pull(
        imageName: String,
        forcePull: Boolean,
        cancellationContext: CancellationContext,
        onProgressUpdate: (ImagePullProgress) -> Unit
    ): DockerImage {
        try {
            val imageReference = ImageReference(imageName)

            if (forcePull || !imagesAPI.hasImage(imageReference)) {
                val credentials = credentialsProvider.getCredentials(imageReference)
                val reporter = imagePullProgressReporterFactory()

                imagesAPI.pull(imageReference, credentials, cancellationContext) { progress ->
                    val progressUpdate = reporter.processProgressUpdate(progress)

                    if (progressUpdate != null) {
                        onProgressUpdate(progressUpdate)
                    }
                }
            }

            return DockerImage(imageName)
        } catch (e: DockerRegistryCredentialsException) {
            throw ImagePullFailedException("Could not pull image '$imageName': ${e.message}", e)
        }
    }
}

private fun LogMessageBuilder.addRequestData(value: ImageBuildRequest) {
    data("buildDirectory", value.buildDirectory)
    data("buildArgs", value.buildArgs)
    data("relativeDockerfilePath", value.relativeDockerfilePath)
    data("imageTags", value.imageTags)
}

data class ImageBuildRequest(
    val buildDirectory: Path,
    val buildArgs: Map<String, String>,
    val relativeDockerfilePath: String,
    val pathResolutionContext: PathResolutionContext,
    val imageTags: Set<String>,
    val forcePull: Boolean
) {
    fun resolveDockerfilePath(): Path {
        val resolvedDockerfilePath = buildDirectory.resolve(relativeDockerfilePath)

        if (!Files.exists(resolvedDockerfilePath)) {
            throw ImageBuildFailedException("Could not build image: the Dockerfile '$relativeDockerfilePath' does not exist in the build directory ${pathResolutionContext.getPathForDisplay(buildDirectory)}")
        }

        if (!resolvedDockerfilePath.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(buildDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS))) {
            throw ImageBuildFailedException("Could not build image: the Dockerfile '$relativeDockerfilePath' is not a child of the build directory ${pathResolutionContext.getPathForDisplay(buildDirectory)}")
        }

        return resolvedDockerfilePath
    }
}
