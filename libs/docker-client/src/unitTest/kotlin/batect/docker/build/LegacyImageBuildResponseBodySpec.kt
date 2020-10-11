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

package batect.docker.build

import batect.docker.DockerImage
import batect.docker.DownloadOperation
import batect.docker.ImageBuildFailedException
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.isEmptyString
import com.natpryce.hamkrest.throws
import okio.sink
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayOutputStream
import java.io.StringReader

object LegacyImageBuildResponseBodySpec : Spek({
    describe("a legacy image build response body") {
        val output by createForEachTest { ByteArrayOutputStream() }
        val outputStream by createForEachTest { output.sink() }
        val eventsPosted by createForEachTest { mutableListOf<ImageBuildEvent>() }
        val eventCallback: ImageBuildEventCallback = { e -> eventsPosted.add(e) }
        val body by createForEachTest { LegacyImageBuildResponseBody() }

        given("an empty response") {
            val input = ""

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts no events") {
                assertThat(eventsPosted, isEmpty)
            }

            it("produces no output") {
                assertThat(output.toString(), isEmptyString)
            }
        }

        given("a response with a single console output message") {
            val input = """
                {"stream":" ---x 817f2d3d51ec\n"}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts no events") {
                assertThat(eventsPosted, isEmpty)
            }

            it("streams the output message to the output stream") {
                assertThat(output.toString(), equalTo(" ---x 817f2d3d51ec\n"))
            }
        }

        given("a response with a single step starting message") {
            val input = """
                {"stream":"Step 1/2 : FROM postgres:13.0"}
                {"stream":"\n"}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts a step starting event") {
                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(0, "FROM postgres:13.0")), 2)
                        )
                    )
                )
            }

            it("streams the output message to the output stream") {
                assertThat(output.toString(), equalTo("Step 1/2 : FROM postgres:13.0\n"))
            }
        }

        given("a response with a series of step starting messages") {
            val input = """
                {"stream":"Step 1/2 : FROM postgres:13.0"}
                {"stream":"\n"}
                {"stream":"Step 2/2 : HEALTHCHECK --interval=0.1s CMD echo -n \"Hello from the healthcheck\""}
                {"stream":"\n"}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts a step starting event when each step starts, and a step complete event when each step finishes") {
                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(0, "FROM postgres:13.0")), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(1, "HEALTHCHECK --interval=0.1s CMD echo -n \"Hello from the healthcheck\"")), 2)
                        )
                    )
                )
            }

            it("streams the output messages to the output stream") {
                assertThat(
                    output.toString(),
                    equalTo(
                        """
                            |Step 1/2 : FROM postgres:13.0
                            |Step 2/2 : HEALTHCHECK --interval=0.1s CMD echo -n "Hello from the healthcheck"
                            |
                        """.trimMargin()
                    )
                )
            }
        }

        given("a response with an image ID message") {
            val input = """
                {"aux":{"ID":"sha256:af6fb21f41800ce17f3df91c26c3d03b775c2ad9208a5ca32c1141f1e9191b12"}}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts a build complete event") {
                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            BuildComplete(DockerImage("sha256:af6fb21f41800ce17f3df91c26c3d03b775c2ad9208a5ca32c1141f1e9191b12"))
                        )
                    )
                )
            }

            it("produces no output") {
                assertThat(output.toString(), isEmptyString)
            }
        }

        given("a response with an image ID message after some steps have started") {
            val input = """
                {"stream":"Step 1/2 : FROM postgres:13.0"}
                {"stream":"\n"}
                {"stream":"Step 2/2 : HEALTHCHECK --interval=0.1s CMD echo -n \"Hello from the healthcheck\""}
                {"stream":"\n"}
                {"aux":{"ID":"sha256:af6fb21f41800ce17f3df91c26c3d03b775c2ad9208a5ca32c1141f1e9191b12"}}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts a step starting event when each step starts, a step complete event when each step finishes, and a build complete event when the image ID is provided") {
                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(0, "FROM postgres:13.0")), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(1, "HEALTHCHECK --interval=0.1s CMD echo -n \"Hello from the healthcheck\"")), 2),
                            BuildComplete(DockerImage("sha256:af6fb21f41800ce17f3df91c26c3d03b775c2ad9208a5ca32c1141f1e9191b12"))
                        )
                    )
                )
            }
        }

        given("a response with a non-image download progress event that includes the total size of the download") {
            val input = """
                {"stream":"Step 2/2 : ADD http://httpbin.org/get test.txt"}
                {"stream":"\n"}
                {"status":"Downloading","progressDetail":{"current":329,"total":4159}}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts a step progress event") {
                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(1, "ADD http://httpbin.org/get test.txt")), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, "ADD http://httpbin.org/get test.txt", DownloadOperation.Downloading, 329, 4159)), 2)
                        )
                    )
                )
            }

            it("produces no output for the download") {
                assertThat(
                    output.toString(),
                    equalTo(
                        """
                            |Step 2/2 : ADD http://httpbin.org/get test.txt
                            |
                        """.trimMargin()
                    )
                )
            }
        }

        given("a response with a non-image download progress event with an unknown total size") {
            val input = """
                {"stream":"Step 2/2 : ADD http://httpbin.org/get test.txt"}
                {"stream":"\n"}
                {"status":"Downloading","progressDetail":{"current":329,"total":-1}}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts a step progress event") {
                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(1, "ADD http://httpbin.org/get test.txt")), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, "ADD http://httpbin.org/get test.txt", DownloadOperation.Downloading, 329, null)), 2)
                        )
                    )
                )
            }
        }

        // There are deeper tests for this in ImagePullProgressReporterSpec.
        given("a response with image download progress events") {
            val input = """
                {"stream":"Step 1/2 : FROM postgres:13.0"}
                {"stream":"\n"}
                {"status":"Pulling from library/postgres","id":"13.0"}
                {"status":"Pulling fs layer","progressDetail":{},"id":"abc123-layer-1"}
                {"status":"Pulling fs layer","progressDetail":{},"id":"abc123-layer-2"}
                {"status":"Pulling fs layer","progressDetail":{},"id":"abc123-layer-3"}
                {"status":"Pulling fs layer","progressDetail":{},"id":"abc123-layer-4"}
                {"status":"Waiting","progressDetail":{},"id":"abc123-layer-4"}
                {"status":"Downloading","progressDetail":{"current":423,"total":1772},"progress":"[===========\u003e                                       ]     423B/1.772kB","id":"abc123-layer-3"}
                {"status":"Downloading","progressDetail":{"current":42704,"total":4178135},"progress":"[\u003e                                                  ]   42.7kB/4.178MB","id":"abc123-layer-2"}
                {"status":"Downloading","progressDetail":{"current":281854,"total":27092161},"progress":"[\u003e                                                  ]  281.9kB/27.09MB","id":"abc123-layer-1"}
                {"status":"Downloading","progressDetail":{"current":1772,"total":1772},"progress":"[==================================================\u003e]  1.772kB/1.772kB","id":"abc123-layer-3"}
                {"status":"Verifying Checksum","progressDetail":{},"id":"abc123-layer-3"}
                {"status":"Download complete","progressDetail":{},"id":"abc123-layer-3"}
                {"status":"Downloading","progressDetail":{"current":5863899,"total":27092161},"progress":"[==========\u003e                                        ]  5.864MB/27.09MB","id":"abc123-layer-1"}
                {"status":"Downloading","progressDetail":{"current":4164061,"total":4178135},"progress":"[=================================================\u003e ]  4.164MB/4.178MB","id":"abc123-layer-2"}
                {"status":"Verifying Checksum","progressDetail":{},"id":"abc123-layer-2"}
                {"status":"Download complete","progressDetail":{},"id":"abc123-layer-2"}
                {"status":"Downloading","progressDetail":{"current":6978011,"total":27092161},"progress":"[============\u003e                                      ]  6.978MB/27.09MB","id":"abc123-layer-1"}
                {"status":"Verifying Checksum","progressDetail":{},"id":"abc123-layer-1"}
                {"status":"Download complete","progressDetail":{},"id":"abc123-layer-1"}
                {"status":"Extracting","progressDetail":{"current":294912,"total":27092161},"progress":"[\u003e                                                  ]  294.9kB/27.09MB","id":"abc123-layer-1"}
                {"status":"Pull complete","progressDetail":{},"id":"abc123-layer-1"}
                {"status":"Downloading","progressDetail":{"current":503039,"total":1419223},"progress":"[=================\u003e                                 ]    503kB/1.419MB","id":"abc123-layer-4"}
                {"status":"Verifying Checksum","progressDetail":{},"id":"abc123-layer-4"}
                {"status":"Download complete","progressDetail":{},"id":"abc123-layer-4"}
                {"status":"Extracting","progressDetail":{"current":65536,"total":4178135},"progress":"[\u003e                                                  ]  65.54kB/4.178MB","id":"abc123-layer-2"}
                {"status":"Extracting","progressDetail":{"current":4178134,"total":4178135},"progress":"[==================================================\u003e]  4.178MB/4.178MB","id":"abc123-layer-2"}
                {"status":"Pull complete","progressDetail":{},"id":"abc123-layer-2"}
                {"status":"Extracting","progressDetail":{"current":1772,"total":1772},"progress":"[==================================================\u003e]  1.772kB/1.772kB","id":"abc123-layer-3"}
                {"status":"Pull complete","progressDetail":{},"id":"abc123-layer-3"}
                {"status":"Extracting","progressDetail":{"current":1419223,"total":1419223},"progress":"[==================================================\u003e]  1.419MB/1.419MB","id":"abc123-layer-4"}
                {"status":"Pull complete","progressDetail":{},"id":"abc123-layer-4"}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts progress events as the download is performed") {
                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(0, "FROM postgres:13.0")), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.Downloading, 423, 1772)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.Downloading, 42704 + 423, 4178135 + 1772)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.Downloading, 281854 + 42704 + 423, 27092161 + 4178135 + 1772)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.Downloading, 281854 + 42704 + 1772, 27092161 + 4178135 + 1772)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.Downloading, 5863899 + 42704 + 1772, 27092161 + 4178135 + 1772)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.Downloading, 5863899 + 4164061 + 1772, 27092161 + 4178135 + 1772)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.Downloading, 5863899 + 4178135 + 1772, 27092161 + 4178135 + 1772)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.Downloading, 6978011 + 4178135 + 1772, 27092161 + 4178135 + 1772)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.VerifyingChecksum, 0 + 4178135 + 1772, 27092161 + 4178135 + 1772)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.DownloadComplete, 27092161 + 4178135 + 1772, 27092161 + 4178135 + 1772)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.Extracting, 294912 + 0 + 0, 27092161 + 4178135 + 1772)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.PullComplete, 27092161 + 0 + 0, 27092161 + 4178135 + 1772)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.Downloading, 27092161 + 4178135 + 1772 + 503039, 27092161 + 4178135 + 1772 + 1419223)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.VerifyingChecksum, 27092161 + 4178135 + 1772 + 0, 27092161 + 4178135 + 1772 + 1419223)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.PullComplete, 27092161 + 0 + 0 + 0, 27092161 + 4178135 + 1772 + 1419223)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.Extracting, 27092161 + 65536 + 0 + 0, 27092161 + 4178135 + 1772 + 1419223)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.Extracting, 27092161 + 4178134 + 0 + 0, 27092161 + 4178135 + 1772 + 1419223)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.PullComplete, 27092161 + 4178135 + 0 + 0, 27092161 + 4178135 + 1772 + 1419223)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.Extracting, 27092161 + 4178135 + 1772 + 0, 27092161 + 4178135 + 1772 + 1419223)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.PullComplete, 27092161 + 4178135 + 1772 + 0, 27092161 + 4178135 + 1772 + 1419223)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.Extracting, 27092161 + 4178135 + 1772 + 1419223, 27092161 + 4178135 + 1772 + 1419223)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.PullComplete, 27092161 + 4178135 + 1772 + 1419223, 27092161 + 4178135 + 1772 + 1419223)), 2),
                        )
                    )
                )
            }

            it("produces no output for the download") {
                assertThat(
                    output.toString(),
                    equalTo(
                        """
                            |Step 1/2 : FROM postgres:13.0
                            |
                        """.trimMargin()
                    )
                )
            }
        }

        given("a response with image download progress events from multiple stages in a multi-stage build") {
            val input = """
                {"stream":"Step 1/2 : FROM postgres:13.0"}
                {"stream":"\n"}
                {"status":"Downloading","progressDetail":{"current":423,"total":1772},"progress":"[===========\u003e                                       ]     423B/1.772kB","id":"abc123-layer-1"}
                {"status":"Downloading","progressDetail":{"current":500,"total":1772},"progress":"[===========\u003e                                       ]     423B/1.772kB","id":"abc123-layer-1"}
                {"stream":"Step 2/2 : FROM postgres:12.0"}
                {"stream":"\n"}
                {"status":"Downloading","progressDetail":{"current":900,"total":1300},"progress":"[===========\u003e                                       ]     423B/1.772kB","id":"def456-layer-2"}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts progress events as the download is performed") {
                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(0, "FROM postgres:13.0")), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.Downloading, 423, 1772)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(0, "FROM postgres:13.0", DownloadOperation.Downloading, 500, 1772)), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(1, "FROM postgres:12.0")), 2),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, "FROM postgres:12.0", DownloadOperation.Downloading, 900, 1300)), 2),
                        )
                    )
                )
            }

            it("produces no output for the download") {
                assertThat(
                    output.toString(),
                    equalTo(
                        """
                            |Step 1/2 : FROM postgres:13.0
                            |Step 2/2 : FROM postgres:12.0
                            |
                        """.trimMargin()
                    )
                )
            }
        }

        given("a response with an error message") {
            val input = """
                {"errorDetail":{"code":1,"message":"The command '/bin/sh -c exit 1' returned a non-zero code: 1"},"error":"The command '/bin/sh -c exit 1' returned a non-zero code: 1"}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts a build error event") {
                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            BuildError("The command '/bin/sh -c exit 1' returned a non-zero code: 1")
                        )
                    )
                )
            }

            it("streams the error message to the output stream") {
                assertThat(output.toString(), equalTo("The command '/bin/sh -c exit 1' returned a non-zero code: 1"))
            }
        }

        mapOf(
            "\n" to """""""",
            "{\n" to """"{""""
        ).forEach { (input, description) ->
            given("a response with the malformed input $description") {
                it("throws an appropriate exception") {
                    assertThat(
                        { body.readFrom(StringReader(input), outputStream, eventCallback) },
                        throws<ImageBuildFailedException>(withMessage("Received malformed response from Docker daemon during build: $description"))
                    )
                }
            }
        }
    }
})
