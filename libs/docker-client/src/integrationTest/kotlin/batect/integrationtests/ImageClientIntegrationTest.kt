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

package batect.integrationtests

import batect.docker.DockerImage
import batect.docker.ImageBuildFailedException
import batect.docker.api.BuilderVersion
import batect.docker.client.ImageBuildRequest
import batect.os.DefaultPathResolutionContext
import batect.os.ProcessOutput
import batect.os.ProcessRunner
import batect.primitives.CancellationContext
import batect.testutils.createForGroup
import batect.testutils.runBeforeGroup
import com.fasterxml.jackson.databind.ObjectMapper
import com.natpryce.hamkrest.anyElement
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.hasElement
import com.natpryce.hamkrest.matches
import com.natpryce.hamkrest.or
import okio.sink
import org.araqnid.hamkrest.json.json
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.dsl.Skip
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayOutputStream
import java.util.UUID

object ImageClientIntegrationTest : Spek({
    describe("a Docker images client") {
        val client by createForGroup { createClient() }

        describe("pulling an image that has not been cached locally already") {
            beforeGroup { removeImage("hello-world:latest") }
            val image by runBeforeGroup { client.pull("hello-world:latest") }

            it("pulls the image successfully") {
                assertThat(image, equalTo(DockerImage("hello-world:latest")))
            }
        }

        describe("building a basic image with BuildKit", skip = if (runBuildKitTests) Skip.No else Skip.Yes("not supported on this version of Docker")) {
            val cacheBustingId by createForGroup { UUID.randomUUID().toString() }
            val imageDirectory = testImagesDirectory.resolve("basic-image")
            val output by createForGroup { ByteArrayOutputStream() }

            runBeforeGroup {
                val request = ImageBuildRequest(
                    imageDirectory,
                    mapOf("CACHE_BUSTING_ID" to cacheBustingId),
                    "Dockerfile",
                    DefaultPathResolutionContext(imageDirectory),
                    setOf("batect-integration-tests-basic-image-buildkit"),
                    false
                )

                client.images.build(
                    request,
                    BuilderVersion.BuildKit,
                    output.sink(),
                    CancellationContext()
                ) {}
            }

            it("includes all expected output in the response") {
                val lines = output.toString().lines()

                assertThat(lines, anyElement(matches("""^#(1|2) \[internal\] load build definition from Dockerfile""".toRegex())))
                assertThat(lines, anyElement(matches("""^#(1|2) \[internal\] load \.dockerignore""".toRegex())))
                assertThat(lines, anyElement(matches("""^#3 \[internal] load metadata for docker.io/library/alpine:.*""".toRegex())))
                assertThat(lines, anyElement(matches("""^#(4|5) \[internal] load build context""".toRegex())))
                assertThat(lines, anyElement(matches("""^#(4|5) \[1/3] FROM docker.io/library/alpine:.*""".toRegex())))
                assertThat(lines, hasElement("#6 [2/3] COPY test.sh /test-$cacheBustingId.sh"))
                assertThat(lines, hasElement("#7 [3/3] RUN /test-$cacheBustingId.sh"))
                assertThat(lines, anyElement(matches("""^#7 \d+\.\d+ Hello from the script!""".toRegex())))
                assertThat(lines, hasElement("#8 exporting to image"))

                assertThat(lines, hasElement("#1 CACHED") or hasElement("#1 DONE"))
                assertThat(lines, hasElement("#2 CACHED") or hasElement("#2 DONE"))
                assertThat(lines, hasElement("#3 CACHED") or hasElement("#3 DONE"))
                assertThat(lines, hasElement("#4 CACHED") or hasElement("#4 DONE"))
                assertThat(lines, hasElement("#5 CACHED") or hasElement("#5 DONE"))
                assertThat(lines, hasElement("#6 DONE"))
                assertThat(lines, hasElement("#7 DONE"))
                assertThat(lines, hasElement("#8 CACHED") or hasElement("#8 DONE"))
            }
        }

        describe("building images") {
            mapOf(
                "the legacy builder" to BuilderVersion.Legacy,
                "BuildKit" to BuilderVersion.BuildKit
            ).forEach { (description, builderVersion) ->
                describe("using $description", skip = if (builderVersion != BuilderVersion.BuildKit || runBuildKitTests) Skip.No else Skip.Yes("not supported on this version of Docker")) {
                    val cacheBustingId by createForGroup { UUID.randomUUID().toString() }

                    fun buildImage(path: String, dockerfileName: String = "Dockerfile", targetStage: String? = null, buildArgs: Map<String, String> = emptyMap()): DockerImage {
                        val imageDirectory = testImagesDirectory.resolve(path)
                        val output = ByteArrayOutputStream()

                        try {
                            val request = ImageBuildRequest(
                                imageDirectory,
                                mapOf("CACHE_BUSTING_ID" to cacheBustingId) + buildArgs,
                                dockerfileName,
                                DefaultPathResolutionContext(imageDirectory),
                                setOf("batect-integration-tests-$path-${builderVersion.toString().lowercase()}"),
                                false,
                                targetStage
                            )

                            return client.images.build(
                                request,
                                builderVersion,
                                output.sink(),
                                CancellationContext()
                            ) {}
                        } catch (e: ImageBuildFailedException) {
                            throw ImageBuildFailedException("Image build failed with an exception. Output was:\n$output", e)
                        }
                    }

                    describe("building an image with an add operation from a URL") {
                        val image by runBeforeGroup { buildImage("dockerfile-with-add-from-url") }
                        val output by runBeforeGroup { executeCommandInContainer(image, "cat", "/test.txt") }

                        it("successfully downloads and stores the file") {
                            assertThat(
                                output,
                                equalTo(
                                    """
                                        |User-agent: *
                                        |Disallow: /deny
                                        |
                                    """.trimMargin()
                                )
                            )
                        }
                    }

                    describe("building an image with a .dockerignore file") {
                        val image by runBeforeGroup { buildImage("dockerignore") }
                        val output by runBeforeGroup { executeCommandInContainer(image, "sh", "-c", "find /app | sort") }

                        it("correctly includes only the permitted files in the build context") {
                            assertThat(
                                output,
                                equalTo(
                                    """
                                        |/app
                                        |/app/files
                                        |/app/files/other_include.txt
                                        |/app/include.txt
                                        |
                                    """.trimMargin()
                                )
                            )
                        }
                    }

                    describe("building an image with a non-default Dockerfile name and a .dockerignore file") {
                        val image by runBeforeGroup { buildImage("dockerignore-custom-dockerfile", "dockerfiles/my-special-dockerfile") }
                        val output by runBeforeGroup { executeCommandInContainer(image, "sh", "-c", "find /app | sort") }

                        it("correctly includes only the permitted files in the build context") {
                            val expectedOutput = when (builderVersion) {
                                BuilderVersion.BuildKit ->
                                    """
                                        |/app
                                        |/app/files
                                        |/app/files/other_include.txt
                                        |/app/include.txt
                                        |
                                    """.trimMargin()
                                BuilderVersion.Legacy ->
                                    """
                                        |/app
                                        |/app/dockerfiles
                                        |/app/files
                                        |/app/files/other_include.txt
                                        |/app/include.txt
                                        |
                                    """.trimMargin()
                            }

                            assertThat(output, equalTo(expectedOutput))
                        }
                    }

                    describe("building an image with a symlink in the build context") {
                        val image by runBeforeGroup { buildImage("symlink-in-build-context") }
                        val output by runBeforeGroup { executeCommandInContainer(image, "tree", "-J", "--noreport", "/everything") }

                        it("correctly copies the linked file into the resulting image") {
                            val expectedContents = ObjectMapper().readTree(
                                """
                                    [
                                      {
                                        "type": "directory",
                                        "name": "/everything",
                                        "contents": [
                                          { "type": "file", "name": "Dockerfile" },
                                          { "type": "link", "name": "link-to-original.txt", "target": "original.txt", "contents": [] },
                                          { "type": "file", "name": "original.txt" }
                                        ]
                                      }
                                    ]
                                """
                            )

                            assertThat(output, json(equalTo(expectedContents)))
                        }
                    }

                    describe("building an image with a particular target") {
                        val image by runBeforeGroup { buildImage("multistage", targetStage = "stage1") }
                        val output by runBeforeGroup { executeCommandInContainer(image, "cat", "/stage-name") }

                        it("executes the desired stage") {
                            assertThat(output.trim(), equalTo("stage1"))
                        }
                    }

                    describe("building an image with a build arg") {
                        val image by runBeforeGroup { buildImage("build-arg", buildArgs = mapOf("SOME_BUILD_ARG" to "This is the value of the build arg")) }
                        val output by runBeforeGroup { executeCommandInContainer(image, "cat", "/build_arg.txt") }

                        it("passes the value of the build arg to the image build process") {
                            assertThat(output.trim(), equalTo("This is the value of the build arg"))
                        }
                    }
                }
            }
        }
    }
})

private fun removeImage(imageName: String) {
    val processRunner = ProcessRunner(mock())
    val result = processRunner.runAndCaptureOutput(listOf("docker", "rmi", "-f", imageName))

    assertThat(result, has(ProcessOutput::output, containsSubstring("No such image: $imageName")) or has(ProcessOutput::exitCode, equalTo(0)))
}

private fun executeCommandInContainer(image: DockerImage, vararg command: String): String {
    val processRunner = ProcessRunner(mock())
    val result = processRunner.runAndCaptureOutput(listOf("docker", "run", "--rm", image.id) + command)

    assertThat(result, has(ProcessOutput::exitCode, equalTo(0)))

    return result.output
}
