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

import batect.integrationtests.build
import batect.integrationtests.createClient
import batect.integrationtests.creationRequestForContainer
import batect.integrationtests.pull
import batect.integrationtests.runContainerAndWaitForCompletion
import batect.integrationtests.testImagesDirectory
import batect.integrationtests.withContainer
import batect.integrationtests.withNetwork
import batect.testutils.createForGroup
import batect.testutils.runBeforeGroup
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import okio.sink
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayOutputStream

object ContainerEndToEndIntegrationTest : Spek({
    describe("running containers") {
        val client by createForGroup { createClient() }

        mapOf(
            "using a pulled image" to { client.pull("alpine:3.7") },
            "using a built image" to { client.build(testImagesDirectory.resolve("basic-image"), "batect-integration-tests-image") }
        ).forEach { (description, imageSource) ->
            describe(description) {
                val image by runBeforeGroup { imageSource() }

                describe("using that image to create and run a container") {
                    val output by runBeforeGroup {
                        val outputStream = ByteArrayOutputStream()
                        val stdout = outputStream.sink()

                        client.withNetwork { network ->
                            client.withContainer(creationRequestForContainer(image, network, ContainerCommands.exitImmediately)) { container ->
                                client.runContainerAndWaitForCompletion(container, stdout)
                            }
                        }

                        outputStream.toString()
                    }

                    it("starts the container successfully") {
                        assertThat(output.trim(), equalTo("Hello from the container"))
                    }
                }
            }
        }
    }
})
