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
