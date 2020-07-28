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

package batect.docker.client

import batect.docker.DockerImage
import batect.docker.DockerRegistryCredentialsException
import batect.docker.ImageBuildFailedException
import batect.docker.ImagePullFailedException
import batect.docker.api.ImagesAPI
import batect.docker.build.DockerImageBuildContextFactory
import batect.docker.build.DockerfileParser
import batect.docker.data
import batect.docker.pull.DockerImageProgress
import batect.docker.pull.DockerImageProgressReporter
import batect.docker.pull.DockerRegistryCredentialsProvider
import batect.logging.Logger
import batect.os.PathResolutionContext
import batect.primitives.CancellationContext
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okio.Sink

class DockerImagesClient(
    private val api: ImagesAPI,
    private val credentialsProvider: DockerRegistryCredentialsProvider,
    private val imageBuildContextFactory: DockerImageBuildContextFactory,
    private val dockerfileParser: DockerfileParser,
    private val logger: Logger,
    private val imageProgressReporterFactory: () -> DockerImageProgressReporter = ::DockerImageProgressReporter
) {

    fun build(
        buildDirectory: Path,
        buildArgs: Map<String, String>,
        dockerfilePath: String,
        pathResolutionContext: PathResolutionContext,
        imageTags: Set<String>,
        outputSink: Sink?,
        cancellationContext: CancellationContext,
        onStatusUpdate: (DockerImageBuildProgress) -> Unit
    ): DockerImage {
        logger.info {
            message("Building image.")
            data("buildDirectory", buildDirectory)
            data("buildArgs", buildArgs)
            data("dockerfilePath", dockerfilePath)
            data("imageTags", imageTags)
        }

        try {
            val resolvedDockerfilePath = buildDirectory.resolve(dockerfilePath)

            if (!Files.exists(resolvedDockerfilePath)) {
                throw ImageBuildFailedException("Could not build image: the Dockerfile '$dockerfilePath' does not exist in the build directory ${pathResolutionContext.getPathForDisplay(buildDirectory)}")
            }

            if (!resolvedDockerfilePath.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(buildDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS))) {
                throw ImageBuildFailedException("Could not build image: the Dockerfile '$dockerfilePath' is not a child of the build directory ${pathResolutionContext.getPathForDisplay(buildDirectory)}")
            }

            val context = imageBuildContextFactory.createFromDirectory(buildDirectory, dockerfilePath)
            val baseImageNames = dockerfileParser.extractBaseImageNames(resolvedDockerfilePath)
            val credentials = baseImageNames.mapNotNull { credentialsProvider.getCredentials(it) }.toSet()

            val reporter = imageProgressReporterFactory()
            var lastStepProgressUpdate: DockerImageBuildProgress? = null

            val image = api.build(context, buildArgs, dockerfilePath, imageTags, credentials, outputSink, cancellationContext) { line ->
                logger.debug {
                    message("Received output from Docker during image build.")
                    data("outputLine", line.toString())
                }

                val stepProgress = DockerImageBuildProgress.fromBuildOutput(line)

                if (stepProgress != null) {
                    lastStepProgressUpdate = stepProgress
                    onStatusUpdate(lastStepProgressUpdate!!)
                }

                val pullProgress = reporter.processProgressUpdate(line)

                if (pullProgress != null && lastStepProgressUpdate != null) {
                    lastStepProgressUpdate = lastStepProgressUpdate!!.copy(progress = pullProgress)
                    onStatusUpdate(lastStepProgressUpdate!!)
                }
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

    fun pull(imageName: String, cancellationContext: CancellationContext, onProgressUpdate: (DockerImageProgress) -> Unit): DockerImage {
        try {
            if (!api.hasImage(imageName)) {
                val credentials = credentialsProvider.getCredentials(imageName)
                val reporter = imageProgressReporterFactory()

                api.pull(imageName, credentials, cancellationContext) { progress ->
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

@Serializable
data class DockerImageBuildProgress(val currentStep: Int, val totalSteps: Int, val message: String, val progress: DockerImageProgress?) {
    companion object {
        private val buildStepLineRegex = """^Step (\d+)/(\d+) : (.*)$""".toRegex()

        fun fromBuildOutput(line: JsonObject): DockerImageBuildProgress? {
            val output = line.getPrimitiveOrNull("stream")?.content

            if (output == null) {
                return null
            }

            val stepLineMatch = buildStepLineRegex.matchEntire(output)

            if (stepLineMatch == null) {
                return null
            }

            return DockerImageBuildProgress(stepLineMatch.groupValues[1].toInt(), stepLineMatch.groupValues[2].toInt(), stepLineMatch.groupValues[3], null)
        }
    }
}
