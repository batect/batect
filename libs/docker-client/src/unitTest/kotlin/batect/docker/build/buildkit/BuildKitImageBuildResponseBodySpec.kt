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

package batect.docker.build.buildkit

import batect.docker.DockerImage
import batect.docker.DownloadOperation
import batect.docker.ImageBuildFailedException
import batect.docker.build.ActiveImageBuildStep
import batect.docker.build.BuildComplete
import batect.docker.build.BuildError
import batect.docker.build.BuildProgress
import batect.docker.build.ImageBuildEvent
import batect.docker.build.ImageBuildEventCallback
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

object BuildKitImageBuildResponseBodySpec : Spek({
    describe("a BuildKit image build response body") {
        val output by createForEachTest { ByteArrayOutputStream() }
        val outputStream by createForEachTest { output.sink() }
        val eventsPosted by createForEachTest { mutableListOf<ImageBuildEvent>() }
        val eventCallback: ImageBuildEventCallback = { e -> eventsPosted.add(e) }
        val body by createForEachTest { BuildKitImageBuildResponseBody() }

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

        given("a response with an error message") {
            val input = """
                {"errorDetail":{"message":"failed to solve with frontend dockerfile.v0: failed to build LLB: executor failed running [/bin/sh -c exit 1]: runc did not terminate sucessfully"},"error":"failed to solve with frontend dockerfile.v0: failed to build LLB: executor failed running [/bin/sh -c exit 1]: runc did not terminate sucessfully"}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts a build error event") {
                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            BuildError("failed to solve with frontend dockerfile.v0: failed to build LLB: executor failed running [/bin/sh -c exit 1]: runc did not terminate sucessfully")
                        )
                    )
                )
            }

            it("produces no output") {
                // We don't need to write anything to the output - error output comes from status updates.
                assertThat(output.toString(), isEmptyString)
            }
        }

        given("a response with an image ID") {
            val input = """
                {"id":"moby.image.id","aux":{"ID":"sha256:a91e78399e9bb1cf844a290323ae8731b78dad99b647db80987260383c13694a"}}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts a build complete event") {
                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            BuildComplete(DockerImage("sha256:a91e78399e9bb1cf844a290323ae8731b78dad99b647db80987260383c13694a"))
                        )
                    )
                )
            }

            it("produces no output") {
                assertThat(output.toString(), isEmptyString)
            }
        }

        given("a response with a series of build trace messages that culminates in the build succeeding") {
            // The 'aux' field is a base64-encoded byte array that can be decoded as a StatusResponse message from github.com/moby/buildkit/api/services/control/control.proto. Decode with the buildkit_decode.sh script in this directory,
            // or: echo "<value of 'aux' field>" | base64 --decode | protoc --decode moby.buildkit.v1.StatusResponse --proto_path ~/go/src/github.com/docker/cli/vendor/github.com/moby/buildkit/api/services/control --proto_path ~/go/src/github.com/docker/cli/vendor/ control.proto

            val input = """
                {"id":"moby.buildkit.trace","aux":"Cn0KR3NoYTI1NjpiMGQwNWRlMGY3ZTYzZWU2ZGFjOWEzZjA1MzE4ZWYyZmM1NzIwM2EzYWNjOTQzOGI5NWQwYTU1MGNkNGJlOGNlGiRbaW50ZXJuYWxdIGxvYWQgcmVtb3RlIGJ1aWxkIGNvbnRleHQqDAiivbH7BRCbtL+kAw=="}
                {"id":"moby.buildkit.trace","aux":"CosBCkdzaGEyNTY6YjBkMDVkZTBmN2U2M2VlNmRhYzlhM2YwNTMxOGVmMmZjNTcyMDNhM2FjYzk0MzhiOTVkMGE1NTBjZDRiZThjZRokW2ludGVybmFsXSBsb2FkIHJlbW90ZSBidWlsZCBjb250ZXh0KgwIor2x+wUQm7S/pAMyDAiivbH7BRDzjsOnAw=="}
                {"id":"moby.buildkit.trace","aux":"CosBCkdzaGEyNTY6YjBkMDVkZTBmN2U2M2VlNmRhYzlhM2YwNTMxOGVmMmZjNTcyMDNhM2FjYzk0MzhiOTVkMGE1NTBjZDRiZThjZRokW2ludGVybmFsXSBsb2FkIHJlbW90ZSBidWlsZCBjb250ZXh0KgwIor2x+wUQs/jMpwMyDAiivbH7BRDDp82nAw=="}
                {"id":"moby.buildkit.trace","aux":"CqMBCkdzaGEyNTY6NzM4OWI1MjczOTkxNzEyOTk0YjllMTdlMWQzZTAyOWFjNDg5MjE4ZmFkNGUzN2UwNjNkMzBkZTkwOTQ1NWI4NBJHc2hhMjU2OmIwZDA1ZGUwZjdlNjNlZTZkYWM5YTNmMDUzMThlZjJmYzU3MjAzYTNhY2M5NDM4Yjk1ZDBhNTUwY2Q0YmU4Y2UaD2NvcHkgL2NvbnRleHQgLw=="}
                {"id":"moby.buildkit.trace","aux":"CrEBCkdzaGEyNTY6NzM4OWI1MjczOTkxNzEyOTk0YjllMTdlMWQzZTAyOWFjNDg5MjE4ZmFkNGUzN2UwNjNkMzBkZTkwOTQ1NWI4NBJHc2hhMjU2OmIwZDA1ZGUwZjdlNjNlZTZkYWM5YTNmMDUzMThlZjJmYzU3MjAzYTNhY2M5NDM4Yjk1ZDBhNTUwY2Q0YmU4Y2UaD2NvcHkgL2NvbnRleHQgLyoMCKK9sfsFEOHSwLID"}
                {"id":"moby.buildkit.trace","aux":"Cr8BCkdzaGEyNTY6NzM4OWI1MjczOTkxNzEyOTk0YjllMTdlMWQzZTAyOWFjNDg5MjE4ZmFkNGUzN2UwNjNkMzBkZTkwOTQ1NWI4NBJHc2hhMjU2OmIwZDA1ZGUwZjdlNjNlZTZkYWM5YTNmMDUzMThlZjJmYzU3MjAzYTNhY2M5NDM4Yjk1ZDBhNTUwY2Q0YmU4Y2UaD2NvcHkgL2NvbnRleHQgLyoMCKK9sfsFEOHSwLIDMgwIor2x+wUQ7qG70AM="}
                {"id":"moby.buildkit.trace","aux":"CpUBCkdzaGEyNTY6NTM3YmY0YjFkYmI2NjBjMzBhMzY4MDUwN2FiMDY5NDU2OTg0YzU4ZDcxNWNlYmVjNTFjZjE1MzQzYTFkNmRjNho8W2ludGVybmFsXSBsb2FkIG1ldGFkYXRhIGZvciBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4wKgwIor2x+wUQnPbR2wM="}
                {"id":"moby.buildkit.trace","aux":"CqMBCkdzaGEyNTY6NTM3YmY0YjFkYmI2NjBjMzBhMzY4MDUwN2FiMDY5NDU2OTg0YzU4ZDcxNWNlYmVjNTFjZjE1MzQzYTFkNmRjNho8W2ludGVybmFsXSBsb2FkIG1ldGFkYXRhIGZvciBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4wKgwIor2x+wUQnPbR2wMyDAiivbH7BRCgjPbbAw=="}
                {"id":"moby.buildkit.trace","aux":"CnUKR3NoYTI1NjpmZGFkNjcxYTkxMmIyZDAxMDMzM2FmMzBkMDUwMzY3ZDdmMGM2N2Y2YmQ0OGU0M2JjM2EyZWVkNzg5MTQ3ZDMyGipbMS8xXSBGUk9NIGRvY2tlci5pby9saWJyYXJ5L2FscGluZTozLjEyLjA="}
                {"id":"moby.buildkit.trace","aux":"CoMBCkdzaGEyNTY6ZmRhZDY3MWE5MTJiMmQwMTAzMzNhZjMwZDA1MDM2N2Q3ZjBjNjdmNmJkNDhlNDNiYzNhMmVlZDc4OTE0N2QzMhoqWzEvMV0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4wKgwIor2x+wUQ1Jqa3AM="}
                {"id":"moby.buildkit.trace","aux":"CpEBCkdzaGEyNTY6ZmRhZDY3MWE5MTJiMmQwMTAzMzNhZjMwZDA1MDM2N2Q3ZjBjNjdmNmJkNDhlNDNiYzNhMmVlZDc4OTE0N2QzMhoqWzEvMV0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4wKgwIor2x+wUQ1Jqa3AMyDAiivbH7BRCTx73cAw=="}
                {"id":"moby.buildkit.trace","aux":"CpEBCkdzaGEyNTY6ZmRhZDY3MWE5MTJiMmQwMTAzMzNhZjMwZDA1MDM2N2Q3ZjBjNjdmNmJkNDhlNDNiYzNhMmVlZDc4OTE0N2QzMhoqWzEvMV0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4wIAEqDAiivbH7BRDvsencAzIKCKO9sfsFELyAAQppCkdzaGEyNTY6ZThjNjEzZTA3YjBiN2ZmMzM4OTNiNjk0Zjc3NTlhMTBkNDJlMTgwZjJiNGRjMzQ5ZmI1N2RjNmI3MWRjYWIwMBoSZXhwb3J0aW5nIHRvIGltYWdlKgoIo72x+wUQpJ4DEn8KEGV4cG9ydGluZyBsYXllcnMSR3NoYTI1NjplOGM2MTNlMDdiMGI3ZmYzMzg5M2I2OTRmNzc1OWExMGQ0MmUxODBmMmI0ZGMzNDlmYjU3ZGM2YjcxZGNhYjAwMgoIo72x+wUQivwLOgoIo72x+wUQoLcDQgoIo72x+wUQ5vYL"}
                {"id":"moby.buildkit.trace","aux":"ErgBClV3cml0aW5nIGltYWdlIHNoYTI1NjphOTFlNzgzOTllOWJiMWNmODQ0YTI5MDMyM2FlODczMWI3OGRhZDk5YjY0N2RiODA5ODcyNjAzODNjMTM2OTRhEkdzaGEyNTY6ZThjNjEzZTA3YjBiN2ZmMzM4OTNiNjk0Zjc3NTlhMTBkNDJlMTgwZjJiNGRjMzQ5ZmI1N2RjNmI3MWRjYWIwMDIKCKO9sfsFEPbHIDoKCKO9sfsFELbFIA=="}
                {"id":"moby.buildkit.trace","aux":"EsYBClV3cml0aW5nIGltYWdlIHNoYTI1NjphOTFlNzgzOTllOWJiMWNmODQ0YTI5MDMyM2FlODczMWI3OGRhZDk5YjY0N2RiODA5ODcyNjAzODNjMTM2OTRhEkdzaGEyNTY6ZThjNjEzZTA3YjBiN2ZmMzM4OTNiNjk0Zjc3NTlhMTBkNDJlMTgwZjJiNGRjMzQ5ZmI1N2RjNmI3MWRjYWIwMDILCKO9sfsFEIj+uQE6CgijvbH7BRC2xSBCCwijvbH7BRCO9LkBEp8BCjpuYW1pbmcgdG8gZG9ja2VyLmlvL2xpYnJhcnkvYmF0ZWN0LWludGVncmF0aW9uLXRlc3RzLWltYWdlEkdzaGEyNTY6ZThjNjEzZTA3YjBiN2ZmMzM4OTNiNjk0Zjc3NTlhMTBkNDJlMTgwZjJiNGRjMzQ5ZmI1N2RjNmI3MWRjYWIwMDILCKO9sfsFELyfvAE6CwijvbH7BRDjnLwB"}
                {"id":"moby.image.id","aux":{"ID":"sha256:a91e78399e9bb1cf844a290323ae8731b78dad99b647db80987260383c13694a"}}
                {"id":"moby.buildkit.trace","aux":"CnYKR3NoYTI1NjplOGM2MTNlMDdiMGI3ZmYzMzg5M2I2OTRmNzc1OWExMGQ0MmUxODBmMmI0ZGMzNDlmYjU3ZGM2YjcxZGNhYjAwGhJleHBvcnRpbmcgdG8gaW1hZ2UqCgijvbH7BRCkngMyCwijvbH7BRC1uKUCEqwBCjpuYW1pbmcgdG8gZG9ja2VyLmlvL2xpYnJhcnkvYmF0ZWN0LWludGVncmF0aW9uLXRlc3RzLWltYWdlEkdzaGEyNTY6ZThjNjEzZTA3YjBiN2ZmMzM4OTNiNjk0Zjc3NTlhMTBkNDJlMTgwZjJiNGRjMzQ5ZmI1N2RjNmI3MWRjYWIwMDILCKO9sfsFEKLDowI6CwijvbH7BRDjnLwBQgsIo72x+wUQ3LujAg=="}
                {"stream":"Successfully tagged batect-integration-tests-image:latest\n"}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts build status messages as the build progresses") {
                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(0, "[internal] load remote build context"))),
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(1, "copy /context /"))),
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(2, "[internal] load metadata for docker.io/library/alpine:3.12.0"))),
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(3, "[1/1] FROM docker.io/library/alpine:3.12.0"))),
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(4, "exporting to image"))),
                            BuildComplete(DockerImage("sha256:a91e78399e9bb1cf844a290323ae8731b78dad99b647db80987260383c13694a"))
                        )
                    )
                )
            }

            it("streams output showing the progression of the build") {
                assertThat(
                    output.toString(),
                    equalTo(
                        """
                        |#1 [internal] load remote build context
                        |#1 DONE
                        |
                        |#2 copy /context /
                        |#2 DONE
                        |
                        |#3 [internal] load metadata for docker.io/library/alpine:3.12.0
                        |#3 DONE
                        |
                        |#4 [1/1] FROM docker.io/library/alpine:3.12.0
                        |#4 CACHED
                        |
                        |#5 exporting to image
                        |#5 exporting layers: done
                        |#5 writing image sha256:a91e78399e9bb1cf844a290323ae8731b78dad99b647db80987260383c13694a: done
                        |#5 naming to docker.io/library/batect-integration-tests-image: done
                        |#5 DONE
                        |
                        |
                        """.trimMargin()
                    )
                )
            }
        }

        given("a response with a series of build trace messages that culminates in the build failing") {
            val input = """
                {"id":"moby.buildkit.trace","aux":"Cm8KR3NoYTI1NjpkOGE5MTRhNDk0MTA0ZTVlMTU0NzQ4NDJiMDUxMDJlNDY0YWE3YmUwOGI4MTk5NjE0MjczNmM3YzdlZjZjYWY0GiRbaW50ZXJuYWxdIGxvYWQgcmVtb3RlIGJ1aWxkIGNvbnRleHQ="}
                {"id":"moby.buildkit.trace","aux":"Cn0KR3NoYTI1NjpkOGE5MTRhNDk0MTA0ZTVlMTU0NzQ4NDJiMDUxMDJlNDY0YWE3YmUwOGI4MTk5NjE0MjczNmM3YzdlZjZjYWY0GiRbaW50ZXJuYWxdIGxvYWQgcmVtb3RlIGJ1aWxkIGNvbnRleHQqDAjf/7n7BRCameK1Aw=="}
                {"id":"moby.buildkit.trace","aux":"CosBCkdzaGEyNTY6ZDhhOTE0YTQ5NDEwNGU1ZTE1NDc0ODQyYjA1MTAyZTQ2NGFhN2JlMDhiODE5OTYxNDI3MzZjN2M3ZWY2Y2FmNBokW2ludGVybmFsXSBsb2FkIHJlbW90ZSBidWlsZCBjb250ZXh0KgwI3/+5+wUQmpnitQMyDAjf/7n7BRDCvMS4Aw=="}
                {"id":"moby.buildkit.trace","aux":"CosBCkdzaGEyNTY6ZDhhOTE0YTQ5NDEwNGU1ZTE1NDc0ODQyYjA1MTAyZTQ2NGFhN2JlMDhiODE5OTYxNDI3MzZjN2M3ZWY2Y2FmNBokW2ludGVybmFsXSBsb2FkIHJlbW90ZSBidWlsZCBjb250ZXh0KgwI3/+5+wUQv+vPuAMyDAjf/7n7BRDlttC4Aw=="}
                {"id":"moby.buildkit.trace","aux":"CqMBCkdzaGEyNTY6MmU3ZjhjNGI2Y2EwMTJiNGY0Y2UxZTNkMGU0MTBlZjNlNDBkZDBmYjI0NmQyZGFhZGIyMjFmOGIyODcxOTAwNxJHc2hhMjU2OmQ4YTkxNGE0OTQxMDRlNWUxNTQ3NDg0MmIwNTEwMmU0NjRhYTdiZTA4YjgxOTk2MTQyNzM2YzdjN2VmNmNhZjQaD2NvcHkgL2NvbnRleHQgLw=="}
                {"id":"moby.buildkit.trace","aux":"CrEBCkdzaGEyNTY6MmU3ZjhjNGI2Y2EwMTJiNGY0Y2UxZTNkMGU0MTBlZjNlNDBkZDBmYjI0NmQyZGFhZGIyMjFmOGIyODcxOTAwNxJHc2hhMjU2OmQ4YTkxNGE0OTQxMDRlNWUxNTQ3NDg0MmIwNTEwMmU0NjRhYTdiZTA4YjgxOTk2MTQyNzM2YzdjN2VmNmNhZjQaD2NvcHkgL2NvbnRleHQgLyoMCN//ufsFEKuKhcUD"}
                {"id":"moby.buildkit.trace","aux":"Cr4BCkdzaGEyNTY6MmU3ZjhjNGI2Y2EwMTJiNGY0Y2UxZTNkMGU0MTBlZjNlNDBkZDBmYjI0NmQyZGFhZGIyMjFmOGIyODcxOTAwNxJHc2hhMjU2OmQ4YTkxNGE0OTQxMDRlNWUxNTQ3NDg0MmIwNTEwMmU0NjRhYTdiZTA4YjgxOTk2MTQyNzM2YzdjN2VmNmNhZjQaD2NvcHkgL2NvbnRleHQgLyoMCN//ufsFEKuKhcUDMgsI4P+5+wUQ8oWvAw=="}
                {"id":"moby.buildkit.trace","aux":"CpQBCkdzaGEyNTY6NTM3YmY0YjFkYmI2NjBjMzBhMzY4MDUwN2FiMDY5NDU2OTg0YzU4ZDcxNWNlYmVjNTFjZjE1MzQzYTFkNmRjNho8W2ludGVybmFsXSBsb2FkIG1ldGFkYXRhIGZvciBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4wKgsI4P+5+wUQn8uEDQ=="}
                {"id":"moby.buildkit.trace","aux":"CqEBCkdzaGEyNTY6NTM3YmY0YjFkYmI2NjBjMzBhMzY4MDUwN2FiMDY5NDU2OTg0YzU4ZDcxNWNlYmVjNTFjZjE1MzQzYTFkNmRjNho8W2ludGVybmFsXSBsb2FkIG1ldGFkYXRhIGZvciBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4wKgsI4P+5+wUQn8uEDTILCOD/ufsFEP2vkA0="}
                {"id":"moby.buildkit.trace","aux":"CrABCkdzaGEyNTY6MWM1YzdlMmY1ZDVhZmFkNTg2OWNlMjkzMjU1Y2FmZDliNTkzYTNhYTU1NDY3YzU0ZWViMWY1YzM3MzQ4MDhkMhJHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzIaHFsyLzNdIFJVTiBlY2hvICJoZWxsbyB3b3JsZCIKpAEKR3NoYTI1NjplZTc2MTBjZDRiYjQxN2QyOWUwNzI2ZTQ1Yzk1NWI5MDg1NzliNGNmNmQ1MzRlYjg1OWViYWI0MDgyYTUxNTVlEkdzaGEyNTY6MWM1YzdlMmY1ZDVhZmFkNTg2OWNlMjkzMjU1Y2FmZDliNTkzYTNhYTU1NDY3YzU0ZWViMWY1YzM3MzQ4MDhkMhoQWzMvM10gUlVOIGV4aXQgMQqPAQpHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzIaKlsxLzNdIEZST00gZG9ja2VyLmlvL2xpYnJhcnkvYWxwaW5lOjMuMTIuMCoLCOD/ufsFEJDAsA0yCwjg/7n7BRCsgr4N"}
                {"id":"moby.buildkit.trace","aux":"Co8BCkdzaGEyNTY6ZmRhZDY3MWE5MTJiMmQwMTAzMzNhZjMwZDA1MDM2N2Q3ZjBjNjdmNmJkNDhlNDNiYzNhMmVlZDc4OTE0N2QzMhoqWzEvM10gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4wKgsI4P+5+wUQpPXSDTILCOD/ufsFEPac4Q0KvQEKR3NoYTI1NjoxYzVjN2UyZjVkNWFmYWQ1ODY5Y2UyOTMyNTVjYWZkOWI1OTNhM2FhNTU0NjdjNTRlZWIxZjVjMzczNDgwOGQyEkdzaGEyNTY6ZmRhZDY3MWE5MTJiMmQwMTAzMzNhZjMwZDA1MDM2N2Q3ZjBjNjdmNmJkNDhlNDNiYzNhMmVlZDc4OTE0N2QzMhocWzIvM10gUlVOIGVjaG8gImhlbGxvIHdvcmxkIioLCOD/ufsFENnG6g0SmQEKJ3Jlc29sdmUgZG9ja2VyLmlvL2xpYnJhcnkvYWxwaW5lOjMuMTIuMBJHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzIyCwjg/7n7BRCfsNQNOgsI4P+5+wUQ89DTDUILCOD/ufsFEMKu1A0="}
                {"id":"moby.buildkit.trace","aux":"GmYKR3NoYTI1NjoxYzVjN2UyZjVkNWFmYWQ1ODY5Y2UyOTMyNTVjYWZkOWI1OTNhM2FhNTU0NjdjNTRlZWIxZjVjMzczNDgwOGQyEgsI4P+5+wUQwoK+ZBgBIgxoZWxsbyB3b3JsZAo="}
                {"id":"moby.buildkit.trace","aux":"CsoBCkdzaGEyNTY6MWM1YzdlMmY1ZDVhZmFkNTg2OWNlMjkzMjU1Y2FmZDliNTkzYTNhYTU1NDY3YzU0ZWViMWY1YzM3MzQ4MDhkMhJHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzIaHFsyLzNdIFJVTiBlY2hvICJoZWxsbyB3b3JsZCIqCwjg/7n7BRDZxuoNMgsI4P+5+wUQ/PfheQ=="}
                {"id":"moby.buildkit.trace","aux":"CrEBCkdzaGEyNTY6ZWU3NjEwY2Q0YmI0MTdkMjllMDcyNmU0NWM5NTViOTA4NTc5YjRjZjZkNTM0ZWI4NTllYmFiNDA4MmE1MTU1ZRJHc2hhMjU2OjFjNWM3ZTJmNWQ1YWZhZDU4NjljZTI5MzI1NWNhZmQ5YjU5M2EzYWE1NTQ2N2M1NGVlYjFmNWMzNzM0ODA4ZDIaEFszLzNdIFJVTiBleGl0IDEqCwjg/7n7BRD8r8Z6"}
                {"id":"moby.buildkit.trace","aux":"CpACCkdzaGEyNTY6ZWU3NjEwY2Q0YmI0MTdkMjllMDcyNmU0NWM5NTViOTA4NTc5YjRjZjZkNTM0ZWI4NTllYmFiNDA4MmE1MTU1ZRJHc2hhMjU2OjFjNWM3ZTJmNWQ1YWZhZDU4NjljZTI5MzI1NWNhZmQ5YjU5M2EzYWE1NTQ2N2M1NGVlYjFmNWMzNzM0ODA4ZDIaEFszLzNdIFJVTiBleGl0IDEqCwjg/7n7BRD8r8Z6MgwI4P+5+wUQtOPw6QE6T2V4ZWN1dG9yIGZhaWxlZCBydW5uaW5nIFsvYmluL3NoIC1jIGV4aXQgMV06IHJ1bmMgZGlkIG5vdCB0ZXJtaW5hdGUgc3VjZXNzZnVsbHk="}
                {"errorDetail":{"message":"failed to solve with frontend dockerfile.v0: failed to build LLB: executor failed running [/bin/sh -c exit 1]: runc did not terminate sucessfully"},"error":"failed to solve with frontend dockerfile.v0: failed to build LLB: executor failed running [/bin/sh -c exit 1]: runc did not terminate sucessfully"}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts build status messages as the build progresses") {
                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(0, "[internal] load remote build context"))),
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(1, "copy /context /"))),
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(2, "[internal] load metadata for docker.io/library/alpine:3.12.0"))),
                            // The FROM step is marked as complete immediately, so we don't need to post a progress event for it.
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(4, "[2/3] RUN echo \"hello world\""))),
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(5, "[3/3] RUN exit 1"))),
                            BuildError("failed to solve with frontend dockerfile.v0: failed to build LLB: executor failed running [/bin/sh -c exit 1]: runc did not terminate sucessfully")
                        )
                    )
                )
            }

            it("streams output showing the progression of the build") {
                assertThat(
                    output.toString(),
                    equalTo(
                        """
                        |#1 [internal] load remote build context
                        |#1 DONE
                        |
                        |#2 copy /context /
                        |#2 DONE
                        |
                        |#3 [internal] load metadata for docker.io/library/alpine:3.12.0
                        |#3 DONE
                        |
                        |#4 [1/3] FROM docker.io/library/alpine:3.12.0
                        |#4 resolve docker.io/library/alpine:3.12.0: done
                        |#4 DONE
                        |
                        |#5 [2/3] RUN echo "hello world"
                        |#5 0.181 hello world
                        |#5 DONE
                        |
                        |#6 [3/3] RUN exit 1
                        |#6 ERROR: executor failed running [/bin/sh -c exit 1]: runc did not terminate sucessfully
                        |
                        |
                        """.trimMargin()
                    )
                )
            }
        }

        given("a response with a trace message containing multi-line output") {
            val input = """
                {"id":"moby.buildkit.trace","aux":"Cs8BCkdzaGEyNTY6MWZjNWU4MWE0YTZiNGNmMzAwNzRhMDA3ZTA3YTY4ZjM0YmQ0MjdhODgyNDllZjNiNzZkZjg3YTRkMzk0MTcwYRJHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzIaLlsyLzJdIFJVTiBzaCAtYyAnZWNobyAiaGVsbG8iICYmIGVjaG8gIndvcmxkIicqCwjun5r8BRDU4bMI"}
                {"id":"moby.buildkit.trace","aux":"GmYKR3NoYTI1NjoxZmM1ZTgxYTRhNmI0Y2YzMDA3NGEwMDdlMDdhNjhmMzRiZDQyN2E4ODI0OWVmM2I3NmRmODdhNGQzOTQxNzBhEgsI7p+a/AUQ1LrxZBgBIgxoZWxsbwp3b3JsZAo="}
                {"id":"moby.buildkit.trace","aux":"CtwBCkdzaGEyNTY6MWZjNWU4MWE0YTZiNGNmMzAwNzRhMDA3ZTA3YTY4ZjM0YmQ0MjdhODgyNDllZjNiNzZkZjg3YTRkMzk0MTcwYRJHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzIaLlsyLzJdIFJVTiBzaCAtYyAnZWNobyAiaGVsbG8iICYmIGVjaG8gIndvcmxkIicqCwjun5r8BRDU4bMIMgsI7p+a/AUQ/K76eA=="}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("prefixes each line of the output") {
                assertThat(
                    output.toString(),
                    equalTo(
                        """
                        |#1 [2/2] RUN sh -c 'echo "hello" && echo "world"'
                        |#1 0.193 hello
                        |#1 0.193 world
                        |#1 DONE
                        |
                        |
                        """.trimMargin()
                    )
                )
            }
        }

        given("a response with a trace message containing output that does not end with a newline character") {
            val input = """
                {"id":"moby.buildkit.trace","aux":"CoECCkdzaGEyNTY6MTlkODAzMDQ5ODA5MzdiOTgzNjA3YTkzNDgwODkzYjdlMDRhZmMxMDJmNDVmYTU0ODYyMmI0MWI0YmE4YTE3ORJHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzIaYFsyLzJdIFJVTiBzaCAtYyAnZWNobyAiaGVsbG8iICYmIGVjaG8gLW4gIndvcmxkIiAmJiBlY2hvICJtb3JlIiAmJiBlY2hvIC1uICJmcm9tIHRoZSBuZXh0IGxpbmUiJyoLCKGkmvwFEKiYhVQ="}
                {"id":"moby.buildkit.trace","aux":"GmYKR3NoYTI1NjoxOWQ4MDMwNDk4MDkzN2I5ODM2MDdhOTM0ODA4OTNiN2UwNGFmYzEwMmY0NWZhNTQ4NjIyYjQxYjRiYThhMTc5EgwIoaSa/AUQzMuaqgEYASILaGVsbG8Kd29ybGQ="}
                {"id":"moby.buildkit.trace","aux":"GnIKR3NoYTI1NjoxOWQ4MDMwNDk4MDkzN2I5ODM2MDdhOTM0ODA4OTNiN2UwNGFmYzEwMmY0NWZhNTQ4NjIyYjQxYjRiYThhMTc5EgwIoaSa/AUQzNSUqwEYASIXbW9yZQpmcm9tIHRoZSBuZXh0IGxpbmU="}
                {"id":"moby.buildkit.trace","aux":"Co8CCkdzaGEyNTY6MTlkODAzMDQ5ODA5MzdiOTgzNjA3YTkzNDgwODkzYjdlMDRhZmMxMDJmNDVmYTU0ODYyMmI0MWI0YmE4YTE3ORJHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzIaYFsyLzJdIFJVTiBzaCAtYyAnZWNobyAiaGVsbG8iICYmIGVjaG8gLW4gIndvcmxkIiAmJiBlY2hvICJtb3JlIiAmJiBlY2hvIC1uICJmcm9tIHRoZSBuZXh0IGxpbmUiJyoLCKGkmvwFEKiYhVQyDAihpJr8BRC4i8vBAQ=="}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("waits to receive any further output for that line before writing it, printing any remaining output before the task is marked as done") {
                assertThat(
                    output.toString(),
                    equalTo(
                        """
                        |#1 [2/2] RUN sh -c 'echo "hello" && echo -n "world" && echo "more" && echo -n "from the next line"'
                        |#1 0.180 hello
                        |#1 0.182 worldmore
                        |#1 0.229 from the next line
                        |#1 DONE
                        |
                        |
                        """.trimMargin()
                    )
                )
            }
        }

        given("a response with a trace message containing output that does not contain a newline character") {
            val input = """
                {"id":"moby.buildkit.trace","aux":"CroBCkdzaGEyNTY6YjNmZjNjZjFhMTM3MGI0NDlkMjUzODQxNDYzMTRjZmQ3YmY4NDY1NDNhODlhNDEyMGJjNDk5MTIxYmNlNTJlNhJHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzIaGVsyLzJdIFJVTiBlY2hvIC1uICJoZWxsbyIqCwi0qrn8BRCAyaV8"}
                {"id":"moby.buildkit.trace","aux":"GmAKR3NoYTI1NjpiM2ZmM2NmMWExMzcwYjQ0OWQyNTM4NDE0NjMxNGNmZDdiZjg0NjU0M2E4OWE0MTIwYmM0OTkxMjFiY2U1MmU2EgwItKq5/AUQ2OaY9QEYASIFaGVsbG8="}
                {"id":"moby.buildkit.trace","aux":"CsgBCkdzaGEyNTY6YjNmZjNjZjFhMTM3MGI0NDlkMjUzODQxNDYzMTRjZmQ3YmY4NDY1NDNhODlhNDEyMGJjNDk5MTIxYmNlNTJlNhJHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzIaGVsyLzJdIFJVTiBlY2hvIC1uICJoZWxsbyIqCwi0qrn8BRCAyaV8MgwItKq5/AUQgJX5kgI="}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("prints the output before reporting that the task is done") {
                assertThat(
                    output.toString(),
                    equalTo(
                        """
                        |#1 [2/2] RUN echo -n "hello"
                        |#1 0.315 hello
                        |#1 DONE
                        |
                        |
                        """.trimMargin()
                    )
                )
            }
        }

        given("a response with a trace message containing output that does not end with a newline character that culminates in an error") {
            val input = """
                {"id":"moby.buildkit.trace","aux":"CoECCkdzaGEyNTY6MTlkODAzMDQ5ODA5MzdiOTgzNjA3YTkzNDgwODkzYjdlMDRhZmMxMDJmNDVmYTU0ODYyMmI0MWI0YmE4YTE3ORJHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzIaYFsyLzJdIFJVTiBzaCAtYyAnZWNobyAiaGVsbG8iICYmIGVjaG8gLW4gIndvcmxkIiAmJiBlY2hvICJtb3JlIiAmJiBlY2hvIC1uICJmcm9tIHRoZSBuZXh0IGxpbmUiJyoLCKGkmvwFEKiYhVQ="}
                {"id":"moby.buildkit.trace","aux":"GmYKR3NoYTI1NjoxOWQ4MDMwNDk4MDkzN2I5ODM2MDdhOTM0ODA4OTNiN2UwNGFmYzEwMmY0NWZhNTQ4NjIyYjQxYjRiYThhMTc5EgwIoaSa/AUQzMuaqgEYASILaGVsbG8Kd29ybGQ="}
                {"id":"moby.buildkit.trace","aux":"GnIKR3NoYTI1NjoxOWQ4MDMwNDk4MDkzN2I5ODM2MDdhOTM0ODA4OTNiN2UwNGFmYzEwMmY0NWZhNTQ4NjIyYjQxYjRiYThhMTc5EgwIoaSa/AUQzNSUqwEYASIXbW9yZQpmcm9tIHRoZSBuZXh0IGxpbmU="}
                {"id":"moby.buildkit.trace","aux":"CqUCCkdzaGEyNTY6MTlkODAzMDQ5ODA5MzdiOTgzNjA3YTkzNDgwODkzYjdlMDRhZmMxMDJmNDVmYTU0ODYyMmI0MWI0YmE4YTE3ORJHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzIaYFsyLzJdIFJVTiBzaCAtYyAnZWNobyAiaGVsbG8iICYmIGVjaG8gLW4gIndvcmxkIiAmJiBlY2hvICJtb3JlIiAmJiBlY2hvIC1uICJmcm9tIHRoZSBuZXh0IGxpbmUiJyoLCKGkmvwFEKiYhVQyDAihpJr8BRC4i8vBAToUU29tZXRoaW5nIHdlbnQgd3Jvbmc="}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("waits to receive any further output for that line before writing it, printing any remaining output before the task is marked as done") {
                assertThat(
                    output.toString(),
                    equalTo(
                        """
                        |#1 [2/2] RUN sh -c 'echo "hello" && echo -n "world" && echo "more" && echo -n "from the next line"'
                        |#1 0.180 hello
                        |#1 0.182 worldmore
                        |#1 0.229 from the next line
                        |#1 ERROR: Something went wrong
                        |
                        |
                        """.trimMargin()
                    )
                )
            }
        }

        given("a response with trace messages containing output from two simultaneously executing build steps") {
            val input = """
                {"id":"moby.buildkit.trace","aux":"CokBCkdzaGEyNTY6ZmRhZDY3MWE5MTJiMmQwMTAzMzNhZjMwZDA1MDM2N2Q3ZjBjNjdmNmJkNDhlNDNiYzNhMmVlZDc4OTE0N2QzMhowW2ZpcnN0IDEvMl0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4wKgwI3Kqa/AUQ7PKLuQI="}
                {"id":"moby.buildkit.trace","aux":"CpcBCkdzaGEyNTY6ZmRhZDY3MWE5MTJiMmQwMTAzMzNhZjMwZDA1MDM2N2Q3ZjBjNjdmNmJkNDhlNDNiYzNhMmVlZDc4OTE0N2QzMhowW2ZpcnN0IDEvMl0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4wKgwI3Kqa/AUQ7PKLuQIyDAjcqpr8BRDA4KG5Ag=="}
                {"id":"moby.buildkit.trace","aux":"CpkBCkdzaGEyNTY6ZmRhZDY3MWE5MTJiMmQwMTAzMzNhZjMwZDA1MDM2N2Q3ZjBjNjdmNmJkNDhlNDNiYzNhMmVlZDc4OTE0N2QzMhowW2ZpcnN0IDEvMl0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4wIAEqDAjcqpr8BRDg4Ly5AjIMCNyqmvwFEJDDybkCCvYBCkdzaGEyNTY6MTA0YTM5NDcyMjQyNThmOGUyYjk4NGFlMzczNGM5YzgzYmNjOTk0Nzg2ODMyNWRmYzYzNjFlZTM5NWMwYmM2NhJHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzIaVFtmaXJzdCAyLzJdIFJVTiBzaCAtYyAnZm9yIGkgaW4gMSAyIDMgNCA1OyBkbyBlY2hvICJGaXJzdCBzdGFnZTogJGkiOyBzbGVlcCAzOyBkb25lJyoMCNyqmvwFELDH0rkC"}
                {"id":"moby.buildkit.trace","aux":"CvwBCkdzaGEyNTY6ZGViYWJlMTQzNDk5NTM3NDBlOWFkOGJmMjg5ODNhMDk1Mjk0MGFiZmIyNDg5YWNhNmI1YjNiMjU2NTQxZTM4ZRJHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzIaWltzZWNvbmQgMi8zXSBSVU4gc2ggLWMgJ2ZvciBpIGluIDEgMiAzIDQgNSA2IDc7IGRvIGVjaG8gIlNlY29uZCBzdGFnZTogJGkiOyBzbGVlcCAyOyBkb25lJyoMCNyqmvwFENiI3bkC"}
                {"id":"moby.buildkit.trace","aux":"GmsKR3NoYTI1NjpkZWJhYmUxNDM0OTk1Mzc0MGU5YWQ4YmYyODk4M2EwOTUyOTQwYWJmYjI0ODlhY2E2YjViM2IyNTY1NDFlMzhlEgwI3Kqa/AUQgMnqqgMYASIQU2Vjb25kIHN0YWdlOiAxCg=="}
                {"id":"moby.buildkit.trace","aux":"GmoKR3NoYTI1NjoxMDRhMzk0NzIyNDI1OGY4ZTJiOTg0YWUzNzM0YzljODNiY2M5OTQ3ODY4MzI1ZGZjNjM2MWVlMzk1YzBiYzY2EgwI3Kqa/AUQlLOxuwMYASIPRmlyc3Qgc3RhZ2U6IDEK"}
                {"id":"moby.buildkit.trace","aux":"GmsKR3NoYTI1NjpkZWJhYmUxNDM0OTk1Mzc0MGU5YWQ4YmYyODk4M2EwOTUyOTQwYWJmYjI0ODlhY2E2YjViM2IyNTY1NDFlMzhlEgwI3qqa/AUQ/ObHrAMYASIQU2Vjb25kIHN0YWdlOiAyCg=="}
                {"id":"moby.buildkit.trace","aux":"GmoKR3NoYTI1NjoxMDRhMzk0NzIyNDI1OGY4ZTJiOTg0YWUzNzM0YzljODNiY2M5OTQ3ODY4MzI1ZGZjNjM2MWVlMzk1YzBiYzY2EgwI36qa/AUQ5IrJvAMYASIPRmlyc3Qgc3RhZ2U6IDIK"}
                {"id":"moby.buildkit.trace","aux":"GmsKR3NoYTI1NjpkZWJhYmUxNDM0OTk1Mzc0MGU5YWQ4YmYyODk4M2EwOTUyOTQwYWJmYjI0ODlhY2E2YjViM2IyNTY1NDFlMzhlEgwI4Kqa/AUQxMuCsAMYASIQU2Vjb25kIHN0YWdlOiAzCg=="}
                {"id":"moby.buildkit.trace","aux":"GmsKR3NoYTI1NjpkZWJhYmUxNDM0OTk1Mzc0MGU5YWQ4YmYyODk4M2EwOTUyOTQwYWJmYjI0ODlhY2E2YjViM2IyNTY1NDFlMzhlEgwI4qqa/AUQnOe6sQMYASIQU2Vjb25kIHN0YWdlOiA0Cg=="}
                {"id":"moby.buildkit.trace","aux":"CooCCkdzaGEyNTY6ZGViYWJlMTQzNDk5NTM3NDBlOWFkOGJmMjg5ODNhMDk1Mjk0MGFiZmIyNDg5YWNhNmI1YjNiMjU2NTQxZTM4ZRJHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzIaWltzZWNvbmQgMi8zXSBSVU4gc2ggLWMgJ2ZvciBpIGluIDEgMiAzIDQgNSA2IDc7IGRvIGVjaG8gIlNlY29uZCBzdGFnZTogJGkiOyBzbGVlcCAyOyBkb25lJyoMCNyqmvwFENiI3bkCMgwI6qqa/AUQ2NmN0AM="}
                {"id":"moby.buildkit.trace","aux":"CoQCCkdzaGEyNTY6MTA0YTM5NDcyMjQyNThmOGUyYjk4NGFlMzczNGM5YzgzYmNjOTk0Nzg2ODMyNWRmYzYzNjFlZTM5NWMwYmM2NhJHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzIaVFtmaXJzdCAyLzJdIFJVTiBzaCAtYyAnZm9yIGkgaW4gMSAyIDMgNCA1OyBkbyBlY2hvICJGaXJzdCBzdGFnZTogJGkiOyBzbGVlcCAzOyBkb25lJyoMCNyqmvwFELDH0rkCMgwI66qa/AUQlNnO1AM="}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts build status messages as the build progresses") {
                val firstStep = ActiveImageBuildStep.NotDownloading(1, "[first 2/2] RUN sh -c 'for i in 1 2 3 4 5; do echo \"First stage: \$i\"; sleep 3; done'")
                val secondStep = ActiveImageBuildStep.NotDownloading(2, "[second 2/3] RUN sh -c 'for i in 1 2 3 4 5 6 7; do echo \"Second stage: \$i\"; sleep 2; done'")

                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(0, "[first 1/2] FROM docker.io/library/alpine:3.12.0"))),
                            BuildProgress(setOf(firstStep)),
                            BuildProgress(setOf(firstStep, secondStep)),
                            BuildProgress(setOf(firstStep))
                        )
                    )
                )
            }

            it("correctly prefixes each line of the output, labelling and ending each block of output") {
                assertThat(
                    output.toString(),
                    equalTo(
                        """
                        |#1 [first 1/2] FROM docker.io/library/alpine:3.12.0
                        |#1 CACHED
                        |
                        |#2 [first 2/2] RUN sh -c 'for i in 1 2 3 4 5; do echo "First stage: ${'$'}i"; sleep 3; done'
                        |#2 ...
                        |
                        |#3 [second 2/3] RUN sh -c 'for i in 1 2 3 4 5 6 7; do echo "Second stage: ${'$'}i"; sleep 2; done'
                        |#3 0.237 Second stage: 1
                        |#3 ...
                        |
                        |#2 [first 2/2] RUN sh -c 'for i in 1 2 3 4 5; do echo "First stage: ${'$'}i"; sleep 3; done'
                        |#2 0.272 First stage: 1
                        |#2 ...
                        |
                        |#3 [second 2/3] RUN sh -c 'for i in 1 2 3 4 5 6 7; do echo "Second stage: ${'$'}i"; sleep 2; done'
                        |#3 2.240 Second stage: 2
                        |#3 ...
                        |
                        |#2 [first 2/2] RUN sh -c 'for i in 1 2 3 4 5; do echo "First stage: ${'$'}i"; sleep 3; done'
                        |#2 3.274 First stage: 2
                        |#2 ...
                        |
                        |#3 [second 2/3] RUN sh -c 'for i in 1 2 3 4 5 6 7; do echo "Second stage: ${'$'}i"; sleep 2; done'
                        |#3 4.248 Second stage: 3
                        |#3 6.251 Second stage: 4
                        |#3 DONE
                        |
                        |#2 [first 2/2] RUN sh -c 'for i in 1 2 3 4 5; do echo "First stage: ${'$'}i"; sleep 3; done'
                        |#2 DONE
                        |
                        |
                        """.trimMargin()
                    )
                )
            }
        }

        given("a response with trace messages for a file download") {
            val input = """
                {"id":"moby.buildkit.trace","aux":"CnUKR3NoYTI1NjpmZGFkNjcxYTkxMmIyZDAxMDMzM2FmMzBkMDUwMzY3ZDdmMGM2N2Y2YmQ0OGU0M2JjM2EyZWVkNzg5MTQ3ZDMyGipbMS8yXSBGUk9NIGRvY2tlci5pby9saWJyYXJ5L2FscGluZTozLjEyLjAKlgEKR3NoYTI1Njo5M2JkMTZkOWFkN2Y2MjcxZGFkZmE0MjE1YTIwZWJkMzlmNjE0YzU2NTM3NTdmMDZkMDE5NWY1N2JlZWFmM2NiGktodHRwczovL2dpdGh1Yi5jb20vYmF0ZWN0L2JhdGVjdC9yZWxlYXNlcy9kb3dubG9hZC8wLjU5LjAvYmF0ZWN0LTAuNTkuMC5qYXIKvQIKR3NoYTI1NjozNTA2OTc5NDM3ZDU5ZjFjNzNlNTM2MGExY2EzZjU4YmI0ODBmNTJjMTRlM2IwNmIzY2YwMWQ2YzMzOTE4MzVmEkdzaGEyNTY6ZmRhZDY3MWE5MTJiMmQwMTAzMzNhZjMwZDA1MDM2N2Q3ZjBjNjdmNmJkNDhlNDNiYzNhMmVlZDc4OTE0N2QzMhJHc2hhMjU2OjkzYmQxNmQ5YWQ3ZjYyNzFkYWRmYTQyMTVhMjBlYmQzOWY2MTRjNTY1Mzc1N2YwNmQwMTk1ZjU3YmVlYWYzY2IaYFsyLzJdIEFERCBodHRwczovL2dpdGh1Yi5jb20vYmF0ZWN0L2JhdGVjdC9yZWxlYXNlcy9kb3dubG9hZC8wLjU5LjAvYmF0ZWN0LTAuNTkuMC5qYXIgYmF0ZWN0Lmphcg=="}
                {"id":"moby.buildkit.trace","aux":"CqMBCkdzaGEyNTY6OTNiZDE2ZDlhZDdmNjI3MWRhZGZhNDIxNWEyMGViZDM5ZjYxNGM1NjUzNzU3ZjA2ZDAxOTVmNTdiZWVhZjNjYhpLaHR0cHM6Ly9naXRodWIuY29tL2JhdGVjdC9iYXRlY3QvcmVsZWFzZXMvZG93bmxvYWQvMC41OS4wL2JhdGVjdC0wLjU5LjAuamFyKgsI1+ud/AUQzJ+FYAqCAQpHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzIaKlsxLzJdIEZST00gZG9ja2VyLmlvL2xpYnJhcnkvYWxwaW5lOjMuMTIuMCoLCNfrnfwFEI7tjGA="}
                {"id":"moby.buildkit.trace","aux":"Co8BCkdzaGEyNTY6ZmRhZDY3MWE5MTJiMmQwMTAzMzNhZjMwZDA1MDM2N2Q3ZjBjNjdmNmJkNDhlNDNiYzNhMmVlZDc4OTE0N2QzMhoqWzEvMl0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4wKgsI1+ud/AUQju2MYDILCNfrnfwFELDYomA="}
                {"id":"moby.buildkit.trace","aux":"CpEBCkdzaGEyNTY6ZmRhZDY3MWE5MTJiMmQwMTAzMzNhZjMwZDA1MDM2N2Q3ZjBjNjdmNmJkNDhlNDNiYzNhMmVlZDc4OTE0N2QzMhoqWzEvMl0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4wIAEqCwjX6538BRC5la9gMgsI1+ud/AUQv6qwYA=="}
                {"id":"moby.buildkit.trace","aux":"CrEBCkdzaGEyNTY6OTNiZDE2ZDlhZDdmNjI3MWRhZGZhNDIxNWEyMGViZDM5ZjYxNGM1NjUzNzU3ZjA2ZDAxOTVmNTdiZWVhZjNjYhpLaHR0cHM6Ly9naXRodWIuY29tL2JhdGVjdC9iYXRlY3QvcmVsZWFzZXMvZG93bmxvYWQvMC41OS4wL2JhdGVjdC0wLjU5LjAuamFyKgsI1+ud/AUQzJ+FYDIMCNrrnfwFEMWy0+8B"}
                {"id":"moby.buildkit.trace","aux":"CrIBCkdzaGEyNTY6OTNiZDE2ZDlhZDdmNjI3MWRhZGZhNDIxNWEyMGViZDM5ZjYxNGM1NjUzNzU3ZjA2ZDAxOTVmNTdiZWVhZjNjYhpLaHR0cHM6Ly9naXRodWIuY29tL2JhdGVjdC9iYXRlY3QvcmVsZWFzZXMvZG93bmxvYWQvMC41OS4wL2JhdGVjdC0wLjU5LjAuamFyKgwI2uud/AUQ4tby7wEyDAja6538BRD7kfPvAQ=="}
                {"id":"moby.buildkit.trace","aux":"CssCCkdzaGEyNTY6MzUwNjk3OTQzN2Q1OWYxYzczZTUzNjBhMWNhM2Y1OGJiNDgwZjUyYzE0ZTNiMDZiM2NmMDFkNmMzMzkxODM1ZhJHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzISR3NoYTI1Njo5M2JkMTZkOWFkN2Y2MjcxZGFkZmE0MjE1YTIwZWJkMzlmNjE0YzU2NTM3NTdmMDZkMDE5NWY1N2JlZWFmM2NiGmBbMi8yXSBBREQgaHR0cHM6Ly9naXRodWIuY29tL2JhdGVjdC9iYXRlY3QvcmVsZWFzZXMvZG93bmxvYWQvMC41OS4wL2JhdGVjdC0wLjU5LjAuamFyIGJhdGVjdC5qYXIqDAja6538BRCwj9iLAg=="}
                {"id":"moby.buildkit.trace","aux":"CtkCCkdzaGEyNTY6MzUwNjk3OTQzN2Q1OWYxYzczZTUzNjBhMWNhM2Y1OGJiNDgwZjUyYzE0ZTNiMDZiM2NmMDFkNmMzMzkxODM1ZhJHc2hhMjU2OmZkYWQ2NzFhOTEyYjJkMDEwMzMzYWYzMGQwNTAzNjdkN2YwYzY3ZjZiZDQ4ZTQzYmMzYTJlZWQ3ODkxNDdkMzISR3NoYTI1Njo5M2JkMTZkOWFkN2Y2MjcxZGFkZmE0MjE1YTIwZWJkMzlmNjE0YzU2NTM3NTdmMDZkMDE5NWY1N2JlZWFmM2NiGmBbMi8yXSBBREQgaHR0cHM6Ly9naXRodWIuY29tL2JhdGVjdC9iYXRlY3QvcmVsZWFzZXMvZG93bmxvYWQvMC41OS4wL2JhdGVjdC0wLjU5LjAuamFyIGJhdGVjdC5qYXIqDAja6538BRCwj9iLAjIMCNrrnfwFEMGUpbgC"}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts build status messages as the build progresses") {
                val imagePullStep = ActiveImageBuildStep.NotDownloading(1, "[1/2] FROM docker.io/library/alpine:3.12.0")
                val download = ActiveImageBuildStep.NotDownloading(0, "https://github.com/batect/batect/releases/download/0.59.0/batect-0.59.0.jar")
                val addStep = ActiveImageBuildStep.NotDownloading(2, "[2/2] ADD https://github.com/batect/batect/releases/download/0.59.0/batect-0.59.0.jar batect.jar")

                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            BuildProgress(setOf(imagePullStep, download)),
                            BuildProgress(setOf(download)),
                            BuildProgress(setOf(addStep))
                        )
                    )
                )
            }

            it("streams output showing the progression of the build") {
                assertThat(
                    output.toString(),
                    equalTo(
                        """
                        |#1 https://github.com/batect/batect/releases/download/0.59.0/batect-0.59.0.jar
                        |#1 ...
                        |
                        |#2 [1/2] FROM docker.io/library/alpine:3.12.0
                        |#2 CACHED
                        |
                        |#1 https://github.com/batect/batect/releases/download/0.59.0/batect-0.59.0.jar
                        |#1 DONE
                        |
                        |#3 [2/2] ADD https://github.com/batect/batect/releases/download/0.59.0/batect-0.59.0.jar batect.jar
                        |#3 DONE
                        |
                        |
                        """.trimMargin()
                    )
                )
            }
        }

        given("a response with trace messages for a single image pull with a single layer to download") {
            val input = """
                {"id":"moby.buildkit.trace","aux":"CpUBCkdzaGEyNTY6ZDU4ZWUyMjFmOWRiYWNiOWU3M2ZmOGY1YmQ0NjliNmYzZTI5Nzk4NmUzZDBjODYzYjZlNWI1NTIzODgyNTkzYho8W2ludGVybmFsXSBsb2FkIG1ldGFkYXRhIGZvciBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMC4wKgwIl/Cd/AUQg7mjkQM="}
                {"id":"moby.buildkit.trace","aux":"CqMBCkdzaGEyNTY6ZDU4ZWUyMjFmOWRiYWNiOWU3M2ZmOGY1YmQ0NjliNmYzZTI5Nzk4NmUzZDBjODYzYjZlNWI1NTIzODgyNTkzYho8W2ludGVybmFsXSBsb2FkIG1ldGFkYXRhIGZvciBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMC4wKgwIl/Cd/AUQg7mjkQMyDAic8J38BRCcmPz5Ag=="}
                {"id":"moby.buildkit.trace","aux":"Cr0BCkdzaGEyNTY6Yzc4ZjkyOGNjODhjZWU1MmFhODc4Mzg4MTc4YjViYTY0YzgwMTkwNTZlZGM0MDNhZDI3NWJmNGRlZWNkMjM3YxpyWzEvMV0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMC4wQHNoYTI1NjpjYTFjOTQ0YTRmODQ4NmExNTMwMjRkOTk2NWFhZmJlMjRmNTcyM2MxZDVjMDJmNDk2NGMwNDVhMTZkMTlkYzU0"}
                {"id":"moby.buildkit.trace","aux":"CssBCkdzaGEyNTY6Yzc4ZjkyOGNjODhjZWU1MmFhODc4Mzg4MTc4YjViYTY0YzgwMTkwNTZlZGM0MDNhZDI3NWJmNGRlZWNkMjM3YxpyWzEvMV0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMC4wQHNoYTI1NjpjYTFjOTQ0YTRmODQ4NmExNTMwMjRkOTk2NWFhZmJlMjRmNTcyM2MxZDVjMDJmNDk2NGMwNDVhMTZkMTlkYzU0KgwInPCd/AUQ4/6Q+gI="}
                {"id":"moby.buildkit.trace","aux":"CtkBCkdzaGEyNTY6Yzc4ZjkyOGNjODhjZWU1MmFhODc4Mzg4MTc4YjViYTY0YzgwMTkwNTZlZGM0MDNhZDI3NWJmNGRlZWNkMjM3YxpyWzEvMV0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMC4wQHNoYTI1NjpjYTFjOTQ0YTRmODQ4NmExNTMwMjRkOTk2NWFhZmJlMjRmNTcyM2MxZDVjMDJmNDk2NGMwNDVhMTZkMTlkYzU0KgwInPCd/AUQ4/6Q+gIyDAic8J38BRD5xLT6Ag=="}
                {"id":"moby.buildkit.trace","aux":"CssBCkdzaGEyNTY6Yzc4ZjkyOGNjODhjZWU1MmFhODc4Mzg4MTc4YjViYTY0YzgwMTkwNTZlZGM0MDNhZDI3NWJmNGRlZWNkMjM3YxpyWzEvMV0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMC4wQHNoYTI1NjpjYTFjOTQ0YTRmODQ4NmExNTMwMjRkOTk2NWFhZmJlMjRmNTcyM2MxZDVjMDJmNDk2NGMwNDVhMTZkMTlkYzU0KgwInPCd/AUQ5t+6+gIS1gEKb3Jlc29sdmUgZG9ja2VyLmlvL2xpYnJhcnkvYWxwaW5lOjMuMTAuMEBzaGEyNTY6Y2ExYzk0NGE0Zjg0ODZhMTUzMDI0ZDk5NjVhYWZiZTI0ZjU3MjNjMWQ1YzAyZjQ5NjRjMDQ1YTE2ZDE5ZGM1NBJHc2hhMjU2OmM3OGY5MjhjYzg4Y2VlNTJhYTg3ODM4ODE3OGI1YmE2NGM4MDE5MDU2ZWRjNDAzYWQyNzViZjRkZWVjZDIzN2MyDAic8J38BRDEiLv6AjoMCJzwnfwFEOWFu/oC"}
                {"id":"moby.buildkit.trace","aux":"CtkBCkdzaGEyNTY6Yzc4ZjkyOGNjODhjZWU1MmFhODc4Mzg4MTc4YjViYTY0YzgwMTkwNTZlZGM0MDNhZDI3NWJmNGRlZWNkMjM3YxpyWzEvMV0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMC4wQHNoYTI1NjpjYTFjOTQ0YTRmODQ4NmExNTMwMjRkOTk2NWFhZmJlMjRmNTcyM2MxZDVjMDJmNDk2NGMwNDVhMTZkMTlkYzU0KgwInPCd/AUQ5t+6+gIyDAic8J38BRDZsZz7AhLkAQpvcmVzb2x2ZSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMC4wQHNoYTI1NjpjYTFjOTQ0YTRmODQ4NmExNTMwMjRkOTk2NWFhZmJlMjRmNTcyM2MxZDVjMDJmNDk2NGMwNDVhMTZkMTlkYzU0EkdzaGEyNTY6Yzc4ZjkyOGNjODhjZWU1MmFhODc4Mzg4MTc4YjViYTY0YzgwMTkwNTZlZGM0MDNhZDI3NWJmNGRlZWNkMjM3YzIMCJzwnfwFEKjgkvsCOgwInPCd/AUQ5YW7+gJCDAic8J38BRDG1pL7Ag=="}
                {"id":"moby.buildkit.trace","aux":"CssBCkdzaGEyNTY6Yzc4ZjkyOGNjODhjZWU1MmFhODc4Mzg4MTc4YjViYTY0YzgwMTkwNTZlZGM0MDNhZDI3NWJmNGRlZWNkMjM3YxpyWzEvMV0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMC4wQHNoYTI1NjpjYTFjOTQ0YTRmODQ4NmExNTMwMjRkOTk2NWFhZmJlMjRmNTcyM2MxZDVjMDJmNDk2NGMwNDVhMTZkMTlkYzU0KgwInPCd/AUQts+p+wI="}
                {"id":"moby.buildkit.trace","aux":"EscBCkdzaGEyNTY6Y2ExYzk0NGE0Zjg0ODZhMTUzMDI0ZDk5NjVhYWZiZTI0ZjU3MjNjMWQ1YzAyZjQ5NjRjMDQ1YTE2ZDE5ZGM1NBJHc2hhMjU2OmM3OGY5MjhjYzg4Y2VlNTJhYTg3ODM4ODE3OGI1YmE2NGM4MDE5MDU2ZWRjNDAzYWQyNzViZjRkZWVjZDIzN2MaBGRvbmUg5gwo5gwyDAid8J38BRCMtqy9AToMCJ3wnfwFEKnTx5QBQgsIm/Cd/AUQ98G3dRLIAQpHc2hhMjU2Ojk3YTA0MmJmMDlmMWJmNzhjOGNmM2RjZWJlZjk0NjE0ZjJiOTVmYTJmOTg4YTVjMDczMTQwMzFiYzI1NzBjN2ESR3NoYTI1NjpjNzhmOTI4Y2M4OGNlZTUyYWE4NzgzODgxNzhiNWJhNjRjODAxOTA1NmVkYzQwM2FkMjc1YmY0ZGVlY2QyMzdjGgRkb25lIJAEKJAEMgwInfCd/AUQ3Ze7vQE6DAid8J38BRDFwNeUAUIMCJvwnfwFEN/f/Z4CEsgBCkdzaGEyNTY6NGQ5MDU0MmYwNjIzYzcxZjFmOWMxMWJlM2RhMjMxNjcxNzRhYzlkOTM3MzFjZjkxOTEyOTIyZTkxNmJhYjAyYxJHc2hhMjU2OmM3OGY5MjhjYzg4Y2VlNTJhYTg3ODM4ODE3OGI1YmE2NGM4MDE5MDU2ZWRjNDAzYWQyNzViZjRkZWVjZDIzN2MaBGRvbmUg6Aso6AsyDAid8J38BRDth7y9AToMCJ3wnfwFEMLw/5QBQgwInPCd/AUQp7TZ+QISwAEKR3NoYTI1Njo5MjFiMzFhYjc3MmIzODE3MmZkOWY5NDJhNDBmYWU2ZGIyNGRlY2JkNjcwNmY2NzgzNjI2MGQ0N2E3MmJhYWI1EkdzaGEyNTY6Yzc4ZjkyOGNjODhjZWU1MmFhODc4Mzg4MTc4YjViYTY0YzgwMTkwNTZlZGM0MDNhZDI3NWJmNGRlZWNkMjM3YxoLZG93bmxvYWRpbmco1aGqATIMCJ3wnfwFEIihvL0BOgwInfCd/AUQjI+wlQE="}
                {"id":"moby.buildkit.trace","aux":"EsABCkdzaGEyNTY6OTIxYjMxYWI3NzJiMzgxNzJmZDlmOTQyYTQwZmFlNmRiMjRkZWNiZDY3MDZmNjc4MzYyNjBkNDdhNzJiYWFiNRJHc2hhMjU2OmM3OGY5MjhjYzg4Y2VlNTJhYTg3ODM4ODE3OGI1YmE2NGM4MDE5MDU2ZWRjNDAzYWQyNzViZjRkZWVjZDIzN2MaC2Rvd25sb2FkaW5nKNWhqgEyDAid8J38BRCNxP/sAToMCJ3wnfwFEIyPsJUB"}
                {"id":"moby.buildkit.trace","aux":"EsABCkdzaGEyNTY6OTIxYjMxYWI3NzJiMzgxNzJmZDlmOTQyYTQwZmFlNmRiMjRkZWNiZDY3MDZmNjc4MzYyNjBkNDdhNzJiYWFiNRJHc2hhMjU2OmM3OGY5MjhjYzg4Y2VlNTJhYTg3ODM4ODE3OGI1YmE2NGM4MDE5MDU2ZWRjNDAzYWQyNzViZjRkZWVjZDIzN2MaC2Rvd25sb2FkaW5nKNWhqgEyDAid8J38BRDswK6dAjoMCJ3wnfwFEIyPsJUB"}
                {"id":"moby.buildkit.trace","aux":"EsABCkdzaGEyNTY6OTIxYjMxYWI3NzJiMzgxNzJmZDlmOTQyYTQwZmFlNmRiMjRkZWNiZDY3MDZmNjc4MzYyNjBkNDdhNzJiYWFiNRJHc2hhMjU2OmM3OGY5MjhjYzg4Y2VlNTJhYTg3ODM4ODE3OGI1YmE2NGM4MDE5MDU2ZWRjNDAzYWQyNzViZjRkZWVjZDIzN2MaC2Rvd25sb2FkaW5nKNWhqgEyDAid8J38BRD07pnMAjoMCJ3wnfwFEIyPsJUB"}
                {"id":"moby.buildkit.trace","aux":"EsQBCkdzaGEyNTY6OTIxYjMxYWI3NzJiMzgxNzJmZDlmOTQyYTQwZmFlNmRiMjRkZWNiZDY3MDZmNjc4MzYyNjBkNDdhNzJiYWFiNRJHc2hhMjU2OmM3OGY5MjhjYzg4Y2VlNTJhYTg3ODM4ODE3OGI1YmE2NGM4MDE5MDU2ZWRjNDAzYWQyNzViZjRkZWVjZDIzN2MaC2Rvd25sb2FkaW5nIMT3RyjVoaoBMgwInfCd/AUQxJfa+wI6DAid8J38BRCMj7CVAQ=="}
                {"id":"moby.buildkit.trace","aux":"EsUBCkdzaGEyNTY6OTIxYjMxYWI3NzJiMzgxNzJmZDlmOTQyYTQwZmFlNmRiMjRkZWNiZDY3MDZmNjc4MzYyNjBkNDdhNzJiYWFiNRJHc2hhMjU2OmM3OGY5MjhjYzg4Y2VlNTJhYTg3ODM4ODE3OGI1YmE2NGM4MDE5MDU2ZWRjNDAzYWQyNzViZjRkZWVjZDIzN2MaC2Rvd25sb2FkaW5nILDzlQEo1aGqATIMCJ3wnfwFEOPfr6sDOgwInfCd/AUQjI+wlQE="}
                {"id":"moby.buildkit.trace","aux":"EsIBClJleHRyYWN0aW5nIHNoYTI1Njo5MjFiMzFhYjc3MmIzODE3MmZkOWY5NDJhNDBmYWU2ZGIyNGRlY2JkNjcwNmY2NzgzNjI2MGQ0N2E3MmJhYWI1EkdzaGEyNTY6Yzc4ZjkyOGNjODhjZWU1MmFhODc4Mzg4MTc4YjViYTY0YzgwMTkwNTZlZGM0MDNhZDI3NWJmNGRlZWNkMjM3YxoHZXh0cmFjdDIMCJ3wnfwFEO6cys0DOgwInfCd/AUQ9YLKzQM="}
                {"id":"moby.buildkit.trace","aux":"EswBCkdzaGEyNTY6OTIxYjMxYWI3NzJiMzgxNzJmZDlmOTQyYTQwZmFlNmRiMjRkZWNiZDY3MDZmNjc4MzYyNjBkNDdhNzJiYWFiNRJHc2hhMjU2OmM3OGY5MjhjYzg4Y2VlNTJhYTg3ODM4ODE3OGI1YmE2NGM4MDE5MDU2ZWRjNDAzYWQyNzViZjRkZWVjZDIzN2MaBGRvbmUg1aGqASjVoaoBMgwInfCd/AUQ2qz72gM6DAid8J38BRCMj7CVAUIMCJ3wnfwFEPWGqM0D"}
                {"id":"moby.buildkit.trace","aux":"EsEBClJleHRyYWN0aW5nIHNoYTI1Njo5MjFiMzFhYjc3MmIzODE3MmZkOWY5NDJhNDBmYWU2ZGIyNGRlY2JkNjcwNmY2NzgzNjI2MGQ0N2E3MmJhYWI1EkdzaGEyNTY6Yzc4ZjkyOGNjODhjZWU1MmFhODc4Mzg4MTc4YjViYTY0YzgwMTkwNTZlZGM0MDNhZDI3NWJmNGRlZWNkMjM3YxoHZXh0cmFjdDILCJ7wnfwFEJ+F1SA6DAid8J38BRD1gsrNAw=="}
                {"id":"moby.buildkit.trace","aux":"Es4BClJleHRyYWN0aW5nIHNoYTI1Njo5MjFiMzFhYjc3MmIzODE3MmZkOWY5NDJhNDBmYWU2ZGIyNGRlY2JkNjcwNmY2NzgzNjI2MGQ0N2E3MmJhYWI1EkdzaGEyNTY6Yzc4ZjkyOGNjODhjZWU1MmFhODc4Mzg4MTc4YjViYTY0YzgwMTkwNTZlZGM0MDNhZDI3NWJmNGRlZWNkMjM3YxoHZXh0cmFjdDILCJ7wnfwFEICIwSM6DAid8J38BRD1gsrNA0ILCJ7wnfwFENaAwSM="}
                {"id":"moby.buildkit.trace","aux":"CtgBCkdzaGEyNTY6Yzc4ZjkyOGNjODhjZWU1MmFhODc4Mzg4MTc4YjViYTY0YzgwMTkwNTZlZGM0MDNhZDI3NWJmNGRlZWNkMjM3YxpyWzEvMV0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMC4wQHNoYTI1NjpjYTFjOTQ0YTRmODQ4NmExNTMwMjRkOTk2NWFhZmJlMjRmNTcyM2MxZDVjMDJmNDk2NGMwNDVhMTZkMTlkYzU0KgwInPCd/AUQts+p+wIyCwie8J38BRCyp6s0"}
                {"id":"moby.buildkit.trace","aux":"CmoKR3NoYTI1NjplOGM2MTNlMDdiMGI3ZmYzMzg5M2I2OTRmNzc1OWExMGQ0MmUxODBmMmI0ZGMzNDlmYjU3ZGM2YjcxZGNhYjAwGhJleHBvcnRpbmcgdG8gaW1hZ2UqCwie8J38BRCUmeE0EoIBChBleHBvcnRpbmcgbGF5ZXJzEkdzaGEyNTY6ZThjNjEzZTA3YjBiN2ZmMzM4OTNiNjk0Zjc3NTlhMTBkNDJlMTgwZjJiNGRjMzQ5ZmI1N2RjNmI3MWRjYWIwMDILCJ7wnfwFEMG84TQ6Cwie8J38BRC9q+E0QgsInvCd/AUQ0rvhNBK6AQpVd3JpdGluZyBpbWFnZSBzaGEyNTY6NTMzMzBhMDM0MDNkZGY5NmRkMTM2OTJlMGIyOWVmMGRhOGZkMjk4YTJiZDhiNTdjZDUzMDE1MTU0ODg2N2Y4OBJHc2hhMjU2OmU4YzYxM2UwN2IwYjdmZjMzODkzYjY5NGY3NzU5YTEwZDQyZTE4MGYyYjRkYzM0OWZiNTdkYzZiNzFkY2FiMDAyCwie8J38BRCfw+U0OgsInvCd/AUQ68HlNA=="}
                {"id":"moby.buildkit.trace","aux":"EscBClV3cml0aW5nIGltYWdlIHNoYTI1Njo1MzMzMGEwMzQwM2RkZjk2ZGQxMzY5MmUwYjI5ZWYwZGE4ZmQyOThhMmJkOGI1N2NkNTMwMTUxNTQ4ODY3Zjg4EkdzaGEyNTY6ZThjNjEzZTA3YjBiN2ZmMzM4OTNiNjk0Zjc3NTlhMTBkNDJlMTgwZjJiNGRjMzQ5ZmI1N2RjNmI3MWRjYWIwMDILCJ7wnfwFEJDToTU6Cwie8J38BRDrweU0QgsInvCd/AUQ4M+hNRKuAQpJbmFtaW5nIHRvIGRvY2tlci5pby9saWJyYXJ5L2JhdGVjdC1pbnRlZ3JhdGlvbi10ZXN0cy1pbWFnZS1sZWdhY3ktYnVpbGRlchJHc2hhMjU2OmU4YzYxM2UwN2IwYjdmZjMzODkzYjY5NGY3NzU5YTEwZDQyZTE4MGYyYjRkYzM0OWZiNTdkYzZiNzFkY2FiMDAyCwie8J38BRD6naI1OgsInvCd/AUQqZyiNQ=="}
                {"id":"moby.buildkit.trace","aux":"CncKR3NoYTI1NjplOGM2MTNlMDdiMGI3ZmYzMzg5M2I2OTRmNzc1OWExMGQ0MmUxODBmMmI0ZGMzNDlmYjU3ZGM2YjcxZGNhYjAwGhJleHBvcnRpbmcgdG8gaW1hZ2UqCwie8J38BRCUmeE0MgsInvCd/AUQlIbpNRK7AQpJbmFtaW5nIHRvIGRvY2tlci5pby9saWJyYXJ5L2JhdGVjdC1pbnRlZ3JhdGlvbi10ZXN0cy1pbWFnZS1sZWdhY3ktYnVpbGRlchJHc2hhMjU2OmU4YzYxM2UwN2IwYjdmZjMzODkzYjY5NGY3NzU5YTEwZDQyZTE4MGYyYjRkYzM0OWZiNTdkYzZiNzFkY2FiMDAyCwie8J38BRDsweg1OgsInvCd/AUQqZyiNUILCJ7wnfwFEJ2+6DU="}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts build status messages as the build progresses") {
                val pullStepName = "[1/1] FROM docker.io/library/alpine:3.10.0@sha256:ca1c944a4f8486a153024d9965aafbe24f5723c1d5c02f4964c045a16d19dc54"

                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(0, "[internal] load metadata for docker.io/library/alpine:3.10.0"))),
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(1, pullStepName))),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Downloading, 1638 + 528 + 1512, 1638 + 528 + 1512 + 2789589))),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Downloading, 1638 + 528 + 1512 + 1178564, 1638 + 528 + 1512 + 2789589))),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Downloading, 1638 + 528 + 1512 + 2455984, 1638 + 528 + 1512 + 2789589))),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Extracting, 1638 + 528 + 1512, 1638 + 528 + 1512 + 2789589))),
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.PullComplete, 1638 + 528 + 1512 + 2789589, 1638 + 528 + 1512 + 2789589))),
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(2, "exporting to image")))
                        )
                    )
                )
            }

            it("streams output showing the progression of the build") {
                assertThat(
                    output.toString(),
                    equalTo(
                        """
                        |#1 [internal] load metadata for docker.io/library/alpine:3.10.0
                        |#1 DONE
                        |
                        |#2 [1/1] FROM docker.io/library/alpine:3.10.0@sha256:ca1c944a4f8486a153024d9965aafbe24f5723c1d5c02f4964c045a16d19dc54
                        |#2 resolve docker.io/library/alpine:3.10.0@sha256:ca1c944a4f8486a153024d9965aafbe24f5723c1d5c02f4964c045a16d19dc54: done
                        |#2 sha256:ca1c944a4f8486a153024d9965aafbe24f5723c1d5c02f4964c045a16d19dc54: done
                        |#2 sha256:97a042bf09f1bf78c8cf3dcebef94614f2b95fa2f988a5c07314031bc2570c7a: done
                        |#2 sha256:4d90542f0623c71f1f9c11be3da23167174ac9d93731cf91912922e916bab02c: done
                        |#2 sha256:921b31ab772b38172fd9f942a40fae6db24decbd6706f67836260d47a72baab5: downloading 2.8 MB
                        |#2 sha256:921b31ab772b38172fd9f942a40fae6db24decbd6706f67836260d47a72baab5: extracting
                        |#2 sha256:921b31ab772b38172fd9f942a40fae6db24decbd6706f67836260d47a72baab5: done
                        |#2 DONE
                        |
                        |#3 exporting to image
                        |#3 exporting layers: done
                        |#3 writing image sha256:53330a03403ddf96dd13692e0b29ef0da8fd298a2bd8b57cd530151548867f88: done
                        |#3 naming to docker.io/library/batect-integration-tests-image-legacy-builder: done
                        |#3 DONE
                        |
                        |
                        """.trimMargin()
                    )
                )
            }
        }

        given("a response with trace messages for a single image pull with multiple layers to download") {
            val input = """
                {"id":"moby.buildkit.trace","aux":"CtMBCkdzaGEyNTY6OTc0MmM4MGE1NzhkNTQxZWIxNzVjMzFiZmY2ZDhmYzM0NjEwZWU3ZmVhNzgwNTJjMGJlOTg1MDFmNjk3YWVhMBp7W2ludGVybmFsXSBsb2FkIG1ldGFkYXRhIGZvciBnY3IuaW8vZGlzdHJvbGVzcy9qYXZhQHNoYTI1NjoyOGVjNTUyNDA1YTkyZWQxYTM3NjdiODFhYWVjZTVjNDhiZDFiODlkZmI1ZjNjMTQ0YjBlNGNlYTRkZDVmZmE0KgsIytae/AUQ1LetSQ=="}
                {"id":"moby.buildkit.trace","aux":"CuEBCkdzaGEyNTY6OTc0MmM4MGE1NzhkNTQxZWIxNzVjMzFiZmY2ZDhmYzM0NjEwZWU3ZmVhNzgwNTJjMGJlOTg1MDFmNjk3YWVhMBp7W2ludGVybmFsXSBsb2FkIG1ldGFkYXRhIGZvciBnY3IuaW8vZGlzdHJvbGVzcy9qYXZhQHNoYTI1NjoyOGVjNTUyNDA1YTkyZWQxYTM3NjdiODFhYWVjZTVjNDhiZDFiODlkZmI1ZjNjMTQ0YjBlNGNlYTRkZDVmZmE0KgsIytae/AUQ1LetSTIMCMzWnvwFEPC12vQCCrQBCkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRppWzEvMV0gRlJPTSBnY3IuaW8vZGlzdHJvbGVzcy9qYXZhQHNoYTI1NjoyOGVjNTUyNDA1YTkyZWQxYTM3NjdiODFhYWVjZTVjNDhiZDFiODlkZmI1ZjNjMTQ0YjBlNGNlYTRkZDVmZmE0"}
                {"id":"moby.buildkit.trace","aux":"CtABCkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRppWzEvMV0gRlJPTSBnY3IuaW8vZGlzdHJvbGVzcy9qYXZhQHNoYTI1NjoyOGVjNTUyNDA1YTkyZWQxYTM3NjdiODFhYWVjZTVjNDhiZDFiODlkZmI1ZjNjMTQ0YjBlNGNlYTRkZDVmZmE0KgwIzNae/AUQ9IaF9QIyDAjM1p78BRDEvZH1Ag=="}
                {"id":"moby.buildkit.trace","aux":"CsIBCkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRppWzEvMV0gRlJPTSBnY3IuaW8vZGlzdHJvbGVzcy9qYXZhQHNoYTI1NjoyOGVjNTUyNDA1YTkyZWQxYTM3NjdiODFhYWVjZTVjNDhiZDFiODlkZmI1ZjNjMTQ0YjBlNGNlYTRkZDVmZmE0KgwIzNae/AUQ1MKh9QISzQEKZnJlc29sdmUgZ2NyLmlvL2Rpc3Ryb2xlc3MvamF2YUBzaGEyNTY6MjhlYzU1MjQwNWE5MmVkMWEzNzY3YjgxYWFlY2U1YzQ4YmQxYjg5ZGZiNWYzYzE0NGIwZTRjZWE0ZGQ1ZmZhNBJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEyDAjM1p78BRC4zab1AjoMCMzWnvwFEMyopfUC"}
                {"id":"moby.buildkit.trace","aux":"CtABCkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRppWzEvMV0gRlJPTSBnY3IuaW8vZGlzdHJvbGVzcy9qYXZhQHNoYTI1NjoyOGVjNTUyNDA1YTkyZWQxYTM3NjdiODFhYWVjZTVjNDhiZDFiODlkZmI1ZjNjMTQ0YjBlNGNlYTRkZDVmZmE0KgwIzNae/AUQ1MKh9QIyDAjM1p78BRDIpKH2AhLbAQpmcmVzb2x2ZSBnY3IuaW8vZGlzdHJvbGVzcy9qYXZhQHNoYTI1NjoyOGVjNTUyNDA1YTkyZWQxYTM3NjdiODFhYWVjZTVjNDhiZDFiODlkZmI1ZjNjMTQ0YjBlNGNlYTRkZDVmZmE0EkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MTIMCMzWnvwFEIiWkfYCOgwIzNae/AUQzKil9QJCDAjM1p78BRCAwI/2Ag=="}
                {"id":"moby.buildkit.trace","aux":"CsIBCkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRppWzEvMV0gRlJPTSBnY3IuaW8vZGlzdHJvbGVzcy9qYXZhQHNoYTI1NjoyOGVjNTUyNDA1YTkyZWQxYTM3NjdiODFhYWVjZTVjNDhiZDFiODlkZmI1ZjNjMTQ0YjBlNGNlYTRkZDVmZmE0KgwIzNae/AUQ3Mip9gI="}
                {"id":"moby.buildkit.trace","aux":"EsABCkdzaGEyNTY6MzFlYjI4OTk2ODA0YmViNDZhZTY4NmQ4NjU3MjFmZjYxMGUyYTg5ZGMxM2QzZGNiN2M0NjIyMzc3NDQ3ZjExZBJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaC2Rvd25sb2FkaW5nKMfNvwMyDAjN1p78BRDgnI+YAjoMCM3WnvwFEJCM2/wBEr8BCkdzaGEyNTY6ZTcwOGJlOThjNThmNmJiMjdiZjJiNzgzOWQ3ZTYzYWQ1YmU2NjdiMTU4NWM2MTQyYWY0ZDNkYmJlODBhNzMzMBJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaC2Rvd25sb2FkaW5nKMykJzIMCM3WnvwFEOi3k5gCOgwIzdae/AUQhKbc/AESwAEKR3NoYTI1Njo0ZmFiZmVkM2E4MjFkZjBjODMxMTVkMGNlNjVlYWY0Y2QzNmJlODc3N2U2M2U5ZDZmNmM3MjJjYWU4MGY0N2Y5EkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoLZG93bmxvYWRpbmcoxbiVAjIMCM3WnvwFEKSelZgCOgwIzdae/AUQmILj/AESxwEKR3NoYTI1NjoyOGVjNTUyNDA1YTkyZWQxYTM3NjdiODFhYWVjZTVjNDhiZDFiODlkZmI1ZjNjMTQ0YjBlNGNlYTRkZDVmZmE0EkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoEZG9uZSDbCijbCjIMCM3WnvwFEMzxmZgCOgwIzdae/AUQ/MTQ+wFCCwjM1p78BRCwvdhM"}
                {"id":"moby.buildkit.trace","aux":"EsgBCkdzaGEyNTY6NTZjMWQxMDBhMDgyZDQzMzE3YmRjNTFmMDJlMDJlYTFkNmMyZjJiMmE0MmQ0Njc0MGUxNzRjNjAyODliODk5YhJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaBGRvbmUgjQkojQkyDAjN1p78BRDwqpyYAjoMCM3WnvwFELCUh/wBQgwIzNae/AUQ+NnC9AI="}
                {"id":"moby.buildkit.trace","aux":"EsABCkdzaGEyNTY6NGZhYmZlZDNhODIxZGYwYzgzMTE1ZDBjZTY1ZWFmNGNkMzZiZTg3NzdlNjNlOWQ2ZjZjNzIyY2FlODBmNDdmORJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaC2Rvd25sb2FkaW5nKMW4lQIyDAjN1p78BRCk9czHAjoMCM3WnvwFEJiC4/wBEsABCkdzaGEyNTY6MzFlYjI4OTk2ODA0YmViNDZhZTY4NmQ4NjU3MjFmZjYxMGUyYTg5ZGMxM2QzZGNiN2M0NjIyMzc3NDQ3ZjExZBJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaC2Rvd25sb2FkaW5nKMfNvwMyDAjN1p78BRCkxNTHAjoMCM3WnvwFEJCM2/wBEr8BCkdzaGEyNTY6ZTcwOGJlOThjNThmNmJiMjdiZjJiNzgzOWQ3ZTYzYWQ1YmU2NjdiMTU4NWM2MTQyYWY0ZDNkYmJlODBhNzMzMBJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaC2Rvd25sb2FkaW5nKMykJzIMCM3WnvwFEPTp1ccCOgwIzdae/AUQhKbc/AE="}
                {"id":"moby.buildkit.trace","aux":"EsABCkdzaGEyNTY6MzFlYjI4OTk2ODA0YmViNDZhZTY4NmQ4NjU3MjFmZjYxMGUyYTg5ZGMxM2QzZGNiN2M0NjIyMzc3NDQ3ZjExZBJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaC2Rvd25sb2FkaW5nKMfNvwMyDAjN1p78BRCMhoX3AjoMCM3WnvwFEJCM2/wBEr8BCkdzaGEyNTY6ZTcwOGJlOThjNThmNmJiMjdiZjJiNzgzOWQ3ZTYzYWQ1YmU2NjdiMTU4NWM2MTQyYWY0ZDNkYmJlODBhNzMzMBJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaC2Rvd25sb2FkaW5nKMykJzIMCM3WnvwFEPDFifcCOgwIzdae/AUQhKbc/AESwAEKR3NoYTI1Njo0ZmFiZmVkM2E4MjFkZjBjODMxMTVkMGNlNjVlYWY0Y2QzNmJlODc3N2U2M2U5ZDZmNmM3MjJjYWU4MGY0N2Y5EkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoLZG93bmxvYWRpbmcoxbiVAjIMCM3WnvwFELi4jvcCOgwIzdae/AUQmILj/AE="}
                {"id":"moby.buildkit.trace","aux":"EsIBCkdzaGEyNTY6ZTcwOGJlOThjNThmNmJiMjdiZjJiNzgzOWQ3ZTYzYWQ1YmU2NjdiMTU4NWM2MTQyYWY0ZDNkYmJlODBhNzMzMBJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaC2Rvd25sb2FkaW5nIICAECjMpCcyCwjO1p78BRCMsJIpOgwIzdae/AUQhKbc/AESwwEKR3NoYTI1Njo0ZmFiZmVkM2E4MjFkZjBjODMxMTVkMGNlNjVlYWY0Y2QzNmJlODc3N2U2M2U5ZDZmNmM3MjJjYWU4MGY0N2Y5EkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoLZG93bmxvYWRpbmcggIAXKMW4lQIyCwjO1p78BRCU6pQpOgwIzdae/AUQmILj/AESwwEKR3NoYTI1NjozMWViMjg5OTY4MDRiZWI0NmFlNjg2ZDg2NTcyMWZmNjEwZTJhODlkYzEzZDNkY2I3YzQ2MjIzNzc0NDdmMTFkEkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoLZG93bmxvYWRpbmcggIACKMfNvwMyCwjO1p78BRDEuJcpOgwIzdae/AUQkIzb/AE="}
                {"id":"moby.buildkit.trace","aux":"EsgBCkdzaGEyNTY6ZTcwOGJlOThjNThmNmJiMjdiZjJiNzgzOWQ3ZTYzYWQ1YmU2NjdiMTU4NWM2MTQyYWY0ZDNkYmJlODBhNzMzMBJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaBGRvbmUgzKQnKMykJzILCM7WnvwFEKzg2Fg6DAjN1p78BRCEptz8AUILCM7WnvwFEOSS5kASwwEKR3NoYTI1Njo0ZmFiZmVkM2E4MjFkZjBjODMxMTVkMGNlNjVlYWY0Y2QzNmJlODc3N2U2M2U5ZDZmNmM3MjJjYWU4MGY0N2Y5EkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoLZG93bmxvYWRpbmcggIBAKMW4lQIyCwjO1p78BRDM7d5YOgwIzdae/AUQmILj/AESvwEKR3NoYTI1NjpmZWJhYjMxMWQ0MzIzNzdhNjhlNTM3ZGZkMTZhODNhMTg1ZTlmYzIxNGU5YjUwOWFmZWEwZDVlOWU4MTU0MDUzEkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoLZG93bmxvYWRpbmco1qy+HDILCM7WnvwFEMTD31g6DAjN1p78BRCM4eb8ARLDAQpHc2hhMjU2OjMxZWIyODk5NjgwNGJlYjQ2YWU2ODZkODY1NzIxZmY2MTBlMmE4OWRjMTNkM2RjYjdjNDYyMjM3NzQ0N2YxMWQSR3NoYTI1NjoxNjkxYmNmOGMwNWFkNTg0NWYyYjYyNDU2ODhhNzNiNDM5NzliZmNlZDZlMzQ5NTc1NTI5OTYxMTY0ZTI2MzgxGgtkb3dubG9hZGluZyCAgBQox82/AzILCM7WnvwFEOC84Vg6DAjN1p78BRCQjNv8AQ=="}
                {"id":"moby.buildkit.trace","aux":"EsQBCkdzaGEyNTY6MzFlYjI4OTk2ODA0YmViNDZhZTY4NmQ4NjU3MjFmZjYxMGUyYTg5ZGMxM2QzZGNiN2M0NjIyMzc3NDQ3ZjExZBJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaC2Rvd25sb2FkaW5nIICASijHzb8DMgwIztae/AUQ9Lrh5wE6DAjN1p78BRCQjNv8ARLFAQpHc2hhMjU2OjRmYWJmZWQzYTgyMWRmMGM4MzExNWQwY2U2NWVhZjRjZDM2YmU4Nzc3ZTYzZTlkNmY2YzcyMmNhZTgwZjQ3ZjkSR3NoYTI1NjoxNjkxYmNmOGMwNWFkNTg0NWYyYjYyNDU2ODhhNzNiNDM5NzliZmNlZDZlMzQ5NTc1NTI5OTYxMTY0ZTI2MzgxGgtkb3dubG9hZGluZyCAgOYBKMW4lQIyDAjO1p78BRDs3+nnAToMCM3WnvwFEJiC4/wBEsABCkdzaGEyNTY6ZmViYWIzMTFkNDMyMzc3YTY4ZTUzN2RmZDE2YTgzYTE4NWU5ZmMyMTRlOWI1MDlhZmVhMGQ1ZTllODE1NDA1MxJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaC2Rvd25sb2FkaW5nKNasvhwyDAjO1p78BRC80+rnAToMCM3WnvwFEIzh5vwB"}
                {"id":"moby.buildkit.trace","aux":"EsABCkdzaGEyNTY6ZmViYWIzMTFkNDMyMzc3YTY4ZTUzN2RmZDE2YTgzYTE4NWU5ZmMyMTRlOWI1MDlhZmVhMGQ1ZTllODE1NDA1MxJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaC2Rvd25sb2FkaW5nKNasvhwyDAjO1p78BRDg1MKXAjoMCM3WnvwFEIzh5vwBEsQBCkdzaGEyNTY6MzFlYjI4OTk2ODA0YmViNDZhZTY4NmQ4NjU3MjFmZjYxMGUyYTg5ZGMxM2QzZGNiN2M0NjIyMzc3NDQ3ZjExZBJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaC2Rvd25sb2FkaW5nIICAXijHzb8DMgwIztae/AUQhO/GlwI6DAjN1p78BRCQjNv8ARLMAQpHc2hhMjU2OjRmYWJmZWQzYTgyMWRmMGM4MzExNWQwY2U2NWVhZjRjZDM2YmU4Nzc3ZTYzZTlkNmY2YzcyMmNhZTgwZjQ3ZjkSR3NoYTI1NjoxNjkxYmNmOGMwNWFkNTg0NWYyYjYyNDU2ODhhNzNiNDM5NzliZmNlZDZlMzQ5NTc1NTI5OTYxMTY0ZTI2MzgxGgRkb25lIMW4lQIoxbiVAjIMCM7WnvwFEKiryJcCOgwIzdae/AUQmILj/AFCDAjO1p78BRD8vraVAg=="}
                {"id":"moby.buildkit.trace","aux":"EsABClJleHRyYWN0aW5nIHNoYTI1NjozMWViMjg5OTY4MDRiZWI0NmFlNjg2ZDg2NTcyMWZmNjEwZTJhODlkYzEzZDNkY2I3YzQ2MjIzNzc0NDdmMTFkEkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoHZXh0cmFjdDILCM/WnvwFEPTW6Rw6CwjP1p78BRC0nucc"}
                {"id":"moby.buildkit.trace","aux":"EsoBCkdzaGEyNTY6MzFlYjI4OTk2ODA0YmViNDZhZTY4NmQ4NjU3MjFmZjYxMGUyYTg5ZGMxM2QzZGNiN2M0NjIyMzc3NDQ3ZjExZBJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaBGRvbmUgx82/AyjHzb8DMgsIz9ae/AUQqKP9KDoMCM3WnvwFEJCM2/wBQgsIz9ae/AUQlMDlGxLDAQpHc2hhMjU2OmZlYmFiMzExZDQzMjM3N2E2OGU1MzdkZmQxNmE4M2ExODVlOWZjMjE0ZTliNTA5YWZlYTBkNWU5ZTgxNTQwNTMSR3NoYTI1NjoxNjkxYmNmOGMwNWFkNTg0NWYyYjYyNDU2ODhhNzNiNDM5NzliZmNlZDZlMzQ5NTc1NTI5OTYxMTY0ZTI2MzgxGgtkb3dubG9hZGluZyDw8Bco1qy+HDILCM/WnvwFENip/ig6DAjN1p78BRCM4eb8AQ=="}
                {"id":"moby.buildkit.trace","aux":"EsABClJleHRyYWN0aW5nIHNoYTI1NjozMWViMjg5OTY4MDRiZWI0NmFlNjg2ZDg2NTcyMWZmNjEwZTJhODlkYzEzZDNkY2I3YzQ2MjIzNzc0NDdmMTFkEkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoHZXh0cmFjdDILCM/WnvwFEJSElU06CwjP1p78BRC0nucc"}
                {"id":"moby.buildkit.trace","aux":"EsMBCkdzaGEyNTY6ZmViYWIzMTFkNDMyMzc3YTY4ZTUzN2RmZDE2YTgzYTE4NWU5ZmMyMTRlOWI1MDlhZmVhMGQ1ZTllODE1NDA1MxJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaC2Rvd25sb2FkaW5nIPDwbyjWrL4cMgsIz9ae/AUQxLjSWDoMCM3WnvwFEIzh5vwB"}
                {"id":"moby.buildkit.trace","aux":"EsABClJleHRyYWN0aW5nIHNoYTI1NjozMWViMjg5OTY4MDRiZWI0NmFlNjg2ZDg2NTcyMWZmNjEwZTJhODlkYzEzZDNkY2I3YzQ2MjIzNzc0NDdmMTFkEkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoHZXh0cmFjdDILCM/WnvwFEPCtqn06CwjP1p78BRC0nucc"}
                {"id":"moby.buildkit.trace","aux":"EsUBCkdzaGEyNTY6ZmViYWIzMTFkNDMyMzc3YTY4ZTUzN2RmZDE2YTgzYTE4NWU5ZmMyMTRlOWI1MDlhZmVhMGQ1ZTllODE1NDA1MxJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaC2Rvd25sb2FkaW5nIPDwwwEo1qy+HDIMCM/WnvwFEJijpIgBOgwIzdae/AUQjOHm/AE="}
                {"id":"moby.buildkit.trace","aux":"Es8BClJleHRyYWN0aW5nIHNoYTI1NjozMWViMjg5OTY4MDRiZWI0NmFlNjg2ZDg2NTcyMWZmNjEwZTJhODlkYzEzZDNkY2I3YzQ2MjIzNzc0NDdmMTFkEkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoHZXh0cmFjdDIMCM/WnvwFEOy3zpYBOgsIz9ae/AUQtJ7nHEIMCM/WnvwFEPThzZYB"}
                {"id":"moby.buildkit.trace","aux":"EsIBClJleHRyYWN0aW5nIHNoYTI1NjplNzA4YmU5OGM1OGY2YmIyN2JmMmI3ODM5ZDdlNjNhZDViZTY2N2IxNTg1YzYxNDJhZjRkM2RiYmU4MGE3MzMwEkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoHZXh0cmFjdDIMCM/WnvwFELjR2KcBOgwIz9ae/AUQ5IvXpwE="}
                {"id":"moby.buildkit.trace","aux":"EsUBCkdzaGEyNTY6ZmViYWIzMTFkNDMyMzc3YTY4ZTUzN2RmZDE2YTgzYTE4NWU5ZmMyMTRlOWI1MDlhZmVhMGQ1ZTllODE1NDA1MxJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaC2Rvd25sb2FkaW5nIPDwkQIo1qy+HDIMCM/WnvwFEOSG/LcBOgwIzdae/AUQjOHm/AE="}
                {"id":"moby.buildkit.trace","aux":"EtABClJleHRyYWN0aW5nIHNoYTI1NjplNzA4YmU5OGM1OGY2YmIyN2JmMmI3ODM5ZDdlNjNhZDViZTY2N2IxNTg1YzYxNDJhZjRkM2RiYmU4MGE3MzMwEkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoHZXh0cmFjdDIMCM/WnvwFELS2h8cBOgwIz9ae/AUQ5IvXpwFCDAjP1p78BRCc2obHAQ=="}
                {"id":"moby.buildkit.trace","aux":"EsIBClJleHRyYWN0aW5nIHNoYTI1Njo0ZmFiZmVkM2E4MjFkZjBjODMxMTVkMGNlNjVlYWY0Y2QzNmJlODc3N2U2M2U5ZDZmNmM3MjJjYWU4MGY0N2Y5EkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoHZXh0cmFjdDIMCM/WnvwFEKDBkdIBOgwIz9ae/AUQ+P2P0gE="}
                {"id":"moby.buildkit.trace","aux":"EsUBCkdzaGEyNTY6ZmViYWIzMTFkNDMyMzc3YTY4ZTUzN2RmZDE2YTgzYTE4NWU5ZmMyMTRlOWI1MDlhZmVhMGQ1ZTllODE1NDA1MxJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaC2Rvd25sb2FkaW5nIPDwvwIo1qy+HDIMCM/WnvwFENCc1ucBOgwIzdae/AUQjOHm/AE="}
                {"id":"moby.buildkit.trace","aux":"EsIBClJleHRyYWN0aW5nIHNoYTI1Njo0ZmFiZmVkM2E4MjFkZjBjODMxMTVkMGNlNjVlYWY0Y2QzNmJlODc3N2U2M2U5ZDZmNmM3MjJjYWU4MGY0N2Y5EkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoHZXh0cmFjdDIMCM/WnvwFEMSaoIICOgwIz9ae/AUQ+P2P0gE="}
                {"id":"moby.buildkit.trace","aux":"EtABClJleHRyYWN0aW5nIHNoYTI1Njo0ZmFiZmVkM2E4MjFkZjBjODMxMTVkMGNlNjVlYWY0Y2QzNmJlODc3N2U2M2U5ZDZmNmM3MjJjYWU4MGY0N2Y5EkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoHZXh0cmFjdDIMCM/WnvwFEODPnZYCOgwIz9ae/AUQ+P2P0gFCDAjP1p78BRCk5pyWAg=="}
                {"id":"moby.buildkit.trace","aux":"EsUBCkdzaGEyNTY6ZmViYWIzMTFkNDMyMzc3YTY4ZTUzN2RmZDE2YTgzYTE4NWU5ZmMyMTRlOWI1MDlhZmVhMGQ1ZTllODE1NDA1MxJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaC2Rvd25sb2FkaW5nIPDwjQMo1qy+HDIMCM/WnvwFEKi6qpcCOgwIzdae/AUQjOHm/AE="}
                {"id":"moby.buildkit.trace","aux":"EswBCkdzaGEyNTY6ZmViYWIzMTFkNDMyMzc3YTY4ZTUzN2RmZDE2YTgzYTE4NWU5ZmMyMTRlOWI1MDlhZmVhMGQ1ZTllODE1NDA1MxJHc2hhMjU2OjE2OTFiY2Y4YzA1YWQ1ODQ1ZjJiNjI0NTY4OGE3M2I0Mzk3OWJmY2VkNmUzNDk1NzU1Mjk5NjExNjRlMjYzODEaBGRvbmUg1qy+HCjWrL4cMgwI09ae/AUQmIrolQM6DAjN1p78BRCM4eb8AUIMCNPWnvwFELTO3oAD"}
                {"id":"moby.buildkit.trace","aux":"EsIBClJleHRyYWN0aW5nIHNoYTI1NjpmZWJhYjMxMWQ0MzIzNzdhNjhlNTM3ZGZkMTZhODNhMTg1ZTlmYzIxNGU5YjUwOWFmZWEwZDVlOWU4MTU0MDUzEkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoHZXh0cmFjdDIMCNPWnvwFEPCknJ4DOgwI09ae/AUQzOiangM="}
                {"id":"moby.buildkit.trace","aux":"EsIBClJleHRyYWN0aW5nIHNoYTI1NjpmZWJhYjMxMWQ0MzIzNzdhNjhlNTM3ZGZkMTZhODNhMTg1ZTlmYzIxNGU5YjUwOWFmZWEwZDVlOWU4MTU0MDUzEkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoHZXh0cmFjdDIMCNPWnvwFEPCZg9QDOgwI09ae/AUQzOiangM="}
                {"id":"moby.buildkit.trace","aux":"Es4BClJleHRyYWN0aW5nIHNoYTI1NjpmZWJhYjMxMWQ0MzIzNzdhNjhlNTM3ZGZkMTZhODNhMTg1ZTlmYzIxNGU5YjUwOWFmZWEwZDVlOWU4MTU0MDUzEkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRoHZXh0cmFjdDILCNXWnvwFEKCuyTE6DAjT1p78BRDM6JqeA0ILCNXWnvwFEPDAyDE="}
                {"id":"moby.buildkit.trace","aux":"Cs8BCkdzaGEyNTY6MTY5MWJjZjhjMDVhZDU4NDVmMmI2MjQ1Njg4YTczYjQzOTc5YmZjZWQ2ZTM0OTU3NTUyOTk2MTE2NGUyNjM4MRppWzEvMV0gRlJPTSBnY3IuaW8vZGlzdHJvbGVzcy9qYXZhQHNoYTI1NjoyOGVjNTUyNDA1YTkyZWQxYTM3NjdiODFhYWVjZTVjNDhiZDFiODlkZmI1ZjNjMTQ0YjBlNGNlYTRkZDVmZmE0KgwIzNae/AUQ3Mip9gIyCwjV1p78BRCwx5dC"}
                {"id":"moby.buildkit.trace","aux":"CmoKR3NoYTI1NjplOGM2MTNlMDdiMGI3ZmYzMzg5M2I2OTRmNzc1OWExMGQ0MmUxODBmMmI0ZGMzNDlmYjU3ZGM2YjcxZGNhYjAwGhJleHBvcnRpbmcgdG8gaW1hZ2UqCwjV1p78BRDEqN9CEoIBChBleHBvcnRpbmcgbGF5ZXJzEkdzaGEyNTY6ZThjNjEzZTA3YjBiN2ZmMzM4OTNiNjk0Zjc3NTlhMTBkNDJlMTgwZjJiNGRjMzQ5ZmI1N2RjNmI3MWRjYWIwMDILCNXWnvwFELCS40I6CwjV1p78BRCg/OBCQgsI1dae/AUQuLziQg=="}
                {"id":"moby.buildkit.trace","aux":"EroBClV3cml0aW5nIGltYWdlIHNoYTI1NjphOTQ0YTkwNmNjOTcxOTgwZTVmNTVkOTkxZmI5MWZhYTkyYTI1YTBhODY2YmNkNTdmY2UyNmE1NjI4ZjkyZmE1EkdzaGEyNTY6ZThjNjEzZTA3YjBiN2ZmMzM4OTNiNjk0Zjc3NTlhMTBkNDJlMTgwZjJiNGRjMzQ5ZmI1N2RjNmI3MWRjYWIwMDILCNXWnvwFEMTVsUQ6CwjV1p78BRDk97BE"}
                {"id":"moby.buildkit.trace","aux":"EscBClV3cml0aW5nIGltYWdlIHNoYTI1NjphOTQ0YTkwNmNjOTcxOTgwZTVmNTVkOTkxZmI5MWZhYTkyYTI1YTBhODY2YmNkNTdmY2UyNmE1NjI4ZjkyZmE1EkdzaGEyNTY6ZThjNjEzZTA3YjBiN2ZmMzM4OTNiNjk0Zjc3NTlhMTBkNDJlMTgwZjJiNGRjMzQ5ZmI1N2RjNmI3MWRjYWIwMDILCNXWnvwFEJjkuUU6CwjV1p78BRDk97BEQgsI1dae/AUQhPa4RRKuAQpJbmFtaW5nIHRvIGRvY2tlci5pby9saWJyYXJ5L2JhdGVjdC1pbnRlZ3JhdGlvbi10ZXN0cy1pbWFnZS1sZWdhY3ktYnVpbGRlchJHc2hhMjU2OmU4YzYxM2UwN2IwYjdmZjMzODkzYjY5NGY3NzU5YTEwZDQyZTE4MGYyYjRkYzM0OWZiNTdkYzZiNzFkY2FiMDAyCwjV1p78BRCg/71FOgsI1dae/AUQ7KO9RQ=="}
                {"id":"moby.buildkit.trace","aux":"CncKR3NoYTI1NjplOGM2MTNlMDdiMGI3ZmYzMzg5M2I2OTRmNzc1OWExMGQ0MmUxODBmMmI0ZGMzNDlmYjU3ZGM2YjcxZGNhYjAwGhJleHBvcnRpbmcgdG8gaW1hZ2UqCwjV1p78BRDEqN9CMgsI1dae/AUQqJbqRhK7AQpJbmFtaW5nIHRvIGRvY2tlci5pby9saWJyYXJ5L2JhdGVjdC1pbnRlZ3JhdGlvbi10ZXN0cy1pbWFnZS1sZWdhY3ktYnVpbGRlchJHc2hhMjU2OmU4YzYxM2UwN2IwYjdmZjMzODkzYjY5NGY3NzU5YTEwZDQyZTE4MGYyYjRkYzM0OWZiNTdkYzZiNzFkY2FiMDAyCwjV1p78BRCM1eZGOgsI1dae/AUQ7KO9RUILCNXWnvwFEJjt5UY="}
                {"id":"moby.image.id","aux":{"ID":"sha256:a944a906cc971980e5f55d991fb91faa92a25a0a866bcd57fce26a5628f92fa5"}}
                {"stream":"Successfully tagged batect-integration-tests-image-legacy-builder:latest\n"}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts build status messages as the build progresses") {
                val pullStepName = "[1/1] FROM gcr.io/distroless/java@sha256:28ec552405a92ed1a3767b81aaece5c48bd1b89dfb5f3c144b0e4cea4dd5ffa4"

                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(0, "[internal] load metadata for gcr.io/distroless/java@sha256:28ec552405a92ed1a3767b81aaece5c48bd1b89dfb5f3c144b0e4cea4dd5ffa4"))),
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(1, pullStepName))),
                            // Line 7:
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Downloading, 0 + 0 + 0 + 1371, 7333575 + 643660 + 4545605 + 1371))),
                            // Line 8:
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Downloading, 0 + 0 + 0 + 1371 + 1165, 7333575 + 643660 + 4545605 + 1371 + 1165))),
                            // Line 9: no change
                            // Line 10: no change
                            // Line 11:
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Downloading, 32768 + 262144 + 376832 + 1371 + 1165, 7333575 + 643660 + 4545605 + 1371 + 1165))),
                            // Line 12:
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Downloading, 327680 + 643660 + 1048576 + 1371 + 1165 + 0, 7333575 + 643660 + 4545605 + 1371 + 1165 + 59741782))),
                            // Line 13:
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Downloading, 1212416 + 643660 + 3768320 + 1371 + 1165 + 0, 7333575 + 643660 + 4545605 + 1371 + 1165 + 59741782))),
                            // Line 14:
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Downloading, 1540096 + 643660 + 4545605 + 1371 + 1165 + 0, 7333575 + 643660 + 4545605 + 1371 + 1165 + 59741782))),
                            // Line 15:
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Downloading, 7333575 + 643660 + 4545605 + 1371 + 1165 + 0, 7333575 + 643660 + 4545605 + 1371 + 1165 + 59741782))),
                            // Line 16:
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Downloading, 7333575 + 643660 + 4545605 + 1371 + 1165 + 391280, 7333575 + 643660 + 4545605 + 1371 + 1165 + 59741782))),
                            // Line 17: no change
                            // Line 18:
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Downloading, 7333575 + 643660 + 4545605 + 1371 + 1165 + 1833072, 7333575 + 643660 + 4545605 + 1371 + 1165 + 59741782))),
                            // Line 19: no change
                            // Line 20:
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Downloading, 7333575 + 643660 + 4545605 + 1371 + 1165 + 3209328, 7333575 + 643660 + 4545605 + 1371 + 1165 + 59741782))),
                            // Line 21: no change
                            // Line 22: no change
                            // Line 23:
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Downloading, 7333575 + 643660 + 4545605 + 1371 + 1165 + 4487280, 7333575 + 643660 + 4545605 + 1371 + 1165 + 59741782))),
                            // Line 24: no change
                            // Line 25: no change
                            // Line 26:
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Downloading, 7333575 + 643660 + 4545605 + 1371 + 1165 + 5240944, 7333575 + 643660 + 4545605 + 1371 + 1165 + 59741782))),
                            // Line 27: no change
                            // Line 28: no change
                            // Line 29:
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Downloading, 7333575 + 643660 + 4545605 + 1371 + 1165 + 6518896, 7333575 + 643660 + 4545605 + 1371 + 1165 + 59741782))),
                            // Line 30:
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.PullComplete, 7333575 + 643660 + 4545605 + 1371 + 1165 + 0, 7333575 + 643660 + 4545605 + 1371 + 1165 + 59741782))),
                            // Line 31:
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.Extracting, 7333575 + 643660 + 4545605 + 1371 + 1165 + 0, 7333575 + 643660 + 4545605 + 1371 + 1165 + 59741782))),
                            // Line 32: no change
                            // Line 33:
                            BuildProgress(setOf(ActiveImageBuildStep.Downloading(1, pullStepName, DownloadOperation.PullComplete, 7333575 + 643660 + 4545605 + 1371 + 1165 + 59741782, 7333575 + 643660 + 4545605 + 1371 + 1165 + 59741782))),
                            // Line 35
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(2, "exporting to image"))),
                            BuildComplete(DockerImage("sha256:a944a906cc971980e5f55d991fb91faa92a25a0a866bcd57fce26a5628f92fa5"))
                        )
                    )
                )
            }

            it("streams output showing the progression of the build") {
                assertThat(
                    output.toString(),
                    equalTo(
                        """
                        |#1 [internal] load metadata for gcr.io/distroless/java@sha256:28ec552405a92ed1a3767b81aaece5c48bd1b89dfb5f3c144b0e4cea4dd5ffa4
                        |#1 DONE
                        |
                        |#2 [1/1] FROM gcr.io/distroless/java@sha256:28ec552405a92ed1a3767b81aaece5c48bd1b89dfb5f3c144b0e4cea4dd5ffa4
                        |#2 resolve gcr.io/distroless/java@sha256:28ec552405a92ed1a3767b81aaece5c48bd1b89dfb5f3c144b0e4cea4dd5ffa4: done
                        |#2 sha256:31eb28996804beb46ae686d865721ff610e2a89dc13d3dcb7c4622377447f11d: downloading 7.3 MB
                        |#2 sha256:e708be98c58f6bb27bf2b7839d7e63ad5be667b1585c6142af4d3dbbe80a7330: downloading 643.7 KB
                        |#2 sha256:4fabfed3a821df0c83115d0ce65eaf4cd36be8777e63e9d6f6c722cae80f47f9: downloading 4.5 MB
                        |#2 sha256:28ec552405a92ed1a3767b81aaece5c48bd1b89dfb5f3c144b0e4cea4dd5ffa4: done
                        |#2 sha256:56c1d100a082d43317bdc51f02e02ea1d6c2f2b2a42d46740e174c60289b899b: done
                        |#2 sha256:febab311d432377a68e537dfd16a83a185e9fc214e9b509afea0d5e9e8154053: downloading 59.7 MB
                        |#2 sha256:31eb28996804beb46ae686d865721ff610e2a89dc13d3dcb7c4622377447f11d: extracting
                        |#2 sha256:31eb28996804beb46ae686d865721ff610e2a89dc13d3dcb7c4622377447f11d: done
                        |#2 sha256:e708be98c58f6bb27bf2b7839d7e63ad5be667b1585c6142af4d3dbbe80a7330: extracting
                        |#2 sha256:e708be98c58f6bb27bf2b7839d7e63ad5be667b1585c6142af4d3dbbe80a7330: done
                        |#2 sha256:4fabfed3a821df0c83115d0ce65eaf4cd36be8777e63e9d6f6c722cae80f47f9: extracting
                        |#2 sha256:4fabfed3a821df0c83115d0ce65eaf4cd36be8777e63e9d6f6c722cae80f47f9: done
                        |#2 sha256:febab311d432377a68e537dfd16a83a185e9fc214e9b509afea0d5e9e8154053: extracting
                        |#2 sha256:febab311d432377a68e537dfd16a83a185e9fc214e9b509afea0d5e9e8154053: done
                        |#2 DONE
                        |
                        |#3 exporting to image
                        |#3 exporting layers: done
                        |#3 writing image sha256:a944a906cc971980e5f55d991fb91faa92a25a0a866bcd57fce26a5628f92fa5: done
                        |#3 naming to docker.io/library/batect-integration-tests-image-legacy-builder: done
                        |#3 DONE
                        |
                        |
                        """.trimMargin()
                    )
                )
            }
        }

        given("a response with trace messages for a multi-stage build with multiple images being pulled simultaneously") {
            val input = """
                {"id":"moby.buildkit.trace","aux":"CtYBCkdzaGEyNTY6MjM3MDAwMGI4ZmEyNTFmYmNlMmM4Y2I3YWRlMDZkYzQyYTcwYmZjODMzMTg0YzBhN2E2M2I4YzUwODI1OGRjMBp9W2ludGVybmFsXSBsb2FkIG1ldGFkYXRhIGZvciBnY3IuaW8vZGlzdHJvbGVzcy9zdGF0aWNAc2hhMjU2OmQwZjQxNGM2NGJkYjBjZWViZWE2NTFiMTRhZDViZTlhMjgxYTdhMWQ2Nzc1MWY5ZGU0MTkzMTYzOTAyMzliNjIqDAj4j5/8BRCUptr6AgrUAQpHc2hhMjU2OjY1YWE0YjcwOGE2M2Q2N2RiNTc3YTZkYjEwNTIxZGNmNjQ4NGUxYzczMDc5YjhiOTI1OGNmOWRhMjRlYjM5OTMae1tpbnRlcm5hbF0gbG9hZCBtZXRhZGF0YSBmb3IgZ2NyLmlvL2Rpc3Ryb2xlc3MvYmFzZUBzaGEyNTY6MmMxMmJkZTNkMDUwODUwZTk3NmZlNjgyMTkzYjk0ZjA4NTU4NjZlYTRmMzdhMTJlZDdkYjg2NjhlODA3MTA0NyoMCPiPn/wFEODO4PoC"}
                {"id":"moby.buildkit.trace","aux":"CuMBCkdzaGEyNTY6MjM3MDAwMGI4ZmEyNTFmYmNlMmM4Y2I3YWRlMDZkYzQyYTcwYmZjODMzMTg0YzBhN2E2M2I4YzUwODI1OGRjMBp9W2ludGVybmFsXSBsb2FkIG1ldGFkYXRhIGZvciBnY3IuaW8vZGlzdHJvbGVzcy9zdGF0aWNAc2hhMjU2OmQwZjQxNGM2NGJkYjBjZWViZWE2NTFiMTRhZDViZTlhMjgxYTdhMWQ2Nzc1MWY5ZGU0MTkzMTYzOTAyMzliNjIqDAj4j5/8BRCUptr6AjILCPyPn/wFEMS07DQ="}
                {"id":"moby.buildkit.trace","aux":"CuIBCkdzaGEyNTY6NjVhYTRiNzA4YTYzZDY3ZGI1NzdhNmRiMTA1MjFkY2Y2NDg0ZTFjNzMwNzliOGI5MjU4Y2Y5ZGEyNGViMzk5Mxp7W2ludGVybmFsXSBsb2FkIG1ldGFkYXRhIGZvciBnY3IuaW8vZGlzdHJvbGVzcy9iYXNlQHNoYTI1NjoyYzEyYmRlM2QwNTA4NTBlOTc2ZmU2ODIxOTNiOTRmMDg1NTg2NmVhNGYzN2ExMmVkN2RiODY2OGU4MDcxMDQ3KgwI+I+f/AUQ4M7g+gIyDAj8j5/8BRCQrKiMAw=="}
                {"id":"moby.buildkit.trace","aux":"CpQCCkdzaGEyNTY6YTA2N2ExMDNiNjVjNzgwM2ZmYzkyMzNkZGY3YTM2ZTBkYTM0NWM2OTM4NGVmY2Q2MTgzNjk0ZTQxZDRjNDQ3ZBJHc2hhMjU2OjZjYjM2YWJkZDk1MTVhNjA0YjNhOTBjNmQ0OGQ4OTkyM2JhNzdlNGQ4N2VlZjYwM2Y5ZWIwYzhlYjEzYmI5NjISR3NoYTI1Njo5ODU0MDAyNzUwYTk3ZmJlZjczMDZkNmY1MjhlNzc5Y2VjY2ViNDhlZTJlM2U1ZDFiN2FiMTZjN2MxMDdmN2IzGjdbc3RhZ2UtMSAyLzJdIENPUFkgLS1mcm9tPWZpcnN0IC9ldGMvcGFzc3dkIC9ldGMvcGFzc3dkCtoBCkdzaGEyNTY6NmNiMzZhYmRkOTUxNWE2MDRiM2E5MGM2ZDQ4ZDg5OTIzYmE3N2U0ZDg3ZWVmNjAzZjllYjBjOGViMTNiYjk2MhpzW3N0YWdlLTEgMS8yXSBGUk9NIGdjci5pby9kaXN0cm9sZXNzL3N0YXRpY0BzaGEyNTY6ZDBmNDE0YzY0YmRiMGNlZWJlYTY1MWIxNGFkNWJlOWEyODFhN2ExZDY3NzUxZjlkZTQxOTMxNjM5MDIzOWI2MioMCPyPn/wFELjw44wDMgwI/I+f/AUQ9J/vjAMKyAEKR3NoYTI1Njo5ODU0MDAyNzUwYTk3ZmJlZjczMDZkNmY1MjhlNzc5Y2VjY2ViNDhlZTJlM2U1ZDFiN2FiMTZjN2MxMDdmN2IzGm9bZmlyc3QgMS8xXSBGUk9NIGdjci5pby9kaXN0cm9sZXNzL2Jhc2VAc2hhMjU2OjJjMTJiZGUzZDA1MDg1MGU5NzZmZTY4MjE5M2I5NGYwODU1ODY2ZWE0ZjM3YTEyZWQ3ZGI4NjY4ZTgwNzEwNDcqDAj8j5/8BRD4tvKMAw=="}
                {"id":"moby.buildkit.trace","aux":"CswBCkdzaGEyNTY6NmNiMzZhYmRkOTUxNWE2MDRiM2E5MGM2ZDQ4ZDg5OTIzYmE3N2U0ZDg3ZWVmNjAzZjllYjBjOGViMTNiYjk2MhpzW3N0YWdlLTEgMS8yXSBGUk9NIGdjci5pby9kaXN0cm9sZXNzL3N0YXRpY0BzaGEyNTY6ZDBmNDE0YzY0YmRiMGNlZWJlYTY1MWIxNGFkNWJlOWEyODFhN2ExZDY3NzUxZjlkZTQxOTMxNjM5MDIzOWI2MioMCPyPn/wFELCN94wDEs0BCmZyZXNvbHZlIGdjci5pby9kaXN0cm9sZXNzL2Jhc2VAc2hhMjU2OjJjMTJiZGUzZDA1MDg1MGU5NzZmZTY4MjE5M2I5NGYwODU1ODY2ZWE0ZjM3YTEyZWQ3ZGI4NjY4ZTgwNzEwNDcSR3NoYTI1Njo5ODU0MDAyNzUwYTk3ZmJlZjczMDZkNmY1MjhlNzc5Y2VjY2ViNDhlZTJlM2U1ZDFiN2FiMTZjN2MxMDdmN2IzMgwI/I+f/AUQ3MT2jAM6DAj8j5/8BRDMq/WMAxLPAQpocmVzb2x2ZSBnY3IuaW8vZGlzdHJvbGVzcy9zdGF0aWNAc2hhMjU2OmQwZjQxNGM2NGJkYjBjZWViZWE2NTFiMTRhZDViZTlhMjgxYTdhMWQ2Nzc1MWY5ZGU0MTkzMTYzOTAyMzliNjISR3NoYTI1Njo2Y2IzNmFiZGQ5NTE1YTYwNGIzYTkwYzZkNDhkODk5MjNiYTc3ZTRkODdlZWY2MDNmOWViMGM4ZWIxM2JiOTYyMgwI/I+f/AUQxKT7jAM6DAj8j5/8BRCo9vmMAw=="}
                {"id":"moby.buildkit.trace","aux":"CtoBCkdzaGEyNTY6NmNiMzZhYmRkOTUxNWE2MDRiM2E5MGM2ZDQ4ZDg5OTIzYmE3N2U0ZDg3ZWVmNjAzZjllYjBjOGViMTNiYjk2MhpzW3N0YWdlLTEgMS8yXSBGUk9NIGdjci5pby9kaXN0cm9sZXNzL3N0YXRpY0BzaGEyNTY6ZDBmNDE0YzY0YmRiMGNlZWJlYTY1MWIxNGFkNWJlOWEyODFhN2ExZDY3NzUxZjlkZTQxOTMxNjM5MDIzOWI2MioMCPyPn/wFELCN94wDMgwI/I+f/AUQ3KipjQMS3QEKaHJlc29sdmUgZ2NyLmlvL2Rpc3Ryb2xlc3Mvc3RhdGljQHNoYTI1NjpkMGY0MTRjNjRiZGIwY2VlYmVhNjUxYjE0YWQ1YmU5YTI4MWE3YTFkNjc3NTFmOWRlNDE5MzE2MzkwMjM5YjYyEkdzaGEyNTY6NmNiMzZhYmRkOTUxNWE2MDRiM2E5MGM2ZDQ4ZDg5OTIzYmE3N2U0ZDg3ZWVmNjAzZjllYjBjOGViMTNiYjk2MjIMCPyPn/wFEMz/pI0DOgwI/I+f/AUQqPb5jANCDAj8j5/8BRDU3qONAw=="}
                {"id":"moby.buildkit.trace","aux":"CswBCkdzaGEyNTY6NmNiMzZhYmRkOTUxNWE2MDRiM2E5MGM2ZDQ4ZDg5OTIzYmE3N2U0ZDg3ZWVmNjAzZjllYjBjOGViMTNiYjk2MhpzW3N0YWdlLTEgMS8yXSBGUk9NIGdjci5pby9kaXN0cm9sZXNzL3N0YXRpY0BzaGEyNTY6ZDBmNDE0YzY0YmRiMGNlZWJlYTY1MWIxNGFkNWJlOWEyODFhN2ExZDY3NzUxZjlkZTQxOTMxNjM5MDIzOWI2MioMCPyPn/wFEICjwI0DEtsBCmZyZXNvbHZlIGdjci5pby9kaXN0cm9sZXNzL2Jhc2VAc2hhMjU2OjJjMTJiZGUzZDA1MDg1MGU5NzZmZTY4MjE5M2I5NGYwODU1ODY2ZWE0ZjM3YTEyZWQ3ZGI4NjY4ZTgwNzEwNDcSR3NoYTI1Njo5ODU0MDAyNzUwYTk3ZmJlZjczMDZkNmY1MjhlNzc5Y2VjY2ViNDhlZTJlM2U1ZDFiN2FiMTZjN2MxMDdmN2IzMgwI/I+f/AUQkLTMjQM6DAj8j5/8BRDMq/WMA0IMCPyPn/wFELSSy40D"}
                {"id":"moby.buildkit.trace","aux":"CtYBCkdzaGEyNTY6OTg1NDAwMjc1MGE5N2ZiZWY3MzA2ZDZmNTI4ZTc3OWNlY2NlYjQ4ZWUyZTNlNWQxYjdhYjE2YzdjMTA3ZjdiMxpvW2ZpcnN0IDEvMV0gRlJPTSBnY3IuaW8vZGlzdHJvbGVzcy9iYXNlQHNoYTI1NjoyYzEyYmRlM2QwNTA4NTBlOTc2ZmU2ODIxOTNiOTRmMDg1NTg2NmVhNGYzN2ExMmVkN2RiODY2OGU4MDcxMDQ3KgwI/I+f/AUQ+LbyjAMyDAj8j5/8BRCQnNSNAw=="}
                {"id":"moby.buildkit.trace","aux":"CsgBCkdzaGEyNTY6OTg1NDAwMjc1MGE5N2ZiZWY3MzA2ZDZmNTI4ZTc3OWNlY2NlYjQ4ZWUyZTNlNWQxYjdhYjE2YzdjMTA3ZjdiMxpvW2ZpcnN0IDEvMV0gRlJPTSBnY3IuaW8vZGlzdHJvbGVzcy9iYXNlQHNoYTI1NjoyYzEyYmRlM2QwNTA4NTBlOTc2ZmU2ODIxOTNiOTRmMDg1NTg2NmVhNGYzN2ExMmVkN2RiODY2OGU4MDcxMDQ3KgwI/I+f/AUQxN/1jQM="}
                {"id":"moby.buildkit.trace","aux":"EsgBCkdzaGEyNTY6ZDBmNDE0YzY0YmRiMGNlZWJlYTY1MWIxNGFkNWJlOWEyODFhN2ExZDY3NzUxZjlkZTQxOTMxNjM5MDIzOWI2MhJHc2hhMjU2OjZjYjM2YWJkZDk1MTVhNjA0YjNhOTBjNmQ0OGQ4OTkyM2JhNzdlNGQ4N2VlZjYwM2Y5ZWIwYzhlYjEzYmI5NjIaBGRvbmUg3AUo3AUyDAj9j5/8BRCAsdzeAjoMCP2Pn/wFEICb37MCQgwI+o+f/AUQoJXVpAMSxwEKR3NoYTI1NjoyNWI4ZmQ0MmZmMzY3NWI2MWJjNjQwMjA5ZGEwYzE3NmQyZWVjZjYzMzZhZDVlZjNjMDkwMDFhZmVkOTQxZGRlEkdzaGEyNTY6NmNiMzZhYmRkOTUxNWE2MDRiM2E5MGM2ZDQ4ZDg5OTIzYmE3N2U0ZDg3ZWVmNjAzZjllYjBjOGViMTNiYjk2MhoEZG9uZSD7BCj7BDIMCP2Pn/wFEMTu594COgwI/Y+f/AUQoJy0tAJCCwj8j5/8BRCYtdI0Er8BCkdzaGEyNTY6NDAwMGFkYmJjM2ViMTA5OWUzYTI2M2M0MThmN2MxZDdkZWYxZmEyZGUwYWUwMGJhNDgyNDVjZGE5Mzg5YzgyMxJHc2hhMjU2OjZjYjM2YWJkZDk1MTVhNjA0YjNhOTBjNmQ0OGQ4OTkyM2JhNzdlNGQ4N2VlZjYwM2Y5ZWIwYzhlYjEzYmI5NjIaC2Rvd25sb2FkaW5nKLSDJzIMCP2Pn/wFEPit6d4COgwI/Y+f/AUQyLz7tAISvQEKR3NoYTI1NjozYzJjYmE5MTkyODNhMjEwNjY1ZTQ4MGJjYmY5NDNlYWFmNGVkODdhODNmMDJlODFiYjI4NmI4YmRlYWQwZTc1EkdzaGEyNTY6NmNiMzZhYmRkOTUxNWE2MDRiM2E5MGM2ZDQ4ZDg5OTIzYmE3N2U0ZDg3ZWVmNjAzZjllYjBjOGViMTNiYjk2MhoLZG93bmxvYWRpbmcoMTIMCP2Pn/wFEJjK6t4COgwI/Y+f/AUQ/OL8tAI="}
                {"id":"moby.buildkit.trace","aux":"EsgBCkdzaGEyNTY6MmMxMmJkZTNkMDUwODUwZTk3NmZlNjgyMTkzYjk0ZjA4NTU4NjZlYTRmMzdhMTJlZDdkYjg2NjhlODA3MTA0NxJHc2hhMjU2Ojk4NTQwMDI3NTBhOTdmYmVmNzMwNmQ2ZjUyOGU3NzljZWNjZWI0OGVlMmUzZTVkMWI3YWIxNmM3YzEwN2Y3YjMaBGRvbmUg5QUo5QUyDAj9j5/8BRC805XfAjoMCP2Pn/wFEOC/kcsCQgwI+o+f/AUQuLSTpQMSyAEKR3NoYTI1NjoxZGJmMDc3YTkzMTNjMDk0YTliNGFlZGQ4NWVhMTM0OTY0NmFiZGRiNGNmYzU4YTVkYzgyM2QyNzQ3N2IyNjgzEkdzaGEyNTY6OTg1NDAwMjc1MGE5N2ZiZWY3MzA2ZDZmNTI4ZTc3OWNlY2NlYjQ4ZWUyZTNlNWQxYjdhYjE2YzdjMTA3ZjdiMxoEZG9uZSCzByizBzIMCP2Pn/wFEKDhmd8COgwI/Y+f/AUQiPPBywJCDAj7j5/8BRCwruG0AhLIAQpHc2hhMjU2OmI0NDdhMzcyMTk2NWZhYWQ1NmY2OTYzNDhmNzlkOGM4MzJlMjZhMDVkOTY2MmE3NjkwZDVkZjhlNTZkOWVhYzcSR3NoYTI1Njo5ODU0MDAyNzUwYTk3ZmJlZjczMDZkNmY1MjhlNzc5Y2VjY2ViNDhlZTJlM2U1ZDFiN2FiMTZjN2MxMDdmN2IzGgRkb25lIK4GKK4GMgwI/Y+f/AUQvNqb3wI6DAj9j5/8BRDI0unLAkIMCPyPn/wFENDajIwDEr8BCkdzaGEyNTY6YTRlYjhhMTQxMmEyMzg5OGRhMzU5Y2Q3M2EzNDg1NjAxYjRhNDFlMjNkZTBhNWVhYjJjYjkzNmFhYTRhZDQ0ZBJHc2hhMjU2Ojk4NTQwMDI3NTBhOTdmYmVmNzMwNmQ2ZjUyOGU3NzljZWNjZWI0OGVlMmUzZTVkMWI3YWIxNmM3YzEwN2Y3YjMaC2Rvd25sb2FkaW5nKO2tKjIMCP2Pn/wFEOTTpN8COgwI/Y+f/AUQ6PK8zAI="}
                {"id":"moby.buildkit.trace","aux":"EsMBCkdzaGEyNTY6YTRlYjhhMTQxMmEyMzg5OGRhMzU5Y2Q3M2EzNDg1NjAxYjRhNDFlMjNkZTBhNWVhYjJjYjkzNmFhYTRhZDQ0ZBJHc2hhMjU2Ojk4NTQwMDI3NTBhOTdmYmVmNzMwNmQ2ZjUyOGU3NzljZWNjZWI0OGVlMmUzZTVkMWI3YWIxNmM3YzEwN2Y3YjMaC2Rvd25sb2FkaW5nIICAHCjtrSoyDAj+j5/8BRDE8YPQAToMCP2Pn/wFEOjyvMwC"}
                {"id":"moby.buildkit.trace","aux":"EsIBClJleHRyYWN0aW5nIHNoYTI1NjphNGViOGExNDEyYTIzODk4ZGEzNTljZDczYTM0ODU2MDFiNGE0MWUyM2RlMGE1ZWFiMmNiOTM2YWFhNGFkNDRkEkdzaGEyNTY6OTg1NDAwMjc1MGE5N2ZiZWY3MzA2ZDZmNTI4ZTc3OWNlY2NlYjQ4ZWUyZTNlNWQxYjdhYjE2YzdjMTA3ZjdiMxoHZXh0cmFjdDIMCP6Pn/wFELzw/9oBOgwI/o+f/AUQhJH+2gE="}
                {"id":"moby.buildkit.trace","aux":"EtABClJleHRyYWN0aW5nIHNoYTI1NjphNGViOGExNDEyYTIzODk4ZGEzNTljZDczYTM0ODU2MDFiNGE0MWUyM2RlMGE1ZWFiMmNiOTM2YWFhNGFkNDRkEkdzaGEyNTY6OTg1NDAwMjc1MGE5N2ZiZWY3MzA2ZDZmNTI4ZTc3OWNlY2NlYjQ4ZWUyZTNlNWQxYjdhYjE2YzdjMTA3ZjdiMxoHZXh0cmFjdDIMCP6Pn/wFELDlzf0BOgwI/o+f/AUQhJH+2gFCDAj+j5/8BRDo/8z9AQ=="}
                {"id":"moby.buildkit.trace","aux":"Er8BCkdzaGEyNTY6NDAwMGFkYmJjM2ViMTA5OWUzYTI2M2M0MThmN2MxZDdkZWYxZmEyZGUwYWUwMGJhNDgyNDVjZGE5Mzg5YzgyMxJHc2hhMjU2OjZjYjM2YWJkZDk1MTVhNjA0YjNhOTBjNmQ0OGQ4OTkyM2JhNzdlNGQ4N2VlZjYwM2Y5ZWIwYzhlYjEzYmI5NjIaC2Rvd25sb2FkaW5nKLSDJzIMCP6Pn/wFEMyggv8BOgwI/Y+f/AUQyLz7tAISxgEKR3NoYTI1NjozYzJjYmE5MTkyODNhMjEwNjY1ZTQ4MGJjYmY5NDNlYWFmNGVkODdhODNmMDJlODFiYjI4NmI4YmRlYWQwZTc1EkdzaGEyNTY6NmNiMzZhYmRkOTUxNWE2MDRiM2E5MGM2ZDQ4ZDg5OTIzYmE3N2U0ZDg3ZWVmNjAzZjllYjBjOGViMTNiYjk2MhoEZG9uZSAxKDEyDAj+j5/8BRDQ1oP/AToMCP2Pn/wFEPzi/LQCQgwI/o+f/AUQjOH07wE="}
                {"id":"moby.buildkit.trace","aux":"EsoBCkdzaGEyNTY6YTRlYjhhMTQxMmEyMzg5OGRhMzU5Y2Q3M2EzNDg1NjAxYjRhNDFlMjNkZTBhNWVhYjJjYjkzNmFhYTRhZDQ0ZBJHc2hhMjU2Ojk4NTQwMDI3NTBhOTdmYmVmNzMwNmQ2ZjUyOGU3NzljZWNjZWI0OGVlMmUzZTVkMWI3YWIxNmM3YzEwN2Y3YjMaBGRvbmUg7a0qKO2tKjIMCP6Pn/wFEMiDzP8BOgwI/Y+f/AUQ6PK8zAJCDAj+j5/8BRDwjOPaAQ=="}
                {"id":"moby.buildkit.trace","aux":"CtYBCkdzaGEyNTY6OTg1NDAwMjc1MGE5N2ZiZWY3MzA2ZDZmNTI4ZTc3OWNlY2NlYjQ4ZWUyZTNlNWQxYjdhYjE2YzdjMTA3ZjdiMxpvW2ZpcnN0IDEvMV0gRlJPTSBnY3IuaW8vZGlzdHJvbGVzcy9iYXNlQHNoYTI1NjoyYzEyYmRlM2QwNTA4NTBlOTc2ZmU2ODIxOTNiOTRmMDg1NTg2NmVhNGYzN2ExMmVkN2RiODY2OGU4MDcxMDQ3KgwI/I+f/AUQxN/1jQMyDAj+j5/8BRCg55COAg=="}
                {"id":"moby.buildkit.trace","aux":"EsIBClJleHRyYWN0aW5nIHNoYTI1Njo0MDAwYWRiYmMzZWIxMDk5ZTNhMjYzYzQxOGY3YzFkN2RlZjFmYTJkZTBhZTAwYmE0ODI0NWNkYTkzODljODIzEkdzaGEyNTY6NmNiMzZhYmRkOTUxNWE2MDRiM2E5MGM2ZDQ4ZDg5OTIzYmE3N2U0ZDg3ZWVmNjAzZjllYjBjOGViMTNiYjk2MhoHZXh0cmFjdDIMCP6Pn/wFEIi/i/kCOgwI/o+f/AUQ9NOJ+QI="}
                {"id":"moby.buildkit.trace","aux":"EsoBCkdzaGEyNTY6NDAwMGFkYmJjM2ViMTA5OWUzYTI2M2M0MThmN2MxZDdkZWYxZmEyZGUwYWUwMGJhNDgyNDVjZGE5Mzg5YzgyMxJHc2hhMjU2OjZjYjM2YWJkZDk1MTVhNjA0YjNhOTBjNmQ0OGQ4OTkyM2JhNzdlNGQ4N2VlZjYwM2Y5ZWIwYzhlYjEzYmI5NjIaBGRvbmUgtIMnKLSDJzIMCP6Pn/wFELy9hI4DOgwI/Y+f/AUQyLz7tAJCDAj+j5/8BRDM3eb4Ag=="}
                {"id":"moby.buildkit.trace","aux":"EtABClJleHRyYWN0aW5nIHNoYTI1Njo0MDAwYWRiYmMzZWIxMDk5ZTNhMjYzYzQxOGY3YzFkN2RlZjFmYTJkZTBhZTAwYmE0ODI0NWNkYTkzODljODIzEkdzaGEyNTY6NmNiMzZhYmRkOTUxNWE2MDRiM2E5MGM2ZDQ4ZDg5OTIzYmE3N2U0ZDg3ZWVmNjAzZjllYjBjOGViMTNiYjk2MhoHZXh0cmFjdDIMCP6Pn/wFELyv4K4DOgwI/o+f/AUQ9NOJ+QJCDAj+j5/8BRDg2N+uAw=="}
                {"id":"moby.buildkit.trace","aux":"EsABClJleHRyYWN0aW5nIHNoYTI1NjozYzJjYmE5MTkyODNhMjEwNjY1ZTQ4MGJjYmY5NDNlYWFmNGVkODdhODNmMDJlODFiYjI4NmI4YmRlYWQwZTc1EkdzaGEyNTY6NmNiMzZhYmRkOTUxNWE2MDRiM2E5MGM2ZDQ4ZDg5OTIzYmE3N2U0ZDg3ZWVmNjAzZjllYjBjOGViMTNiYjk2MhoHZXh0cmFjdDILCP+Pn/wFELTUmgY6Cwj/j5/8BRCs/pgG"}
                {"id":"moby.buildkit.trace","aux":"Es0BClJleHRyYWN0aW5nIHNoYTI1NjozYzJjYmE5MTkyODNhMjEwNjY1ZTQ4MGJjYmY5NDNlYWFmNGVkODdhODNmMDJlODFiYjI4NmI4YmRlYWQwZTc1EkdzaGEyNTY6NmNiMzZhYmRkOTUxNWE2MDRiM2E5MGM2ZDQ4ZDg5OTIzYmE3N2U0ZDg3ZWVmNjAzZjllYjBjOGViMTNiYjk2MhoHZXh0cmFjdDILCP+Pn/wFENzLxQY6Cwj/j5/8BRCs/pgGQgsI/4+f/AUQ+ObEBg=="}
                {"id":"moby.buildkit.trace","aux":"CtkBCkdzaGEyNTY6NmNiMzZhYmRkOTUxNWE2MDRiM2E5MGM2ZDQ4ZDg5OTIzYmE3N2U0ZDg3ZWVmNjAzZjllYjBjOGViMTNiYjk2MhpzW3N0YWdlLTEgMS8yXSBGUk9NIGdjci5pby9kaXN0cm9sZXNzL3N0YXRpY0BzaGEyNTY6ZDBmNDE0YzY0YmRiMGNlZWJlYTY1MWIxNGFkNWJlOWEyODFhN2ExZDY3NzUxZjlkZTQxOTMxNjM5MDIzOWI2MioMCPyPn/wFEICjwI0DMgsI/4+f/AUQ1PrfIw=="}
                {"id":"moby.buildkit.trace","aux":"CqECCkdzaGEyNTY6YTA2N2ExMDNiNjVjNzgwM2ZmYzkyMzNkZGY3YTM2ZTBkYTM0NWM2OTM4NGVmY2Q2MTgzNjk0ZTQxZDRjNDQ3ZBJHc2hhMjU2OjZjYjM2YWJkZDk1MTVhNjA0YjNhOTBjNmQ0OGQ4OTkyM2JhNzdlNGQ4N2VlZjYwM2Y5ZWIwYzhlYjEzYmI5NjISR3NoYTI1Njo5ODU0MDAyNzUwYTk3ZmJlZjczMDZkNmY1MjhlNzc5Y2VjY2ViNDhlZTJlM2U1ZDFiN2FiMTZjN2MxMDdmN2IzGjdbc3RhZ2UtMSAyLzJdIENPUFkgLS1mcm9tPWZpcnN0IC9ldGMvcGFzc3dkIC9ldGMvcGFzc3dkKgsI/4+f/AUQ4NLPJA=="}
                {"id":"moby.buildkit.trace","aux":"Cq4CCkdzaGEyNTY6YTA2N2ExMDNiNjVjNzgwM2ZmYzkyMzNkZGY3YTM2ZTBkYTM0NWM2OTM4NGVmY2Q2MTgzNjk0ZTQxZDRjNDQ3ZBJHc2hhMjU2OjZjYjM2YWJkZDk1MTVhNjA0YjNhOTBjNmQ0OGQ4OTkyM2JhNzdlNGQ4N2VlZjYwM2Y5ZWIwYzhlYjEzYmI5NjISR3NoYTI1Njo5ODU0MDAyNzUwYTk3ZmJlZjczMDZkNmY1MjhlNzc5Y2VjY2ViNDhlZTJlM2U1ZDFiN2FiMTZjN2MxMDdmN2IzGjdbc3RhZ2UtMSAyLzJdIENPUFkgLS1mcm9tPWZpcnN0IC9ldGMvcGFzc3dkIC9ldGMvcGFzc3dkKgsI/4+f/AUQ4NLPJDILCP+Pn/wFEIT8zj0="}
                {"id":"moby.buildkit.trace","aux":"CmoKR3NoYTI1NjplOGM2MTNlMDdiMGI3ZmYzMzg5M2I2OTRmNzc1OWExMGQ0MmUxODBmMmI0ZGMzNDlmYjU3ZGM2YjcxZGNhYjAwGhJleHBvcnRpbmcgdG8gaW1hZ2UqCwj/j5/8BRDUiLE/EnUKEGV4cG9ydGluZyBsYXllcnMSR3NoYTI1NjplOGM2MTNlMDdiMGI3ZmYzMzg5M2I2OTRmNzc1OWExMGQ0MmUxODBmMmI0ZGMzNDlmYjU3ZGM2YjcxZGNhYjAwMgsI/4+f/AUQhNS0PzoLCP+Pn/wFENT/sz8="}
                {"id":"moby.buildkit.trace","aux":"EoIBChBleHBvcnRpbmcgbGF5ZXJzEkdzaGEyNTY6ZThjNjEzZTA3YjBiN2ZmMzM4OTNiNjk0Zjc3NTlhMTBkNDJlMTgwZjJiNGRjMzQ5ZmI1N2RjNmI3MWRjYWIwMDILCP+Pn/wFELytxUM6Cwj/j5/8BRDU/7M/QgsI/4+f/AUQsM3EQw=="}
                {"id":"moby.buildkit.trace","aux":"EroBClV3cml0aW5nIGltYWdlIHNoYTI1NjplZGU3ZjA1N2Q1YWUwNWQ0YWNhM2M2NWUxYTFkNTExNTgwMDJmMjcyMWQ3NzFkZTc1Zjc5MGNjYTRiMDc0OGY5EkdzaGEyNTY6ZThjNjEzZTA3YjBiN2ZmMzM4OTNiNjk0Zjc3NTlhMTBkNDJlMTgwZjJiNGRjMzQ5ZmI1N2RjNmI3MWRjYWIwMDILCP+Pn/wFEOjBikQ6Cwj/j5/8BRDs5IlE"}
                {"id":"moby.buildkit.trace","aux":"EscBClV3cml0aW5nIGltYWdlIHNoYTI1NjplZGU3ZjA1N2Q1YWUwNWQ0YWNhM2M2NWUxYTFkNTExNTgwMDJmMjcyMWQ3NzFkZTc1Zjc5MGNjYTRiMDc0OGY5EkdzaGEyNTY6ZThjNjEzZTA3YjBiN2ZmMzM4OTNiNjk0Zjc3NTlhMTBkNDJlMTgwZjJiNGRjMzQ5ZmI1N2RjNmI3MWRjYWIwMDILCP+Pn/wFELSo2EQ6Cwj/j5/8BRDs5IlEQgsI/4+f/AUQ8MnXRBKoAQpDbmFtaW5nIHRvIGRvY2tlci5pby9saWJyYXJ5L2JhdGVjdC1pbnRlZ3JhdGlvbi10ZXN0cy1pbWFnZS1idWlsZGtpdBJHc2hhMjU2OmU4YzYxM2UwN2IwYjdmZjMzODkzYjY5NGY3NzU5YTEwZDQyZTE4MGYyYjRkYzM0OWZiNTdkYzZiNzFkY2FiMDAyCwj/j5/8BRDIqdtEOgsI/4+f/AUQwNDaRA=="}
                {"id":"moby.image.id","aux":{"ID":"sha256:ede7f057d5ae05d4aca3c65e1a1d51158002f2721d771de75f790cca4b0748f9"}}
                {"id":"moby.buildkit.trace","aux":"CncKR3NoYTI1NjplOGM2MTNlMDdiMGI3ZmYzMzg5M2I2OTRmNzc1OWExMGQ0MmUxODBmMmI0ZGMzNDlmYjU3ZGM2YjcxZGNhYjAwGhJleHBvcnRpbmcgdG8gaW1hZ2UqCwj/j5/8BRDUiLE/MgsI/4+f/AUQqPSkRRK1AQpDbmFtaW5nIHRvIGRvY2tlci5pby9saWJyYXJ5L2JhdGVjdC1pbnRlZ3JhdGlvbi10ZXN0cy1pbWFnZS1idWlsZGtpdBJHc2hhMjU2OmU4YzYxM2UwN2IwYjdmZjMzODkzYjY5NGY3NzU5YTEwZDQyZTE4MGYyYjRkYzM0OWZiNTdkYzZiNzFkY2FiMDAyCwj/j5/8BRDkzaJFOgsI/4+f/AUQwNDaREILCP+Pn/wFEKDvoUU="}
                {"stream":"Successfully tagged batect-integration-tests-image-buildkit:latest\n"}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("posts build status messages as the build progresses") {
                val loadStaticImageMetadataStep = ActiveImageBuildStep.NotDownloading(0, "[internal] load metadata for gcr.io/distroless/static@sha256:d0f414c64bdb0ceebea651b14ad5be9a281a7a1d67751f9de419316390239b62")
                val loadBaseImageMetadataStep = ActiveImageBuildStep.NotDownloading(1, "[internal] load metadata for gcr.io/distroless/base@sha256:2c12bde3d050850e976fe682193b94f0855866ea4f37a12ed7db8668e8071047")
                val pullStaticImageStepName = "[stage-1 1/2] FROM gcr.io/distroless/static@sha256:d0f414c64bdb0ceebea651b14ad5be9a281a7a1d67751f9de419316390239b62"
                val pullStaticImageStepIndex = 2
                val pullBaseImageStepName = "[first 1/1] FROM gcr.io/distroless/base@sha256:2c12bde3d050850e976fe682193b94f0855866ea4f37a12ed7db8668e8071047"
                val pullBaseImageStepIndex = 3
                val pullBaseImageComplete = ActiveImageBuildStep.Downloading(pullBaseImageStepIndex, pullBaseImageStepName, DownloadOperation.PullComplete, 741 + 947 + 814 + 693997, 741 + 947 + 814 + 693997)

                assertThat(
                    eventsPosted,
                    equalTo(
                        listOf(
                            // Line 1:
                            BuildProgress(setOf(loadStaticImageMetadataStep, loadBaseImageMetadataStep)),
                            // Line 2:
                            BuildProgress(setOf(loadBaseImageMetadataStep)),
                            // Line 3: no change (all steps complete, wait for next update)
                            // Line 4:
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(pullBaseImageStepIndex, pullBaseImageStepName))),
                            // Line 5:
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(pullBaseImageStepIndex, pullBaseImageStepName), ActiveImageBuildStep.NotDownloading(pullStaticImageStepIndex, pullStaticImageStepName))),
                            // Line 6:
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(pullBaseImageStepIndex, pullBaseImageStepName))),
                            // Line 7:
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(pullBaseImageStepIndex, pullBaseImageStepName), ActiveImageBuildStep.NotDownloading(pullStaticImageStepIndex, pullStaticImageStepName))),
                            // Line 8:
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(pullStaticImageStepIndex, pullStaticImageStepName))),
                            // Line 9:
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(pullStaticImageStepIndex, pullStaticImageStepName), ActiveImageBuildStep.NotDownloading(pullBaseImageStepIndex, pullBaseImageStepName))),
                            // Line 10:
                            BuildProgress(
                                setOf(
                                    ActiveImageBuildStep.Downloading(pullStaticImageStepIndex, pullStaticImageStepName, DownloadOperation.Downloading, 732 + 635 + 0 + 0, 732 + 635 + 639412 + 49),
                                    ActiveImageBuildStep.NotDownloading(pullBaseImageStepIndex, pullBaseImageStepName),
                                )
                            ),
                            // Line 11:
                            BuildProgress(
                                setOf(
                                    ActiveImageBuildStep.Downloading(pullStaticImageStepIndex, pullStaticImageStepName, DownloadOperation.Downloading, 732 + 635 + 0 + 0, 732 + 635 + 639412 + 49),
                                    ActiveImageBuildStep.Downloading(pullBaseImageStepIndex, pullBaseImageStepName, DownloadOperation.Downloading, 741 + 947 + 814 + 0, 741 + 947 + 814 + 693997),
                                )
                            ),
                            // Line 12:
                            BuildProgress(
                                setOf(
                                    ActiveImageBuildStep.Downloading(pullStaticImageStepIndex, pullStaticImageStepName, DownloadOperation.Downloading, 732 + 635 + 0 + 0, 732 + 635 + 639412 + 49),
                                    ActiveImageBuildStep.Downloading(pullBaseImageStepIndex, pullBaseImageStepName, DownloadOperation.Downloading, 741 + 947 + 814 + 458752, 741 + 947 + 814 + 693997),
                                )
                            ),
                            // Line 13:
                            BuildProgress(
                                setOf(
                                    ActiveImageBuildStep.Downloading(pullStaticImageStepIndex, pullStaticImageStepName, DownloadOperation.Downloading, 732 + 635 + 0 + 0, 732 + 635 + 639412 + 49),
                                    ActiveImageBuildStep.Downloading(pullBaseImageStepIndex, pullBaseImageStepName, DownloadOperation.Extracting, 741 + 947 + 814 + 0, 741 + 947 + 814 + 693997),
                                )
                            ),
                            // Line 14:
                            BuildProgress(
                                setOf(
                                    ActiveImageBuildStep.Downloading(pullStaticImageStepIndex, pullStaticImageStepName, DownloadOperation.Downloading, 732 + 635 + 0 + 0, 732 + 635 + 639412 + 49),
                                    pullBaseImageComplete,
                                )
                            ),
                            // Line 15:
                            BuildProgress(
                                setOf(
                                    ActiveImageBuildStep.Downloading(pullStaticImageStepIndex, pullStaticImageStepName, DownloadOperation.Downloading, 732 + 635 + 0 + 49, 732 + 635 + 639412 + 49),
                                    pullBaseImageComplete,
                                )
                            ),
                            // Line 16: no change
                            // Line 17:
                            BuildProgress(
                                setOf(
                                    ActiveImageBuildStep.Downloading(pullStaticImageStepIndex, pullStaticImageStepName, DownloadOperation.Downloading, 732 + 635 + 0 + 49, 732 + 635 + 639412 + 49),
                                )
                            ),
                            // Line 18:
                            BuildProgress(
                                setOf(
                                    ActiveImageBuildStep.Downloading(pullStaticImageStepIndex, pullStaticImageStepName, DownloadOperation.Extracting, 732 + 635 + 0 + 0, 732 + 635 + 639412 + 49),
                                )
                            ),
                            // Line 19:
                            BuildProgress(
                                setOf(
                                    ActiveImageBuildStep.Downloading(pullStaticImageStepIndex, pullStaticImageStepName, DownloadOperation.PullComplete, 732 + 635 + 639412 + 0, 732 + 635 + 639412 + 49),
                                )
                            ),
                            // Line 20:
                            BuildProgress(
                                setOf(
                                    ActiveImageBuildStep.Downloading(pullStaticImageStepIndex, pullStaticImageStepName, DownloadOperation.Extracting, 732 + 635 + 639412 + 0, 732 + 635 + 639412 + 49),
                                )
                            ),
                            // Line 21: no change
                            // Line 22:
                            BuildProgress(
                                setOf(
                                    ActiveImageBuildStep.Downloading(pullStaticImageStepIndex, pullStaticImageStepName, DownloadOperation.PullComplete, 732 + 635 + 639412 + 49, 732 + 635 + 639412 + 49),
                                )
                            ),
                            // Line 23: no change (all steps complete, wait for next update)
                            // Line 24:
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(4, "[stage-1 2/2] COPY --from=first /etc/passwd /etc/passwd"))),
                            // Line 25: no change (all steps complete, wait for next update)
                            // Line 26:
                            BuildProgress(setOf(ActiveImageBuildStep.NotDownloading(5, "exporting to image"))),
                            // Line 27: no change
                            // Line 28: no change
                            // Line 29: no change
                            // Line 30:
                            BuildComplete(DockerImage("sha256:ede7f057d5ae05d4aca3c65e1a1d51158002f2721d771de75f790cca4b0748f9"))
                            // Line 31: no change (all steps complete, wait for next update)
                            // Line 32: no change
                        )
                    )
                )
            }

            it("streams output showing the progression of the build") {
                assertThat(
                    output.toString(),
                    equalTo(
                        """
                        |#1 [internal] load metadata for gcr.io/distroless/static@sha256:d0f414c64bdb0ceebea651b14ad5be9a281a7a1d67751f9de419316390239b62
                        |#1 ...
                        |
                        |#2 [internal] load metadata for gcr.io/distroless/base@sha256:2c12bde3d050850e976fe682193b94f0855866ea4f37a12ed7db8668e8071047
                        |#2 ...
                        |
                        |#1 [internal] load metadata for gcr.io/distroless/static@sha256:d0f414c64bdb0ceebea651b14ad5be9a281a7a1d67751f9de419316390239b62
                        |#1 DONE
                        |
                        |#2 [internal] load metadata for gcr.io/distroless/base@sha256:2c12bde3d050850e976fe682193b94f0855866ea4f37a12ed7db8668e8071047
                        |#2 DONE
                        |
                        |#3 [stage-1 1/2] FROM gcr.io/distroless/static@sha256:d0f414c64bdb0ceebea651b14ad5be9a281a7a1d67751f9de419316390239b62
                        |#3 ...
                        |
                        |#4 [first 1/1] FROM gcr.io/distroless/base@sha256:2c12bde3d050850e976fe682193b94f0855866ea4f37a12ed7db8668e8071047
                        |#4 ...
                        |
                        |#3 [stage-1 1/2] FROM gcr.io/distroless/static@sha256:d0f414c64bdb0ceebea651b14ad5be9a281a7a1d67751f9de419316390239b62
                        |#3 resolve gcr.io/distroless/static@sha256:d0f414c64bdb0ceebea651b14ad5be9a281a7a1d67751f9de419316390239b62: done
                        |#3 ...
                        |
                        |#4 [first 1/1] FROM gcr.io/distroless/base@sha256:2c12bde3d050850e976fe682193b94f0855866ea4f37a12ed7db8668e8071047
                        |#4 resolve gcr.io/distroless/base@sha256:2c12bde3d050850e976fe682193b94f0855866ea4f37a12ed7db8668e8071047: done
                        |#4 ...
                        |
                        |#3 [stage-1 1/2] FROM gcr.io/distroless/static@sha256:d0f414c64bdb0ceebea651b14ad5be9a281a7a1d67751f9de419316390239b62
                        |#3 sha256:d0f414c64bdb0ceebea651b14ad5be9a281a7a1d67751f9de419316390239b62: done
                        |#3 sha256:25b8fd42ff3675b61bc640209da0c176d2eecf6336ad5ef3c09001afed941dde: done
                        |#3 sha256:4000adbbc3eb1099e3a263c418f7c1d7def1fa2de0ae00ba48245cda9389c823: downloading 639.4 KB
                        |#3 sha256:3c2cba919283a210665e480bcbf943eaaf4ed87a83f02e81bb286b8bdead0e75: downloading 49 B
                        |#3 ...
                        |
                        |#4 [first 1/1] FROM gcr.io/distroless/base@sha256:2c12bde3d050850e976fe682193b94f0855866ea4f37a12ed7db8668e8071047
                        |#4 sha256:2c12bde3d050850e976fe682193b94f0855866ea4f37a12ed7db8668e8071047: done
                        |#4 sha256:1dbf077a9313c094a9b4aedd85ea1349646abddb4cfc58a5dc823d27477b2683: done
                        |#4 sha256:b447a3721965faad56f696348f79d8c832e26a05d9662a7690d5df8e56d9eac7: done
                        |#4 sha256:a4eb8a1412a23898da359cd73a3485601b4a41e23de0a5eab2cb936aaa4ad44d: downloading 694.0 KB
                        |#4 sha256:a4eb8a1412a23898da359cd73a3485601b4a41e23de0a5eab2cb936aaa4ad44d: extracting
                        |#4 sha256:a4eb8a1412a23898da359cd73a3485601b4a41e23de0a5eab2cb936aaa4ad44d: done
                        |#4 ...
                        |
                        |#3 [stage-1 1/2] FROM gcr.io/distroless/static@sha256:d0f414c64bdb0ceebea651b14ad5be9a281a7a1d67751f9de419316390239b62
                        |#3 sha256:4000adbbc3eb1099e3a263c418f7c1d7def1fa2de0ae00ba48245cda9389c823: extracting
                        |#3 sha256:4000adbbc3eb1099e3a263c418f7c1d7def1fa2de0ae00ba48245cda9389c823: done
                        |#3 sha256:3c2cba919283a210665e480bcbf943eaaf4ed87a83f02e81bb286b8bdead0e75: extracting
                        |#3 sha256:3c2cba919283a210665e480bcbf943eaaf4ed87a83f02e81bb286b8bdead0e75: done
                        |#3 DONE
                        |
                        |#4 [first 1/1] FROM gcr.io/distroless/base@sha256:2c12bde3d050850e976fe682193b94f0855866ea4f37a12ed7db8668e8071047
                        |#4 DONE
                        |
                        |#5 [stage-1 2/2] COPY --from=first /etc/passwd /etc/passwd
                        |#5 DONE
                        |
                        |#6 exporting to image
                        |#6 exporting layers: done
                        |#6 writing image sha256:ede7f057d5ae05d4aca3c65e1a1d51158002f2721d771de75f790cca4b0748f9: done
                        |#6 naming to docker.io/library/batect-integration-tests-image-buildkit: done
                        |#6 DONE
                        |
                        |
                        """.trimMargin()
                    )
                )
            }
        }

        given("a response with trace messages for a build with a non-default syntax") {
            /*
                Example:

                # syntax=docker/dockerfile:1.1-experimental
                FROM alpine:3.12.3

                HEALTHCHECK --interval=0.1s CMD echo -n "Hello from the healthcheck"
             */

            val input = """
                {"id":"moby.buildkit.trace","aux":"Cm8KR3NoYTI1NjoxNjk5NjcwZjQ4ZWYyNDExNWQ0MjRiMzRkYjY3ODA4NmNhNzVjYjQwOTY3M2ZiYjIzYmQwZDU1OGFhNGY5YjRhGiRbaW50ZXJuYWxdIGxvYWQgcmVtb3RlIGJ1aWxkIGNvbnRleHQ="}
                {"id":"moby.buildkit.trace","aux":"Cn0KR3NoYTI1NjoxNjk5NjcwZjQ4ZWYyNDExNWQ0MjRiMzRkYjY3ODA4NmNhNzVjYjQwOTY3M2ZiYjIzYmQwZDU1OGFhNGY5YjRhGiRbaW50ZXJuYWxdIGxvYWQgcmVtb3RlIGJ1aWxkIGNvbnRleHQqDAismov/BRDkw4+BAg=="}
                {"id":"moby.buildkit.trace","aux":"CosBCkdzaGEyNTY6MTY5OTY3MGY0OGVmMjQxMTVkNDI0YjM0ZGI2NzgwODZjYTc1Y2I0MDk2NzNmYmIyM2JkMGQ1NThhYTRmOWI0YRokW2ludGVybmFsXSBsb2FkIHJlbW90ZSBidWlsZCBjb250ZXh0KgwIrJqL/wUQ5MOPgQIyDAismov/BRDE3OaNAg=="}
                {"id":"moby.buildkit.trace","aux":"CosBCkdzaGEyNTY6MTY5OTY3MGY0OGVmMjQxMTVkNDI0YjM0ZGI2NzgwODZjYTc1Y2I0MDk2NzNmYmIyM2JkMGQ1NThhYTRmOWI0YRokW2ludGVybmFsXSBsb2FkIHJlbW90ZSBidWlsZCBjb250ZXh0KgwIrJqL/wUQ/L+hjgIyDAismov/BRCI6KOOAg=="}
                {"id":"moby.buildkit.trace","aux":"CqMBCkdzaGEyNTY6YmZjYzk3ZDU4MTU4OWEzMjM3ZTU4NmQyOWFiNTU4ODlkMzNjYzNmNGFiNzM2YjA3MTE5MGJjZjI4MTY2MzUzZBJHc2hhMjU2OjE2OTk2NzBmNDhlZjI0MTE1ZDQyNGIzNGRiNjc4MDg2Y2E3NWNiNDA5NjczZmJiMjNiZDBkNTU4YWE0ZjliNGEaD2NvcHkgL2NvbnRleHQgLw=="}
                {"id":"moby.buildkit.trace","aux":"CrEBCkdzaGEyNTY6YmZjYzk3ZDU4MTU4OWEzMjM3ZTU4NmQyOWFiNTU4ODlkMzNjYzNmNGFiNzM2YjA3MTE5MGJjZjI4MTY2MzUzZBJHc2hhMjU2OjE2OTk2NzBmNDhlZjI0MTE1ZDQyNGIzNGRiNjc4MDg2Y2E3NWNiNDA5NjczZmJiMjNiZDBkNTU4YWE0ZjliNGEaD2NvcHkgL2NvbnRleHQgLyoMCKyai/8FENyPtJ0C"}
                {"id":"moby.buildkit.trace","aux":"Cr8BCkdzaGEyNTY6YmZjYzk3ZDU4MTU4OWEzMjM3ZTU4NmQyOWFiNTU4ODlkMzNjYzNmNGFiNzM2YjA3MTE5MGJjZjI4MTY2MzUzZBJHc2hhMjU2OjE2OTk2NzBmNDhlZjI0MTE1ZDQyNGIzNGRiNjc4MDg2Y2E3NWNiNDA5NjczZmJiMjNiZDBkNTU4YWE0ZjliNGEaD2NvcHkgL2NvbnRleHQgLyoMCKyai/8FENyPtJ0CMgwIrJqL/wUQhNPO6AI="}
                {"id":"moby.buildkit.trace","aux":"Cp4BCkdzaGEyNTY6NWUwNDlhMTM4ZThkNGNmMmU3OWE5ZGEyNGNjYzRkOTVmZGVjZGRlYTIyYjhlODMzYzZlY2ViOTQxZDVlYmZjMxpFcmVzb2x2ZSBpbWFnZSBjb25maWcgZm9yIGRvY2tlci5pby9kb2NrZXIvZG9ja2VyZmlsZToxLjEtZXhwZXJpbWVudGFsKgwIrJqL/wUQ2IyrlwM="}
                {"id":"moby.buildkit.trace","aux":"CqwBCkdzaGEyNTY6NWUwNDlhMTM4ZThkNGNmMmU3OWE5ZGEyNGNjYzRkOTVmZGVjZGRlYTIyYjhlODMzYzZlY2ViOTQxZDVlYmZjMxpFcmVzb2x2ZSBpbWFnZSBjb25maWcgZm9yIGRvY2tlci5pby9kb2NrZXIvZG9ja2VyZmlsZToxLjEtZXhwZXJpbWVudGFsKgwIrJqL/wUQ2IyrlwMyDAivmov/BRC0lPvkAQ=="}
                {"id":"moby.buildkit.trace","aux":"Cs8BCkdzaGEyNTY6ZTE0MTk3YWJlNzIzZTgwNWFjZGY5ZDcyNWFhY2E0NWIzYjEzZDQ5MWQ4ZjUyMjY1ODdhYWQzZDMxMGU2YjVmMRqDAWRvY2tlci1pbWFnZTovL2RvY2tlci5pby9kb2NrZXIvZG9ja2VyZmlsZToxLjEtZXhwZXJpbWVudGFsQHNoYTI1NjpkZTg1YjJmM2EzZThhMmY3ZmU0OGU4ZTg0YTY1ZjZmZGQ1Y2Q1MTgzYWZhNjQxMmZmZjljYWE2ODcxNjQ5YzQ0"}
                {"id":"moby.buildkit.trace","aux":"Ct0BCkdzaGEyNTY6ZTE0MTk3YWJlNzIzZTgwNWFjZGY5ZDcyNWFhY2E0NWIzYjEzZDQ5MWQ4ZjUyMjY1ODdhYWQzZDMxMGU2YjVmMRqDAWRvY2tlci1pbWFnZTovL2RvY2tlci5pby9kb2NrZXIvZG9ja2VyZmlsZToxLjEtZXhwZXJpbWVudGFsQHNoYTI1NjpkZTg1YjJmM2EzZThhMmY3ZmU0OGU4ZTg0YTY1ZjZmZGQ1Y2Q1MTgzYWZhNjQxMmZmZjljYWE2ODcxNjQ5YzQ0KgwIr5qL/wUQhPG95QE="}
                {"id":"moby.buildkit.trace","aux":"CusBCkdzaGEyNTY6ZTE0MTk3YWJlNzIzZTgwNWFjZGY5ZDcyNWFhY2E0NWIzYjEzZDQ5MWQ4ZjUyMjY1ODdhYWQzZDMxMGU2YjVmMRqDAWRvY2tlci1pbWFnZTovL2RvY2tlci5pby9kb2NrZXIvZG9ja2VyZmlsZToxLjEtZXhwZXJpbWVudGFsQHNoYTI1NjpkZTg1YjJmM2EzZThhMmY3ZmU0OGU4ZTg0YTY1ZjZmZGQ1Y2Q1MTgzYWZhNjQxMmZmZjljYWE2ODcxNjQ5YzQ0KgwIr5qL/wUQhPG95QEyDAivmov/BRD8zd3lAQ=="}
                {"id":"moby.buildkit.trace","aux":"Cu0BCkdzaGEyNTY6ZTE0MTk3YWJlNzIzZTgwNWFjZGY5ZDcyNWFhY2E0NWIzYjEzZDQ5MWQ4ZjUyMjY1ODdhYWQzZDMxMGU2YjVmMRqDAWRvY2tlci1pbWFnZTovL2RvY2tlci5pby9kb2NrZXIvZG9ja2VyZmlsZToxLjEtZXhwZXJpbWVudGFsQHNoYTI1NjpkZTg1YjJmM2EzZThhMmY3ZmU0OGU4ZTg0YTY1ZjZmZGQ1Y2Q1MTgzYWZhNjQxMmZmZjljYWE2ODcxNjQ5YzQ0IAEqDAivmov/BRCY6v3lATIMCK+ai/8FEJjegeYB"}
                {"id":"moby.buildkit.trace","aux":"CpUBCkdzaGEyNTY6YmU5ZGE5M2YzOTdkMjRlODc5ZTc1NmM0ZGM5MTBhNjllOGQwNmI2ZjhjODdlMjhjNTg4ZTcwZTZiMGExNjQwYho8W2ludGVybmFsXSBsb2FkIG1ldGFkYXRhIGZvciBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4zKgwIr5qL/wUQ/I+HzgI="}
                {"id":"moby.buildkit.trace","aux":"CqMBCkdzaGEyNTY6YmU5ZGE5M2YzOTdkMjRlODc5ZTc1NmM0ZGM5MTBhNjllOGQwNmI2ZjhjODdlMjhjNTg4ZTcwZTZiMGExNjQwYho8W2ludGVybmFsXSBsb2FkIG1ldGFkYXRhIGZvciBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4zKgwIr5qL/wUQ/I+HzgIyDAiymov/BRDgl5qjAQ=="}
                {"id":"moby.buildkit.trace","aux":"Cr0BCkdzaGEyNTY6NjVlNGRmNGRmYjVjYzk4NjVjMjg0NGExZTgwMzczMmNiYjFmZmQ0YjBjOWUzYzg2NDY3MTNkMTg0NzE5MzIxNBpyWzEvMV0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4zQHNoYTI1NjozYzc0OTdiZjBjN2FmOTM0MjgyNDJkNjE3NmU4Zjc5MDVmMjIwMWQ4ZmM1ODYxZjQ1YmU3YTM0NmI1ZjIzNDM2"}
                {"id":"moby.buildkit.trace","aux":"CssBCkdzaGEyNTY6NjVlNGRmNGRmYjVjYzk4NjVjMjg0NGExZTgwMzczMmNiYjFmZmQ0YjBjOWUzYzg2NDY3MTNkMTg0NzE5MzIxNBpyWzEvMV0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4zQHNoYTI1NjozYzc0OTdiZjBjN2FmOTM0MjgyNDJkNjE3NmU4Zjc5MDVmMjIwMWQ4ZmM1ODYxZjQ1YmU3YTM0NmI1ZjIzNDM2KgwIspqL/wUQzMrWvQE="}
                {"id":"moby.buildkit.trace","aux":"CtkBCkdzaGEyNTY6NjVlNGRmNGRmYjVjYzk4NjVjMjg0NGExZTgwMzczMmNiYjFmZmQ0YjBjOWUzYzg2NDY3MTNkMTg0NzE5MzIxNBpyWzEvMV0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4zQHNoYTI1NjozYzc0OTdiZjBjN2FmOTM0MjgyNDJkNjE3NmU4Zjc5MDVmMjIwMWQ4ZmM1ODYxZjQ1YmU3YTM0NmI1ZjIzNDM2KgwIspqL/wUQzMrWvQEyDAiymov/BRCo9OW9AQ=="}
                {"id":"moby.buildkit.trace","aux":"CssBCkdzaGEyNTY6NjVlNGRmNGRmYjVjYzk4NjVjMjg0NGExZTgwMzczMmNiYjFmZmQ0YjBjOWUzYzg2NDY3MTNkMTg0NzE5MzIxNBpyWzEvMV0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4zQHNoYTI1NjozYzc0OTdiZjBjN2FmOTM0MjgyNDJkNjE3NmU4Zjc5MDVmMjIwMWQ4ZmM1ODYxZjQ1YmU3YTM0NmI1ZjIzNDM2KgwIspqL/wUQ1L/vvQE="}
                {"id":"moby.buildkit.trace","aux":"EtYBCm9yZXNvbHZlIGRvY2tlci5pby9saWJyYXJ5L2FscGluZTozLjEyLjNAc2hhMjU2OjNjNzQ5N2JmMGM3YWY5MzQyODI0MmQ2MTc2ZThmNzkwNWYyMjAxZDhmYzU4NjFmNDViZTdhMzQ2YjVmMjM0MzYSR3NoYTI1Njo2NWU0ZGY0ZGZiNWNjOTg2NWMyODQ0YTFlODAzNzMyY2JiMWZmZDRiMGM5ZTNjODY0NjcxM2QxODQ3MTkzMjE0MgwIspqL/wUQhJ71vQE6DAiymov/BRDQwvS9AQ=="}
                {"id":"moby.buildkit.trace","aux":"EuQBCm9yZXNvbHZlIGRvY2tlci5pby9saWJyYXJ5L2FscGluZTozLjEyLjNAc2hhMjU2OjNjNzQ5N2JmMGM3YWY5MzQyODI0MmQ2MTc2ZThmNzkwNWYyMjAxZDhmYzU4NjFmNDViZTdhMzQ2YjVmMjM0MzYSR3NoYTI1Njo2NWU0ZGY0ZGZiNWNjOTg2NWMyODQ0YTFlODAzNzMyY2JiMWZmZDRiMGM5ZTNjODY0NjcxM2QxODQ3MTkzMjE0MgwIspqL/wUQ+M6SwAE6DAiymov/BRDQwvS9AUIMCLKai/8FEIjukcAB"}
                {"id":"moby.buildkit.trace","aux":"CtkBCkdzaGEyNTY6NjVlNGRmNGRmYjVjYzk4NjVjMjg0NGExZTgwMzczMmNiYjFmZmQ0YjBjOWUzYzg2NDY3MTNkMTg0NzE5MzIxNBpyWzEvMV0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4zQHNoYTI1NjozYzc0OTdiZjBjN2FmOTM0MjgyNDJkNjE3NmU4Zjc5MDVmMjIwMWQ4ZmM1ODYxZjQ1YmU3YTM0NmI1ZjIzNDM2KgwIspqL/wUQ1L/vvQEyDAiymov/BRCImJ3AAQ=="}
                {"id":"moby.buildkit.trace","aux":"CtsBCkdzaGEyNTY6NjVlNGRmNGRmYjVjYzk4NjVjMjg0NGExZTgwMzczMmNiYjFmZmQ0YjBjOWUzYzg2NDY3MTNkMTg0NzE5MzIxNBpyWzEvMV0gRlJPTSBkb2NrZXIuaW8vbGlicmFyeS9hbHBpbmU6My4xMi4zQHNoYTI1NjozYzc0OTdiZjBjN2FmOTM0MjgyNDJkNjE3NmU4Zjc5MDVmMjIwMWQ4ZmM1ODYxZjQ1YmU3YTM0NmI1ZjIzNDM2IAEqDAiymov/BRCcyLrAATIMCLKai/8FEJCRvcAB"}
                {"id":"moby.buildkit.trace","aux":"CmsKR3NoYTI1NjplOGM2MTNlMDdiMGI3ZmYzMzg5M2I2OTRmNzc1OWExMGQ0MmUxODBmMmI0ZGMzNDlmYjU3ZGM2YjcxZGNhYjAwGhJleHBvcnRpbmcgdG8gaW1hZ2UqDAiymov/BRDk3d3AARKFAQoQZXhwb3J0aW5nIGxheWVycxJHc2hhMjU2OmU4YzYxM2UwN2IwYjdmZjMzODkzYjY5NGY3NzU5YTEwZDQyZTE4MGYyYjRkYzM0OWZiNTdkYzZiNzFkY2FiMDAyDAiymov/BRCAr+TAAToMCLKai/8FEMSy4cABQgwIspqL/wUQjMfjwAE="}
                {"id":"moby.buildkit.trace","aux":"ErwBClV3cml0aW5nIGltYWdlIHNoYTI1NjpkMGU4MzcyNTBhMTEyNWUxY2M2NmM0MTU4NmIyODM3MDIwOGIyYzRiNDc5NTZmZjI4OTMyNzllNTA0M2U0MDEwEkdzaGEyNTY6ZThjNjEzZTA3YjBiN2ZmMzM4OTNiNjk0Zjc3NTlhMTBkNDJlMTgwZjJiNGRjMzQ5ZmI1N2RjNmI3MWRjYWIwMDIMCLKai/8FEIDp8sABOgwIspqL/wUQxP/xwAE="}
                {"id":"moby.image.id","aux":{"ID":"sha256:d0e837250a1125e1cc66c41586b28370208b2c4b47956ff2893279e5043e4010"}}
                {"id":"moby.buildkit.trace","aux":"CnkKR3NoYTI1NjplOGM2MTNlMDdiMGI3ZmYzMzg5M2I2OTRmNzc1OWExMGQ0MmUxODBmMmI0ZGMzNDlmYjU3ZGM2YjcxZGNhYjAwGhJleHBvcnRpbmcgdG8gaW1hZ2UqDAiymov/BRDk3d3AATIMCLKai/8FELC+3MIBEsoBClV3cml0aW5nIGltYWdlIHNoYTI1NjpkMGU4MzcyNTBhMTEyNWUxY2M2NmM0MTU4NmIyODM3MDIwOGIyYzRiNDc5NTZmZjI4OTMyNzllNTA0M2U0MDEwEkdzaGEyNTY6ZThjNjEzZTA3YjBiN2ZmMzM4OTNiNjk0Zjc3NTlhMTBkNDJlMTgwZjJiNGRjMzQ5ZmI1N2RjNmI3MWRjYWIwMDIMCLKai/8FEMDW1cIBOgwIspqL/wUQxP/xwAFCDAiymov/BRD06dTCARK4AQpDbmFtaW5nIHRvIGRvY2tlci5pby9saWJyYXJ5L2JhdGVjdC1pbnRlZ3JhdGlvbi10ZXN0cy1pbWFnZS1idWlsZGtpdBJHc2hhMjU2OmU4YzYxM2UwN2IwYjdmZjMzODkzYjY5NGY3NzU5YTEwZDQyZTE4MGYyYjRkYzM0OWZiNTdkYzZiNzFkY2FiMDAyDAiymov/BRCozNvCAToMCLKai/8FEPjL2MIBQgwIspqL/wUQ5O3awgE="}
                {"stream":"Successfully tagged batect-integration-tests-image-buildkit:latest\n"}
            """.trimIndent()

            beforeEachTest {
                body.readFrom(StringReader(input), outputStream, eventCallback)
            }

            it("streams output showing the progression of the build") {
                assertThat(
                    output.toString(),
                    equalTo(
                        """
                        |#1 [internal] load remote build context
                        |#1 DONE
                        |
                        |#2 copy /context /
                        |#2 DONE
                        |
                        |#3 resolve image config for docker.io/docker/dockerfile:1.1-experimental
                        |#3 DONE
                        |
                        |#4 docker-image://docker.io/docker/dockerfile:1.1-experimental@sha256:de85b2f3a3e8a2f7fe48e8e84a65f6fdd5cd5183afa6412fff9caa6871649c44
                        |#4 CACHED
                        |
                        |#5 [internal] load metadata for docker.io/library/alpine:3.12.3
                        |#5 DONE
                        |
                        |#6 [1/1] FROM docker.io/library/alpine:3.12.3@sha256:3c7497bf0c7af93428242d6176e8f7905f2201d8fc5861f45be7a346b5f23436
                        |#6 resolve docker.io/library/alpine:3.12.3@sha256:3c7497bf0c7af93428242d6176e8f7905f2201d8fc5861f45be7a346b5f23436: done
                        |#6 CACHED
                        |
                        |#7 exporting to image
                        |#7 exporting layers: done
                        |#7 writing image sha256:d0e837250a1125e1cc66c41586b28370208b2c4b47956ff2893279e5043e4010: done
                        |#7 naming to docker.io/library/batect-integration-tests-image-buildkit: done
                        |#7 DONE
                        |
                        |
                        """.trimMargin()
                    )
                )
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
