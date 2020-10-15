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

package batect.integrationtests

import batect.docker.DockerImage
import batect.docker.api.BuilderVersion
import batect.os.ProcessOutput
import batect.os.ProcessRunner
import batect.testutils.createForGroup
import batect.testutils.runBeforeGroup
import com.natpryce.hamkrest.anyElement
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.hasElement
import com.natpryce.hamkrest.matches
import com.natpryce.hamkrest.or
import com.nhaarman.mockitokotlin2.mock
import okio.sink
import org.spekframework.spek2.Spek
import org.spekframework.spek2.dsl.Skip
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayOutputStream

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

        describe("building an image with BuildKit", skip = if (runBuildKitTests) Skip.No else Skip.Yes("not supported on this version of Docker")) {
            val imageDirectory = testImagesDirectory.resolve("basic-image")
            val output by createForGroup { ByteArrayOutputStream() }
            runBeforeGroup { client.build(imageDirectory, "batect-integration-tests-image-buildkit", BuilderVersion.BuildKit, output.sink()) }

            it("includes all expected output in the response") {
                val lines = output.toString().lines()

                assertThat(lines, hasElement("#1 [internal] load remote build context"))
                assertThat(lines, hasElement("#1 CACHED") or hasElement("#1 DONE"))

                assertThat(lines, hasElement("#2 copy /context /"))
                assertThat(lines, hasElement("#2 CACHED") or hasElement("#2 DONE"))

                assertThat(lines, anyElement(matches("""^#3 \[internal] load metadata for docker.io/library/alpine:.*""".toRegex())))
                assertThat(lines, hasElement("#3 CACHED") or hasElement("#3 DONE"))

                assertThat(lines, anyElement(matches("""^#4 \[1/1] FROM docker.io/library/alpine:.*""".toRegex())))
                assertThat(lines, hasElement("#4 CACHED") or hasElement("#4 DONE"))

                assertThat(lines, hasElement("#5 exporting to image"))
                assertThat(lines, hasElement("#5 CACHED") or hasElement("#5 DONE"))
            }
        }
    }
})

private fun removeImage(imageName: String) {
    val processRunner = ProcessRunner(mock())
    val result = processRunner.runAndCaptureOutput(listOf("docker", "rmi", "-f", imageName))

    assertThat(result, has(ProcessOutput::output, containsSubstring("No such image: $imageName")) or has(ProcessOutput::exitCode, equalTo(0)))
}
