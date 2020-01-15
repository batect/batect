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

package batect.integrationtests.endtoend

import batect.config.PortMapping
import batect.docker.DockerContainer
import batect.integrationtests.build
import batect.integrationtests.createClient
import batect.integrationtests.creationRequestForContainer
import batect.integrationtests.runContainer
import batect.integrationtests.testImagesDirectory
import batect.integrationtests.withContainer
import batect.integrationtests.withNetwork
import batect.testutils.createForGroup
import batect.testutils.runBeforeGroup
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object NetworkingEndToEndIntegrationTest : Spek({
    describe("container networking") {
        val client by createForGroup { createClient() }

        describe("running a container that exposes a port") {
            mapOf(
                "image-with-expose" to "the image has an EXPOSE instruction for the port to be exposed",
                "image-without-expose" to "the image does not have an EXPOSE instruction for the port to be exposed"
            ).forEach { (path, description) ->
                describe("given $description") {
                    val dockerfilePath by createForGroup { testImagesDirectory.resolve(path) }
                    val image by runBeforeGroup { client.build(dockerfilePath, path) }

                    fun runContainerAndGetHttpResponse(container: DockerContainer): Response {
                        return client.runContainer(container) {
                            httpGet("http://localhost:8080").also {
                                client.containers.stop(container)
                            }
                        }
                    }

                    val response by runBeforeGroup {
                        client.withNetwork { network ->
                            client.withContainer(creationRequestForContainer(image, network, emptyList(), portMappings = setOf(PortMapping(8080, 80)))) { container ->
                                runContainerAndGetHttpResponse(container)
                            }
                        }
                    }

                    it("successfully starts the container and exposes the port") {
                        assertThat(response, has(Response::codeValue, equalTo(200)))
                    }
                }
            }
        }
    }
})

private fun httpGet(url: String): Response {
    retry(3) {
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder()
            .url(url)
            .header("Connection", "close")
            .build()

        val response = client.newCall(request).execute()
        response.close() // We don't use the body.

        return response
    }
}

private inline fun <T> retry(retries: Int, operation: () -> T): T {
    val exceptions = mutableListOf<Throwable>()

    for (retry in 1..retries) {
        try {
            return operation()
        } catch (e: Throwable) {
            exceptions.add(e)
        }
    }

    val exceptionDetails = exceptions
        .mapIndexed { i, e -> "Attempt ${i + 1}: $e\n" }
        .joinToString("\n")

    throw RuntimeException("Could not execute operation after $retries attempts. Exceptions were:\n$exceptionDetails")
}

private fun Response.codeValue() = this.code
