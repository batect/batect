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
import batect.docker.Json
import batect.docker.api.ImagesAPI
import batect.docker.build.DockerImageBuildContext
import batect.docker.build.DockerImageBuildContextFactory
import batect.docker.build.DockerfileParser
import batect.docker.pull.DockerImageProgress
import batect.docker.pull.DockerImageProgressReporter
import batect.docker.pull.DockerRegistryCredentials
import batect.docker.pull.DockerRegistryCredentialsProvider
import batect.os.PathResolutionContext
import batect.primitives.CancellationContext
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withCause
import batect.testutils.withMessage
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.nio.file.Files
import kotlinx.serialization.json.JsonObject
import okio.Sink
import org.mockito.invocation.InvocationOnMock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DockerImagesClientSpec : Spek({
    describe("a Docker images client") {
        val api by createForEachTest { mock<ImagesAPI>() }
        val credentialsProvider by createForEachTest { mock<DockerRegistryCredentialsProvider>() }
        val imageBuildContextFactory by createForEachTest { mock<DockerImageBuildContextFactory>() }
        val dockerfileParser by createForEachTest { mock<DockerfileParser>() }
        val logger by createLoggerForEachTestWithoutCustomSerializers()
        val imageProgressReporter by createForEachTest { mock<DockerImageProgressReporter>() }
        val imageProgressReporterFactory = { imageProgressReporter }
        val client by createForEachTest { DockerImagesClient(api, credentialsProvider, imageBuildContextFactory, dockerfileParser, logger, imageProgressReporterFactory) }

        describe("building an image") {
            val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
            val buildDirectory by createForEachTest { fileSystem.getPath("/path/to/build/dir") }
            val buildArgs = mapOf(
                "some_name" to "some_value",
                "some_other_name" to "some_other_value"
            )

            val dockerfilePath = "some-Dockerfile-path"
            val imageTags = setOf("some_image_tag", "some_other_image_tag")
            val forcePull = true

            val pathResolutionContext by createForEachTest {
                mock<PathResolutionContext> {
                    on { getPathForDisplay(buildDirectory) } doReturn "<a nicely formatted version of the build directory>"
                }
            }

            val outputSink by createForEachTest { mock<Sink>() }
            val cancellationContext by createForEachTest { mock<CancellationContext>() }
            val context = DockerImageBuildContext(emptySet())

            given("the Dockerfile exists") {
                val resolvedDockerfilePath by createForEachTest { buildDirectory.resolve(dockerfilePath) }

                beforeEachTest {
                    Files.createDirectories(buildDirectory)
                    Files.createFile(resolvedDockerfilePath)

                    whenever(imageBuildContextFactory.createFromDirectory(buildDirectory, dockerfilePath)).doReturn(context)
                    whenever(dockerfileParser.extractBaseImageNames(resolvedDockerfilePath)).doReturn(setOf("nginx:1.13.0", "some-other-image:2.3.4"))
                }

                given("getting the credentials for the base image succeeds") {
                    val image1Credentials = mock<DockerRegistryCredentials>()
                    val image2Credentials = mock<DockerRegistryCredentials>()

                    beforeEachTest {
                        whenever(credentialsProvider.getCredentials("nginx:1.13.0")).doReturn(image1Credentials)
                        whenever(credentialsProvider.getCredentials("some-other-image:2.3.4")).doReturn(image2Credentials)
                    }

                    on("a successful build") {
                        val output = """
                        |{"stream":"Step 1/5 : FROM nginx:1.13.0"}
                        |{"status":"pulling the image"}
                        |{"stream":"\n"}
                        |{"stream":" ---\u003e 3448f27c273f\n"}
                        |{"stream":"Step 2/5 : RUN apt update \u0026\u0026 apt install -y curl \u0026\u0026 rm -rf /var/lib/apt/lists/*"}
                        |{"stream":"\n"}
                        |{"stream":" ---\u003e Using cache\n"}
                        |{"stream":" ---\u003e 0ceae477da9d\n"}
                        |{"stream":"Step 3/5 : COPY index.html /usr/share/nginx/html"}
                        |{"stream":"\n"}
                        |{"stream":" ---\u003e b288a67b828c\n"}
                        |{"stream":"Step 4/5 : COPY health-check.sh /tools/"}
                        |{"stream":"\n"}
                        |{"stream":" ---\u003e 951e32ae4f76\n"}
                        |{"stream":"Step 5/5 : HEALTHCHECK --interval=2s --retries=1 CMD /tools/health-check.sh"}
                        |{"stream":"\n"}
                        |{"stream":" ---\u003e Running in 3de7e4521d69\n"}
                        |{"stream":"Removing intermediate container 3de7e4521d69\n"}
                        |{"stream":" ---\u003e 24125bbc6cbe\n"}
                        |{"aux":{"ID":"sha256:24125bbc6cbe08f530e97c81ee461357fa3ba56f4d7693d7895ec86671cf3540"}}
                        |{"stream":"Successfully built 24125bbc6cbe\n"}
                    """.trimMargin()

                        val imagePullProgress = DockerImageProgress("Doing something", 10, 20)

                        beforeEachTest {
                            stubProgressUpdate(imageProgressReporter, output.lines()[0], imagePullProgress)
                            whenever(api.build(any(), any(), any(), any(), any(), any(), any(), any(), any())).doAnswer(sendProgressAndReturnImage(output, DockerImage("some-image-id")))
                        }

                        val statusUpdates by createForEachTest { mutableListOf<DockerImageBuildProgress>() }

                        val onStatusUpdate = fun(p: DockerImageBuildProgress) {
                            statusUpdates.add(p)
                        }

                        val result by runForEachTest { client.build(buildDirectory, buildArgs, dockerfilePath, pathResolutionContext, imageTags, forcePull, outputSink, cancellationContext, onStatusUpdate) }

                        it("builds the image") {
                            verify(api).build(eq(context), eq(buildArgs), eq(dockerfilePath), eq(imageTags), eq(forcePull), eq(setOf(image1Credentials, image2Credentials)), eq(outputSink), eq(cancellationContext), any())
                        }

                        it("returns the ID of the created image") {
                            assertThat(result.id, equalTo("some-image-id"))
                        }

                        it("sends status updates as the build progresses") {
                            assertThat(
                                statusUpdates, equalTo(
                                    listOf(
                                        DockerImageBuildProgress(1, 5, "FROM nginx:1.13.0", null),
                                        DockerImageBuildProgress(1, 5, "FROM nginx:1.13.0", imagePullProgress),
                                        DockerImageBuildProgress(2, 5, "RUN apt update && apt install -y curl && rm -rf /var/lib/apt/lists/*", null),
                                        DockerImageBuildProgress(3, 5, "COPY index.html /usr/share/nginx/html", null),
                                        DockerImageBuildProgress(4, 5, "COPY health-check.sh /tools/", null),
                                        DockerImageBuildProgress(5, 5, "HEALTHCHECK --interval=2s --retries=1 CMD /tools/health-check.sh", null)
                                    )
                                )
                            )
                        }
                    }

                    on("the daemon sending image pull information before sending any step progress information") {
                        val output = """
                        |{"status":"pulling the image"}
                        |{"stream":"Step 1/5 : FROM nginx:1.13.0"}
                        |{"status":"pulling the image"}
                        |{"stream":"\n"}
                        |{"stream":" ---\u003e 3448f27c273f\n"}
                        |{"stream":"Step 2/5 : RUN apt update \u0026\u0026 apt install -y curl \u0026\u0026 rm -rf /var/lib/apt/lists/*"}
                    """.trimMargin()

                        val imagePullProgress = DockerImageProgress("Doing something", 10, 20)
                        val statusUpdates by createForEachTest { mutableListOf<DockerImageBuildProgress>() }

                        beforeEachTest {
                            stubProgressUpdate(imageProgressReporter, output.lines()[0], imagePullProgress)
                            whenever(api.build(any(), any(), any(), any(), any(), any(), any(), any(), any())).doAnswer(sendProgressAndReturnImage(output, DockerImage("some-image-id")))

                            val onStatusUpdate = fun(p: DockerImageBuildProgress) {
                                statusUpdates.add(p)
                            }

                            client.build(buildDirectory, buildArgs, dockerfilePath, pathResolutionContext, imageTags, forcePull, outputSink, cancellationContext, onStatusUpdate)
                        }

                        it("sends status updates only once the first step is started") {
                            assertThat(
                                statusUpdates, equalTo(
                                    listOf(
                                        DockerImageBuildProgress(1, 5, "FROM nginx:1.13.0", null),
                                        DockerImageBuildProgress(1, 5, "FROM nginx:1.13.0", imagePullProgress),
                                        DockerImageBuildProgress(2, 5, "RUN apt update && apt install -y curl && rm -rf /var/lib/apt/lists/*", null)
                                    )
                                )
                            )
                        }
                    }
                }

                given("getting credentials for the base image fails") {
                    val exception = DockerRegistryCredentialsException("Could not load credentials: something went wrong.")

                    beforeEachTest {
                        whenever(credentialsProvider.getCredentials("nginx:1.13.0")).thenThrow(exception)
                    }

                    on("building the image") {
                        it("throws an appropriate exception") {
                            assertThat(
                                { client.build(buildDirectory, buildArgs, dockerfilePath, pathResolutionContext, imageTags, forcePull, outputSink, cancellationContext, {}) }, throws<ImageBuildFailedException>(
                                    withMessage("Could not build image: Could not load credentials: something went wrong.")
                                        and withCause(exception)
                                )
                            )
                        }
                    }
                }
            }

            given("the Dockerfile does not exist") {
                on("building the image") {
                    it("throws an appropriate exception") {
                        assertThat(
                            { client.build(buildDirectory, buildArgs, dockerfilePath, pathResolutionContext, imageTags, forcePull, outputSink, cancellationContext, {}) },
                            throws<ImageBuildFailedException>(withMessage("Could not build image: the Dockerfile 'some-Dockerfile-path' does not exist in the build directory <a nicely formatted version of the build directory>"))
                        )
                    }
                }
            }

            given("the Dockerfile exists but is not a child of the build directory") {
                val dockerfilePathOutsideBuildDir = "../some-Dockerfile"
                val resolvedDockerfilePath by createForEachTest { buildDirectory.resolve(dockerfilePathOutsideBuildDir) }

                beforeEachTest {
                    Files.createDirectories(buildDirectory)
                    Files.createFile(resolvedDockerfilePath)
                }

                on("building the image") {
                    it("throws an appropriate exception") {
                        assertThat(
                            { client.build(buildDirectory, buildArgs, dockerfilePathOutsideBuildDir, pathResolutionContext, imageTags, forcePull, outputSink, cancellationContext, {}) },
                            throws<ImageBuildFailedException>(withMessage("Could not build image: the Dockerfile '../some-Dockerfile' is not a child of the build directory <a nicely formatted version of the build directory>"))
                        )
                    }
                }
            }
        }

        describe("pulling an image") {
            val cancellationContext by createForEachTest { mock<CancellationContext>() }

            given("forcibly pulling the image is disabled") {
                val forcePull = false

                given("the image does not exist locally") {
                    beforeEachTest {
                        whenever(api.hasImage("some-image")).thenReturn(false)
                    }

                    given("getting credentials for the image succeeds") {
                        val credentials = mock<DockerRegistryCredentials>()

                        beforeEachTest {
                            whenever(credentialsProvider.getCredentials("some-image")).thenReturn(credentials)
                        }

                        on("pulling the image") {
                            val firstProgressUpdate = Json.default.parseJson("""{"thing": "value"}""").jsonObject
                            val secondProgressUpdate = Json.default.parseJson("""{"thing": "other value"}""").jsonObject

                            beforeEachTest {
                                whenever(imageProgressReporter.processProgressUpdate(firstProgressUpdate)).thenReturn(DockerImageProgress("Doing something", 10, 20))
                                whenever(imageProgressReporter.processProgressUpdate(secondProgressUpdate)).thenReturn(null)

                                whenever(api.pull(any(), any(), any(), any())).then { invocation ->
                                    @Suppress("UNCHECKED_CAST")
                                    val onProgressUpdate = invocation.arguments[3] as (JsonObject) -> Unit
                                    onProgressUpdate(firstProgressUpdate)
                                    onProgressUpdate(secondProgressUpdate)

                                    null
                                }
                            }

                            val progressUpdatesReceived by createForEachTest { mutableListOf<DockerImageProgress>() }
                            val image by runForEachTest { client.pull("some-image", forcePull, cancellationContext) { progressUpdatesReceived.add(it) } }

                            it("calls the Docker API to pull the image") {
                                verify(api).pull(eq("some-image"), eq(credentials), eq(cancellationContext), any())
                            }

                            it("sends notifications for all relevant progress updates") {
                                assertThat(progressUpdatesReceived, equalTo(listOf(DockerImageProgress("Doing something", 10, 20))))
                            }

                            it("returns the Docker image") {
                                assertThat(image, equalTo(DockerImage("some-image")))
                            }
                        }
                    }

                    given("getting credentials for the image fails") {
                        val exception = DockerRegistryCredentialsException("Could not load credentials: something went wrong.")

                        beforeEachTest {
                            whenever(credentialsProvider.getCredentials("some-image")).thenThrow(exception)
                        }

                        on("pulling the image") {
                            it("throws an appropriate exception") {
                                assertThat({ client.pull("some-image", forcePull, cancellationContext, {}) }, throws<ImagePullFailedException>(
                                    withMessage("Could not pull image 'some-image': Could not load credentials: something went wrong.")
                                        and withCause(exception)
                                ))
                            }
                        }
                    }
                }

                on("when the image already exists locally") {
                    beforeEachTest { whenever(api.hasImage("some-image")).thenReturn(true) }

                    val image by runForEachTest { client.pull("some-image", forcePull, cancellationContext, {}) }

                    it("does not call the Docker API to pull the image again") {
                        verify(api, never()).pull(any(), any(), any(), any())
                    }

                    it("returns the Docker image") {
                        assertThat(image, equalTo(DockerImage("some-image")))
                    }
                }
            }

            given("forcibly pulling the image is enabled") {
                val forcePull = true

                given("getting credentials for the image succeeds") {
                    val credentials = mock<DockerRegistryCredentials>()

                    beforeEachTest {
                        whenever(credentialsProvider.getCredentials("some-image")).thenReturn(credentials)
                    }

                    on("pulling the image") {
                        val image by runForEachTest { client.pull("some-image", forcePull, cancellationContext, {}) }

                        it("calls the Docker API to pull the image") {
                            verify(api).pull(eq("some-image"), eq(credentials), eq(cancellationContext), any())
                        }

                        it("returns the Docker image") {
                            assertThat(image, equalTo(DockerImage("some-image")))
                        }

                        it("does not call the Docker API to check if the image has already been pulled") {
                            verify(api, never()).hasImage(any())
                        }
                    }
                }
            }
        }
    }
})

private fun stubProgressUpdate(reporter: DockerImageProgressReporter, input: String, update: DockerImageProgress) {
    val json = Json.default.parseJson(input).jsonObject
    whenever(reporter.processProgressUpdate(eq(json))).thenReturn(update)
}

private fun sendProgressAndReturnImage(progressUpdates: String, image: DockerImage) = { invocation: InvocationOnMock ->
    @Suppress("UNCHECKED_CAST")
    val onProgressUpdate = invocation.arguments.last() as (JsonObject) -> Unit

    progressUpdates.lines().forEach { line ->
        onProgressUpdate(Json.default.parseJson(line).jsonObject)
    }

    image
}
