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

package batect.docker.api

import batect.docker.DockerHttpConfig
import batect.docker.DockerImage
import batect.docker.ImageBuildFailedException
import batect.docker.ImagePullFailedException
import batect.docker.ImageReference
import batect.docker.Json
import batect.docker.build.ActiveImageBuildStep
import batect.docker.build.BuildComplete
import batect.docker.build.BuildError
import batect.docker.build.BuildProgress
import batect.docker.build.ImageBuildContext
import batect.docker.build.ImageBuildContextEntry
import batect.docker.build.ImageBuildContextRequestBody
import batect.docker.build.ImageBuildEvent
import batect.docker.build.ImageBuildEventCallback
import batect.docker.build.ImageBuildResponseBody
import batect.docker.pull.RegistryCredentials
import batect.docker.pull.TokenRegistryCredentials
import batect.os.SystemInfo
import batect.primitives.CancellationContext
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.mock
import batect.testutils.mockPost
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okio.Sink
import okio.buffer
import okio.sink
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayOutputStream
import java.io.Reader
import java.nio.file.Paths

object ImagesAPISpec : Spek({
    describe("a Docker images API client") {
        val dockerHost = "the-docker-daemon"
        val dockerBaseUrl = "http://$dockerHost"
        val httpClient by createForEachTest { mock<OkHttpClient>() }
        val httpConfig by createForEachTest {
            mock<DockerHttpConfig> {
                on { client } doReturn httpClient
                on { baseUrl } doReturn dockerBaseUrl.toHttpUrl()
            }
        }

        val systemInfo by createForEachTest {
            mock<SystemInfo> {
                on { lineSeparator } doReturn "SYSTEM_LINE_SEPARATOR"
            }
        }

        val logger by createLoggerForEachTestWithoutCustomSerializers()
        val buildResponseBody by createForEachTest { MockImageBuildResponseBody() }
        val api by createForEachTest { ImagesAPI(httpConfig, systemInfo, logger, { buildResponseBody }) }

        val errorResponse = """{"message": "Something went wrong.\nMore details on next line."}"""
        val errorMessageWithCorrectLineEndings = "Something went wrong.SYSTEM_LINE_SEPARATORMore details on next line."

        describe("building an image") {
            val clientWithLongTimeout by createForEachTest { mock<OkHttpClient>() }
            val longTimeoutClientBuilder by createForEachTest {
                mock<OkHttpClient.Builder> { mock ->
                    on { readTimeout(any(), any()) } doReturn mock
                    on { build() } doReturn clientWithLongTimeout
                }
            }

            beforeEachTest {
                whenever(httpClient.newBuilder()).doReturn(longTimeoutClientBuilder)
            }

            val context = ImageBuildContext(setOf(ImageBuildContextEntry(Paths.get("/some/file"), "file")))
            val buildArgs = mapOf("someArg" to "someValue")
            val dockerfilePath = "some-Dockerfile-path"
            val imageTags = setOf("some_image_tag", "some_other_image_tag")
            val pullImage = false
            val registryCredentials = setOf(TokenRegistryCredentials("some_token", "registry.com"))

            val expectedUrl = hasScheme("http") and
                hasHost(dockerHost) and
                hasPath("/v1.37/build") and
                hasQueryParameter("buildargs", """{"someArg":"someValue"}""") and
                hasQueryParameter("t", "some_image_tag") and
                hasQueryParameter("t", "some_other_image_tag") and
                hasQueryParameter("dockerfile", dockerfilePath) and
                hasQueryParameter("pull", "0")

            val base64EncodedJSONCredentials = "eyJyZWdpc3RyeS5jb20iOnsiaWRlbnRpdHl0b2tlbiI6InNvbWVfdG9rZW4ifX0="
            val expectedHeadersForAuthentication = Headers.Builder().set("X-Registry-Config", base64EncodedJSONCredentials).build()
            val cancellationContext by createForEachTest { mock<CancellationContext>() }

            val successEvents = listOf(
                BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(0, "FROM nginx:1.13.0")), 5),
                BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(1, "RUN apt update \u0026\u0026 apt install -y curl \u0026\u0026 rm -rf /var/lib/apt/lists/*")), 5),
                BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(2, "COPY index.html /usr/share/nginx/html")), 5),
                BuildComplete(DockerImage("sha256:24125bbc6cbe08f530e97c81ee461357fa3ba56f4d7693d7895ec86671cf3540"))
            )

            val successProgressEvents = successEvents.dropLast(1)
            val successOutput = "This is some output from the build process."

            on("the build succeeding") {
                val call by createForEachTest { clientWithLongTimeout.mock("POST", expectedUrl, daemonBuildResponse, 200, expectedHeadersForAuthentication) }
                val output by createForEachTest { ByteArrayOutputStream() }
                val eventsReceiver by createForEachTest { BuildEventsReceiver() }

                beforeEachTest {
                    buildResponseBody.outputToStream = successOutput
                    buildResponseBody.eventsToPost = successEvents
                }

                val image by runForEachTest { api.build(context, buildArgs, dockerfilePath, imageTags, pullImage, registryCredentials, output.sink(), cancellationContext, eventsReceiver::onProgressUpdate) }

                it("sends a request to the Docker daemon to build the image") {
                    verify(call).execute()
                }

                it("configures the HTTP client with no timeout to allow for slow build output") {
                    verify(longTimeoutClientBuilder).readTimeout(eq(0), any())
                }

                it("builds the image with the expected context") {
                    verify(clientWithLongTimeout).newCall(requestWithBody(ImageBuildContextRequestBody(context)))
                }

                it("sends all build progress events to the receiver") {
                    assertThat(eventsReceiver.eventsReceived, equalTo(successProgressEvents))
                }

                it("registers the API call with the cancellation context") {
                    verify(cancellationContext).addCancellationCallback(call::cancel)
                }

                it("returns the build image") {
                    assertThat(image, equalTo(DockerImage("sha256:24125bbc6cbe08f530e97c81ee461357fa3ba56f4d7693d7895ec86671cf3540")))
                }

                it("writes all output from the build process to the provided output stream") {
                    assertThat(output.toString(), equalTo(successOutput))
                }
            }

            on("the build running with all images re-pulled") {
                val expectedUrlWithRePullingEnabled = hasScheme("http") and
                    hasHost(dockerHost) and
                    hasPath("/v1.37/build") and
                    hasQueryParameter("pull", "1")

                val call by createForEachTest { clientWithLongTimeout.mock("POST", expectedUrlWithRePullingEnabled, daemonBuildResponse, 200, expectedHeadersForAuthentication) }

                beforeEachTest {
                    buildResponseBody.outputToStream = successOutput
                    buildResponseBody.eventsToPost = successEvents
                }

                beforeEachTest { api.build(context, emptyMap(), dockerfilePath, imageTags, true, registryCredentials, null, cancellationContext, {}) }

                it("sends a request to the Docker daemon to build the image with re-pulling all images enabled") {
                    verify(call).execute()
                }
            }

            on("the build having no registry credentials") {
                val expectedHeadersForNoAuthentication = Headers.Builder().build()
                val call by createForEachTest { clientWithLongTimeout.mock("POST", expectedUrl, daemonBuildResponse, 200, expectedHeadersForNoAuthentication) }

                beforeEachTest {
                    buildResponseBody.outputToStream = successOutput
                    buildResponseBody.eventsToPost = successEvents
                }

                beforeEachTest { api.build(context, buildArgs, dockerfilePath, imageTags, pullImage, emptySet(), null, cancellationContext, {}) }

                it("sends a request to the Docker daemon to build the image with no authentication header") {
                    verify(call).execute()
                }
            }

            on("the build having no build args") {
                val expectedUrlWithNoBuildArgs = hasScheme("http") and
                    hasHost(dockerHost) and
                    hasPath("/v1.37/build") and
                    hasQueryParameter("buildargs", """{}""")

                val call by createForEachTest { clientWithLongTimeout.mock("POST", expectedUrlWithNoBuildArgs, daemonBuildResponse, 200, expectedHeadersForAuthentication) }

                beforeEachTest {
                    buildResponseBody.outputToStream = successOutput
                    buildResponseBody.eventsToPost = successEvents
                }

                beforeEachTest { api.build(context, emptyMap(), dockerfilePath, imageTags, pullImage, registryCredentials, null, cancellationContext, {}) }

                it("sends a request to the Docker daemon to build the image with an empty set of build args") {
                    verify(call).execute()
                }
            }

            on("the build failing immediately") {
                beforeEachTest {
                    clientWithLongTimeout.mock("POST", expectedUrl, errorResponse, 418, expectedHeadersForAuthentication)
                }

                it("throws an appropriate exception") {
                    assertThat(
                        { api.build(context, buildArgs, dockerfilePath, imageTags, pullImage, registryCredentials, null, cancellationContext, {}) },
                        throws<ImageBuildFailedException>(
                            withMessage("Building image failed: $errorMessageWithCorrectLineEndings")
                        )
                    )
                }
            }

            on("the build failing during the build process") {
                val output by createForEachTest { ByteArrayOutputStream() }

                beforeEachTest {
                    clientWithLongTimeout.mock("POST", expectedUrl, daemonBuildResponse, 200, expectedHeadersForAuthentication)

                    buildResponseBody.outputToStream = "This is some output from the build process.\nThis is some more output from the build process.\n"
                    buildResponseBody.eventsToPost = listOf(
                        BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(0, "FROM nginx:1.13.0")), 5),
                        BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(1, "RUN exit 1")), 5),
                        BuildError("The command '/bin/sh -c exit 1' returned a non-zero code: 1")
                    )
                }

                it("throws an appropriate exception with all line endings corrected for the host system") {
                    assertThat(
                        { api.build(context, buildArgs, dockerfilePath, imageTags, pullImage, registryCredentials, output.sink(), cancellationContext, {}) },
                        throws<ImageBuildFailedException>(
                            withMessage(
                                "Building image failed: The command '/bin/sh -c exit 1' returned a non-zero code: 1. Output from build process was:SYSTEM_LINE_SEPARATOR" +
                                    "This is some output from the build process.SYSTEM_LINE_SEPARATOR" +
                                    "This is some more output from the build process."
                            )
                        )
                    )
                }

                it("writes all output from the build process to the output stream") {
                    try { api.build(context, buildArgs, dockerfilePath, imageTags, pullImage, registryCredentials, output.sink(), cancellationContext, {}) } catch (_: Throwable) {}
                    assertThat(
                        output.toString(),
                        equalTo(
                            """
                                |This is some output from the build process.
                                |This is some more output from the build process.
                                |
                            """.trimMargin()
                        )
                    )
                }
            }

            on("the build process never sending an output line with the built image ID") {
                beforeEachTest {
                    clientWithLongTimeout.mock("POST", expectedUrl, daemonBuildResponse, 200, expectedHeadersForAuthentication)

                    buildResponseBody.eventsToPost = listOf(
                        BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(0, "FROM nginx:1.13.0")), 5),
                    )
                }

                it("throws an appropriate exception") {
                    assertThat(
                        { api.build(context, buildArgs, dockerfilePath, imageTags, pullImage, registryCredentials, null, cancellationContext, {}) },
                        throws<ImageBuildFailedException>(
                            withMessage("Building image failed: daemon never sent built image ID.")
                        )
                    )
                }
            }
        }

        describe("pulling an image") {
            val imageReference = ImageReference("some-image")
            val expectedUrl = "$dockerBaseUrl/v1.37/images/create?fromImage=some-image%3Alatest"
            val clientWithLongTimeout by createForEachTest { mock<OkHttpClient>() }
            val longTimeoutClientBuilder by createForEachTest {
                mock<OkHttpClient.Builder> { mock ->
                    on { readTimeout(any(), any()) } doReturn mock
                    on { build() } doReturn clientWithLongTimeout
                }
            }

            beforeEachTest {
                whenever(httpClient.newBuilder()).doReturn(longTimeoutClientBuilder)
            }

            val registryCredentials = mock<RegistryCredentials> {
                on { toJSON() } doReturn JsonPrimitive("some json credentials")
            }

            val base64EncodedJSONCredentials = "InNvbWUganNvbiBjcmVkZW50aWFscyI="
            val expectedHeadersForAuthentication = Headers.Builder().set("X-Registry-Auth", base64EncodedJSONCredentials).build()
            val cancellationContext by createForEachTest { mock<CancellationContext>() }

            on("the pull succeeding because the image is already present") {
                val response = """
                    |{"status":"Pulling from library/some-image","id":"latest"}
                    |{"status":"Status: Image is up to date for some-image"}
                """.trimMargin()

                val call by createForEachTest { clientWithLongTimeout.mockPost(expectedUrl, response, 200, expectedHeadersForAuthentication) }
                val progressReceiver by createForEachTest { ImageProgressReceiver() }
                beforeEachTest { api.pull(imageReference, registryCredentials, cancellationContext, progressReceiver::onProgressUpdate) }

                it("sends a request to the Docker daemon to pull the image") {
                    verify(call).execute()
                }

                it("sends all progress updates to the receiver") {
                    assertThat(progressReceiver, receivedAllUpdatesFrom(response))
                }

                it("configures the HTTP client with no timeout to allow for slow layer extraction operations") {
                    verify(longTimeoutClientBuilder).readTimeout(eq(0), any())
                }

                it("registers the API call with the cancellation context") {
                    verify(cancellationContext).addCancellationCallback(call::cancel)
                }
            }

            on("the pull succeeding because the image was pulled") {
                val response = """
                    |{"status":"Pulling from library/some-image","id":"latest"}
                    |{"status":"Pulling fs layer","progressDetail":{},"id":"d660b1f15b9b"}
                    |{"status":"Pulling fs layer","progressDetail":{},"id":"46dde23c37b3"}
                    |{"status":"Pulling fs layer","progressDetail":{},"id":"6ebaeb074589"}
                    |{"status":"Pulling fs layer","progressDetail":{},"id":"e7428f935583"}
                    |{"status":"Status: Downloaded newer image for some-image:latest"}
                """.trimMargin()

                val call by createForEachTest { clientWithLongTimeout.mockPost(expectedUrl, response, 200, expectedHeadersForAuthentication) }
                val progressReceiver by createForEachTest { ImageProgressReceiver() }
                beforeEachTest { api.pull(imageReference, registryCredentials, cancellationContext, progressReceiver::onProgressUpdate) }

                it("sends a request to the Docker daemon to pull the image") {
                    verify(call).execute()
                }

                it("sends all progress updates to the receiver") {
                    assertThat(progressReceiver, receivedAllUpdatesFrom(response))
                }

                it("configures the HTTP client with no timeout to allow for slow layer extraction operations") {
                    verify(longTimeoutClientBuilder).readTimeout(eq(0), any())
                }

                it("registers the API call with the cancellation context") {
                    verify(cancellationContext).addCancellationCallback(call::cancel)
                }
            }

            on("the pull request having no registry credentials") {
                val expectedHeadersForNoAuthentication = Headers.Builder().build()
                val call by createForEachTest { clientWithLongTimeout.mockPost(expectedUrl, "", 200, expectedHeadersForNoAuthentication) }
                beforeEachTest { api.pull(imageReference, null, cancellationContext, {}) }

                it("sends a request to the Docker daemon to pull the image with no authentication header") {
                    verify(call).execute()
                }
            }

            on("the pull failing immediately") {
                beforeEachTest { clientWithLongTimeout.mockPost(expectedUrl, errorResponse, 418, expectedHeadersForAuthentication) }

                it("throws an appropriate exception") {
                    assertThat(
                        { api.pull(imageReference, registryCredentials, cancellationContext, {}) },
                        throws<ImagePullFailedException>(
                            withMessage("Pulling image 'some-image:latest' failed: $errorMessageWithCorrectLineEndings")
                        )
                    )
                }
            }

            on("the pull failing part-way through the process") {
                val response = """
                    |{"status":"Pulling from library/some-image","id":"latest"}
                    |{"status":"Pulling fs layer","progressDetail":{},"id":"d660b1f15b9b"}
                    |{"status":"Pulling fs layer","progressDetail":{},"id":"46dde23c37b3"}
                    |{"status":"Pulling fs layer","progressDetail":{},"id":"6ebaeb074589"}
                    |{"status":"Pulling fs layer","progressDetail":{},"id":"e7428f935583"}
                    |{"error":"Server error: 404 trying to fetch remote history for some-image.\nMore details on following line.","errorDetail":{"code":404,"message":"Server error: 404 trying to fetch remote history for some-image"}}
                """.trimMargin()

                beforeEachTest { clientWithLongTimeout.mockPost(expectedUrl, response, 200, expectedHeadersForAuthentication) }
                val progressReceiver = ImageProgressReceiver()

                it("throws an appropriate exception with all line endings corrected for the host system") {
                    assertThat(
                        { api.pull(imageReference, registryCredentials, cancellationContext, progressReceiver::onProgressUpdate) },
                        throws<ImagePullFailedException>(
                            withMessage("Pulling image 'some-image:latest' failed: Server error: 404 trying to fetch remote history for some-image.SYSTEM_LINE_SEPARATORMore details on following line.")
                        )
                    )
                }

                it("sends all progress updates to the receiver except for the error") {
                    assertThat(progressReceiver, receivedAllUpdatesFrom(response.lines().dropLast(1)))
                }
            }
        }

        describe("checking if an image has been pulled") {
            val imageReference = ImageReference("some-image")
            val expectedUrl = "$dockerBaseUrl/v1.37/images/some-image:latest/json"

            given("the image has already been pulled") {
                beforeEachTest { httpClient.mock("GET", expectedUrl, """{"Id": "abc123"}""") }
                val hasImage by runForEachTest { api.hasImage(imageReference) }

                it("returns true") {
                    assertThat(hasImage, equalTo(true))
                }
            }

            given("the image has not already been pulled") {
                beforeEachTest { httpClient.mock("GET", expectedUrl, errorResponse, 404) }
                val hasImage by runForEachTest { api.hasImage(imageReference) }

                it("returns false") {
                    assertThat(hasImage, equalTo(false))
                }
            }

            given("the call to the Docker daemon fails") {
                beforeEachTest { httpClient.mock("GET", expectedUrl, errorResponse, 418) }

                it("throws an appropriate exception") {
                    assertThat({ api.hasImage(imageReference) }, throws<ImagePullFailedException>(withMessage("Checking if image 'some-image:latest' has already been pulled failed: $errorMessageWithCorrectLineEndings")))
                }
            }
        }
    }
})

private class BuildEventsReceiver {
    val eventsReceived = mutableListOf<BuildProgress>()

    fun onProgressUpdate(update: BuildProgress) {
        eventsReceived.add(update)
    }
}

private class ImageProgressReceiver {
    val updatesReceived = mutableListOf<JsonObject>()

    fun onProgressUpdate(update: JsonObject) {
        updatesReceived.add(update)
    }
}

private fun receivedAllUpdatesFrom(response: String): Matcher<ImageProgressReceiver> = receivedAllUpdatesFrom(response.lines())

private fun receivedAllUpdatesFrom(lines: Iterable<String>): Matcher<ImageProgressReceiver> {
    val expectedUpdates = lines.map { Json.default.parseToJsonElement(it).jsonObject }

    return has(ImageProgressReceiver::updatesReceived, equalTo(expectedUpdates))
}

private val daemonBuildResponse = "This is the response from the daemon."

private class MockImageBuildResponseBody : ImageBuildResponseBody {
    var outputToStream: String = ""
    var eventsToPost: List<ImageBuildEvent> = emptyList()

    override fun readFrom(stream: Reader, outputStream: Sink, eventCallback: ImageBuildEventCallback) {
        assertThat(stream.readText(), equalTo(daemonBuildResponse))

        outputStream.buffer().writeString(outputToStream, Charsets.UTF_8).flush()

        eventsToPost.forEach(eventCallback)
    }
}
