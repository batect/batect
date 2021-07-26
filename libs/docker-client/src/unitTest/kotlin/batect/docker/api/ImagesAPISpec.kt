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
import batect.docker.build.BuildKitConfig
import batect.docker.build.BuildProgress
import batect.docker.build.ImageBuildEvent
import batect.docker.build.ImageBuildEventCallback
import batect.docker.build.ImageBuildOutputSink
import batect.docker.build.ImageBuildResponseBody
import batect.docker.build.LegacyBuilderConfig
import batect.docker.build.buildkit.BuildKitSession
import batect.docker.build.legacy.ImageBuildContext
import batect.docker.build.legacy.ImageBuildContextEntry
import batect.docker.build.legacy.ImageBuildContextRequestBody
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
import batect.testutils.noHeaders
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
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

        val buildResponseBodyFactory = { builderVersion: BuilderVersion ->
            buildResponseBody.builderVersion = builderVersion
            buildResponseBody
        }

        val api by createForEachTest { ImagesAPI(httpConfig, systemInfo, logger, buildResponseBodyFactory) }

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
            val targetStage: String? = null

            val baseBuildUrl = hasScheme("http") and
                hasHost(dockerHost) and
                hasPath("/v1.37/build")

            val cancellationContext by createForEachTest { mock<CancellationContext>() }

            val successEvents = listOf(
                BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(0, "FROM nginx:1.13.0"))),
                BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(1, "RUN apt update \u0026\u0026 apt install -y curl \u0026\u0026 rm -rf /var/lib/apt/lists/*"))),
                BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(2, "COPY index.html /usr/share/nginx/html"))),
                BuildComplete(DockerImage("sha256:24125bbc6cbe08f530e97c81ee461357fa3ba56f4d7693d7895ec86671cf3540"))
            )

            val successProgressEvents = successEvents.dropLast(1)

            given("the legacy builder is requested") {
                given("the build succeeds") {
                    beforeEachTest {
                        buildResponseBody.eventsToPost = successEvents
                    }

                    val registryCredentials = setOf(TokenRegistryCredentials("some_token", "registry.com"))
                    val base64EncodedJSONCredentials = "eyJyZWdpc3RyeS5jb20iOnsiaWRlbnRpdHl0b2tlbiI6InNvbWVfdG9rZW4ifX0="
                    val expectedHeadersForLegacyAuthentication = Headers.Builder().set("X-Registry-Config", base64EncodedJSONCredentials).build()

                    val call by createForEachTest { clientWithLongTimeout.mock("POST", baseBuildUrl, daemonBuildResponse, 200, expectedHeadersForLegacyAuthentication) }
                    val output by createForEachTest { ImageBuildOutputSink(null) }
                    val eventsReceiver by createForEachTest { BuildEventsReceiver() }

                    val image by runForEachTest {
                        api.build(buildArgs, dockerfilePath, imageTags, pullImage, targetStage, output, LegacyBuilderConfig(registryCredentials, context), cancellationContext, eventsReceiver::onProgressUpdate)
                    }

                    it("sends a request to the Docker daemon to build the image") {
                        verify(call).execute()
                    }

                    it("includes the build args in the request") {
                        assertThat(call.request().url, hasQueryParameter("buildargs", """{"someArg":"someValue"}"""))
                    }

                    it("includes the image tags in the request") {
                        assertThat(call.request().url, hasQueryParameter("t", "some_image_tag") and hasQueryParameter("t", "some_other_image_tag"))
                    }

                    it("includes the Dockerfile name in the request") {
                        assertThat(call.request().url, hasQueryParameter("dockerfile", dockerfilePath))
                    }

                    it("instructs the daemon to not re-pull already pulled images") {
                        assertThat(call.request().url, hasQueryParameter("pull", "0"))
                    }

                    it("does not include an explicit version in the request") {
                        assertThat(call.request().url, doesNotHaveQueryParameter("version"))
                    }

                    it("includes the registry credentials in a header in the request") {
                        assertThat(call.request().headers, equalTo(expectedHeadersForLegacyAuthentication))
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

                    it("passes the provided output sink to the builder response body") {
                        assertThat(buildResponseBody.invokedWithOutputSink, equalTo(output))
                    }

                    it("creates a legacy builder response body") {
                        assertThat(buildResponseBody.builderVersion, equalTo(BuilderVersion.Legacy))
                    }
                }

                given("the build has no registry credentials") {
                    val expectedHeadersForNoAuthentication = noHeaders
                    val call by createForEachTest { clientWithLongTimeout.mock("POST", baseBuildUrl, daemonBuildResponse, 200, expectedHeadersForNoAuthentication) }

                    beforeEachTest {
                        buildResponseBody.eventsToPost = successEvents
                    }

                    beforeEachTest { api.build(buildArgs, dockerfilePath, imageTags, pullImage, targetStage, ImageBuildOutputSink(null), LegacyBuilderConfig(emptySet(), context), cancellationContext, {}) }

                    it("sends a request to the Docker daemon to build the image with no authentication header") {
                        verify(call).execute()
                    }

                    it("includes no authentication headers in the request") {
                        assertThat(call.request().headers, equalTo(expectedHeadersForNoAuthentication))
                    }
                }
            }

            given("BuildKit is requested") {
                val session by createForEachTest {
                    BuildKitSession(
                        "session-123",
                        "build-123",
                        "name-123",
                        "key-123",
                        mock(),
                        mock()
                    )
                }

                given("the build succeeds") {
                    beforeEachTest {
                        buildResponseBody.eventsToPost = successEvents
                    }

                    given("the build has no special options") {
                        val call by createForEachTest { clientWithLongTimeout.mock("POST", baseBuildUrl, daemonBuildResponse, 200) }

                        runForEachTest { api.build(buildArgs, dockerfilePath, imageTags, pullImage, targetStage, ImageBuildOutputSink(null), BuildKitConfig(session), cancellationContext, {}) }

                        it("sends a request to the Docker daemon to build the image with the expected URL") {
                            verify(call).execute()
                        }

                        it("sends a request to the Docker daemon to build the image") {
                            verify(call).execute()
                        }

                        it("includes the build args in the request") {
                            assertThat(call.request().url, hasQueryParameter("buildargs", """{"someArg":"someValue"}"""))
                        }

                        it("includes the image tags in the request") {
                            assertThat(call.request().url, hasQueryParameter("t", "some_image_tag") and hasQueryParameter("t", "some_other_image_tag"))
                        }

                        it("includes the Dockerfile name in the request") {
                            assertThat(call.request().url, hasQueryParameter("dockerfile", dockerfilePath))
                        }

                        it("instructs the daemon to not re-pull already pulled images") {
                            assertThat(call.request().url, hasQueryParameter("pull", "0"))
                        }

                        it("includes the BuildKit version number in the request") {
                            assertThat(call.request().url, hasQueryParameter("version", "2"))
                        }

                        it("includes the session ID in the request") {
                            assertThat(call.request().url, hasQueryParameter("session", session.sessionId))
                        }

                        it("includes the build ID in the request") {
                            assertThat(call.request().url, hasQueryParameter("buildid", session.buildId))
                        }

                        it("instructs the daemon to take build context from the session") {
                            assertThat(call.request().url, hasQueryParameter("remote", "client-session"))
                        }

                        it("does not include a target stage name in the request") {
                            assertThat(call.request().url, doesNotHaveQueryParameter("target"))
                        }

                        it("does not include any registry authentication headers in the request") {
                            assertThat(call.request().headers, equalTo(noHeaders))
                        }

                        it("builds the image with no context in the request body") {
                            verify(clientWithLongTimeout).newCall(requestWithEmptyBody())
                        }

                        it("creates a BuildKit response body") {
                            assertThat(buildResponseBody.builderVersion, equalTo(BuilderVersion.BuildKit))
                        }
                    }

                    given("the build is running with all images re-pulled") {
                        val expectedCall by createForEachTest { clientWithLongTimeout.mock("POST", baseBuildUrl, daemonBuildResponse, 200) }

                        beforeEachTest { api.build(emptyMap(), dockerfilePath, imageTags, true, targetStage, ImageBuildOutputSink(null), BuildKitConfig(session), cancellationContext, {}) }

                        it("makes a call to the build API endpoint") {
                            verify(expectedCall).execute()
                        }

                        it("requests the daemon to re-pull all images") {
                            assertThat(expectedCall.request().url, hasQueryParameter("pull", "1"))
                        }
                    }

                    given("the build has no build args") {
                        val expectedCall by createForEachTest { clientWithLongTimeout.mock("POST", baseBuildUrl, daemonBuildResponse, 200) }

                        beforeEachTest { api.build(emptyMap(), dockerfilePath, imageTags, pullImage, targetStage, ImageBuildOutputSink(null), BuildKitConfig(session), cancellationContext, {}) }

                        it("makes a call to the build API endpoint") {
                            verify(expectedCall).execute()
                        }

                        it("includes an empty set of build args") {
                            assertThat(expectedCall.request().url, hasQueryParameter("buildargs", "{}"))
                        }
                    }

                    given("the build is for a particular stage in the Dockerfile") {
                        val expectedCall by createForEachTest { clientWithLongTimeout.mock("POST", baseBuildUrl, daemonBuildResponse, 200) }

                        beforeEachTest { api.build(emptyMap(), dockerfilePath, imageTags, pullImage, "the-target-stage", ImageBuildOutputSink(null), BuildKitConfig(session), cancellationContext, {}) }

                        it("makes a call to the build API endpoint") {
                            verify(expectedCall).execute()
                        }

                        it("includes the target stage name") {
                            assertThat(expectedCall.request().url, hasQueryParameter("target", "the-target-stage"))
                        }
                    }
                }

                given("the build fails immediately") {
                    beforeEachTest {
                        clientWithLongTimeout.mock("POST", baseBuildUrl, errorResponse, 418)
                    }

                    it("throws an appropriate exception") {
                        assertThat(
                            { api.build(buildArgs, dockerfilePath, imageTags, pullImage, targetStage, ImageBuildOutputSink(null), BuildKitConfig(session), cancellationContext, {}) },
                            throws<ImageBuildFailedException>(
                                withMessage("Building image failed: $errorMessageWithCorrectLineEndings")
                            )
                        )
                    }
                }

                given("the build fails during the build process") {
                    val output by createForEachTest { ImageBuildOutputSink(null) }

                    beforeEachTest {
                        clientWithLongTimeout.mock("POST", baseBuildUrl, daemonBuildResponse, 200)

                        buildResponseBody.outputToStream = "This is some output from the build process.\nThis is some more output from the build process.\n"
                        buildResponseBody.eventsToPost = listOf(
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(0, "FROM nginx:1.13.0"))),
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(1, "RUN exit 1"))),
                            BuildError("The command '/bin/sh -c exit 1' returned a non-zero code: 1")
                        )
                    }

                    it("throws an appropriate exception with all line endings corrected for the host system") {
                        assertThat(
                            { api.build(buildArgs, dockerfilePath, imageTags, pullImage, targetStage, output, BuildKitConfig(session), cancellationContext, {}) },
                            throws<ImageBuildFailedException>(
                                withMessage(
                                    "Building image failed: The command '/bin/sh -c exit 1' returned a non-zero code: 1. Output from build process was:SYSTEM_LINE_SEPARATOR" +
                                        "This is some output from the build process.SYSTEM_LINE_SEPARATOR" +
                                        "This is some more output from the build process."
                                )
                            )
                        )
                    }
                }

                given("the build process never sends an output line with the built image ID") {
                    beforeEachTest {
                        clientWithLongTimeout.mock("POST", baseBuildUrl, daemonBuildResponse, 200)

                        buildResponseBody.eventsToPost = listOf(
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(0, "FROM nginx:1.13.0"))),
                        )
                    }

                    it("throws an appropriate exception") {
                        assertThat(
                            { api.build(buildArgs, dockerfilePath, imageTags, pullImage, targetStage, ImageBuildOutputSink(null), BuildKitConfig(session), cancellationContext, {}) },
                            throws<ImageBuildFailedException>(
                                withMessage("Building image failed: daemon never sent built image ID.")
                            )
                        )
                    }
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
                val expectedHeadersForNoAuthentication = noHeaders
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

private class MockImageBuildResponseBody() : ImageBuildResponseBody {
    var outputToStream: String = ""
    var eventsToPost: List<ImageBuildEvent> = emptyList()
    lateinit var builderVersion: BuilderVersion
    lateinit var invokedWithOutputSink: ImageBuildOutputSink

    override fun readFrom(stream: Reader, outputSink: ImageBuildOutputSink, eventCallback: ImageBuildEventCallback) {
        invokedWithOutputSink = outputSink

        assertThat(stream.readText(), equalTo(daemonBuildResponse))

        outputSink.use { outputStream ->
            outputStream.writeString(outputToStream, Charsets.UTF_8).flush()
        }

        eventsToPost.forEach(eventCallback)
    }
}
