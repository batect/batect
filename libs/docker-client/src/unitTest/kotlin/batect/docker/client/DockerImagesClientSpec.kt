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
import batect.docker.DockerImageReference
import batect.docker.DockerRegistryCredentialsException
import batect.docker.DownloadOperation
import batect.docker.ImageBuildFailedException
import batect.docker.ImagePullFailedException
import batect.docker.Json
import batect.docker.api.ImagesAPI
import batect.docker.build.BuildProgress
import batect.docker.build.DockerImageBuildContext
import batect.docker.build.DockerImageBuildContextFactory
import batect.docker.build.DockerfileParser
import batect.docker.pull.DockerImagePullProgress
import batect.docker.pull.DockerImagePullProgressReporter
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
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okio.Sink
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files

object DockerImagesClientSpec : Spek({
    describe("a Docker images client") {
        val api by createForEachTest { mock<ImagesAPI>() }
        val credentialsProvider by createForEachTest { mock<DockerRegistryCredentialsProvider>() }
        val imageBuildContextFactory by createForEachTest { mock<DockerImageBuildContextFactory>() }
        val dockerfileParser by createForEachTest { mock<DockerfileParser>() }
        val logger by createLoggerForEachTestWithoutCustomSerializers()
        val imageProgressReporter by createForEachTest { mock<DockerImagePullProgressReporter>() }
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
                    whenever(dockerfileParser.extractBaseImageNames(resolvedDockerfilePath)).doReturn(setOf(DockerImageReference("nginx:1.13.0"), DockerImageReference("some-other-image:2.3.4")))
                }

                given("getting the credentials for the base image succeeds") {
                    val image1Credentials = mock<DockerRegistryCredentials>()
                    val image2Credentials = mock<DockerRegistryCredentials>()

                    beforeEachTest {
                        whenever(credentialsProvider.getCredentials(DockerImageReference("nginx:1.13.0"))).doReturn(image1Credentials)
                        whenever(credentialsProvider.getCredentials(DockerImageReference("some-other-image:2.3.4"))).doReturn(image2Credentials)
                    }

                    on("a successful build") {
                        val image = DockerImage("some-image-id")
                        beforeEachTest { whenever(api.build(any(), any(), any(), any(), any(), any(), any(), any(), any())).doReturn(image) }

                        val onStatusUpdate = fun(_: BuildProgress) {}
                        val result by runForEachTest { client.build(buildDirectory, buildArgs, dockerfilePath, pathResolutionContext, imageTags, forcePull, outputSink, cancellationContext, onStatusUpdate) }

                        it("builds the image") {
                            verify(api).build(eq(context), eq(buildArgs), eq(dockerfilePath), eq(imageTags), eq(forcePull), eq(setOf(image1Credentials, image2Credentials)), eq(outputSink), eq(cancellationContext), eq(onStatusUpdate))
                        }

                        it("returns the built image") {
                            assertThat(result, equalTo(image))
                        }
                    }
                }

                given("getting credentials for the base image fails") {
                    val exception = DockerRegistryCredentialsException("Could not load credentials: something went wrong.")

                    beforeEachTest {
                        whenever(credentialsProvider.getCredentials(DockerImageReference("nginx:1.13.0"))).thenThrow(exception)
                    }

                    on("building the image") {
                        it("throws an appropriate exception") {
                            assertThat(
                                { client.build(buildDirectory, buildArgs, dockerfilePath, pathResolutionContext, imageTags, forcePull, outputSink, cancellationContext, {}) },
                                throws<ImageBuildFailedException>(
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
                        whenever(api.hasImage(DockerImageReference("some-image"))).thenReturn(false)
                    }

                    given("getting credentials for the image succeeds") {
                        val credentials = mock<DockerRegistryCredentials>()

                        beforeEachTest {
                            whenever(credentialsProvider.getCredentials(DockerImageReference("some-image"))).thenReturn(credentials)
                        }

                        on("pulling the image") {
                            val firstProgressUpdate = Json.default.parseToJsonElement("""{"thing": "value"}""").jsonObject
                            val secondProgressUpdate = Json.default.parseToJsonElement("""{"thing": "other value"}""").jsonObject

                            beforeEachTest {
                                whenever(imageProgressReporter.processProgressUpdate(firstProgressUpdate)).thenReturn(DockerImagePullProgress(DownloadOperation.Downloading, 10, 20))
                                whenever(imageProgressReporter.processProgressUpdate(secondProgressUpdate)).thenReturn(null)

                                whenever(api.pull(any(), any(), any(), any())).then { invocation ->
                                    @Suppress("UNCHECKED_CAST")
                                    val onProgressUpdate = invocation.arguments[3] as (JsonObject) -> Unit
                                    onProgressUpdate(firstProgressUpdate)
                                    onProgressUpdate(secondProgressUpdate)

                                    null
                                }
                            }

                            val progressUpdatesReceived by createForEachTest { mutableListOf<DockerImagePullProgress>() }
                            val image by runForEachTest { client.pull("some-image", forcePull, cancellationContext) { progressUpdatesReceived.add(it) } }

                            it("calls the Docker API to pull the image") {
                                verify(api).pull(eq(DockerImageReference("some-image")), eq(credentials), eq(cancellationContext), any())
                            }

                            it("sends notifications for all relevant progress updates") {
                                assertThat(progressUpdatesReceived, equalTo(listOf(DockerImagePullProgress(DownloadOperation.Downloading, 10, 20))))
                            }

                            it("returns the Docker image") {
                                assertThat(image, equalTo(DockerImage("some-image")))
                            }
                        }
                    }

                    given("getting credentials for the image fails") {
                        val exception = DockerRegistryCredentialsException("Could not load credentials: something went wrong.")

                        beforeEachTest {
                            whenever(credentialsProvider.getCredentials(DockerImageReference("some-image"))).thenThrow(exception)
                        }

                        on("pulling the image") {
                            it("throws an appropriate exception") {
                                assertThat(
                                    { client.pull("some-image", forcePull, cancellationContext, {}) },
                                    throws<ImagePullFailedException>(
                                        withMessage("Could not pull image 'some-image': Could not load credentials: something went wrong.")
                                            and withCause(exception)
                                    )
                                )
                            }
                        }
                    }
                }

                on("when the image already exists locally") {
                    beforeEachTest { whenever(api.hasImage(DockerImageReference("some-image"))).thenReturn(true) }

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
                        whenever(credentialsProvider.getCredentials(DockerImageReference("some-image"))).thenReturn(credentials)
                    }

                    on("pulling the image") {
                        val image by runForEachTest { client.pull("some-image", forcePull, cancellationContext, {}) }

                        it("calls the Docker API to pull the image") {
                            verify(api).pull(eq(DockerImageReference("some-image")), eq(credentials), eq(cancellationContext), any())
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
