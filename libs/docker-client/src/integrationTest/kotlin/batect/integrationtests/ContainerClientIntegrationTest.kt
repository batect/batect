/*
    Copyright 2017-2022 Charles Korn.

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

import batect.docker.ContainerDirectory
import batect.docker.ContainerFile
import batect.testutils.createForGroup
import batect.testutils.equalTo
import batect.testutils.runBeforeGroup
import com.natpryce.hamkrest.assertion.assertThat
import okio.sink
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayOutputStream

object ContainerClientIntegrationTest : Spek({
    describe("a Docker container client") {
        val client by createForGroup { createClient() }

        describe("uploading files") {
            val image by runBeforeGroup { client.pull("alpine:3.14.0") }
            val fileContent = "This is the file content!"

            mapOf(
                "existing file" to "passwd",
                "new file" to "new-file"
            ).forEach { (description, fileName) ->
                describe("uploading a $description") {
                    val file = ContainerFile(fileName, 123, 456, fileContent.toByteArray(Charsets.UTF_8))

                    val output by runBeforeGroup {
                        val outputStream = ByteArrayOutputStream()
                        val stdout = outputStream.sink()

                        client.withNetwork { network ->
                            client.withContainer(creationRequestForContainer(image, network, listOf("sh", "-c", "stat -c 'UID: %u, GID: %g, size: %s' /etc/$fileName && cat /etc/$fileName"), useTTY = false)) { container ->
                                client.containers.upload(container, setOf(file), "/etc")

                                client.runContainerAndWaitForCompletion(container, stdout, useTTY = false)
                            }
                        }

                        outputStream.toString()
                    }

                    it("uploads the file to the expected path and sets the UID and GID as expected") {
                        assertThat(
                            output,
                            equalTo(
                                """
                                    |UID: 123, GID: 456, size: 25
                                    |This is the file content!
                                """.trimMargin()
                            )
                        )
                    }
                }
            }

            data class DirectoryTestScenario(
                val description: String,
                val targetDirectory: String,
                val directoryNameToUpload: String
            )

            setOf(
                DirectoryTestScenario("existing directory", "/", "etc"),
                DirectoryTestScenario("new directory", "/", "brand-new-directory")
            ).forEach { scenario ->
                describe("uploading a ${scenario.description}") {
                    val directory = ContainerDirectory(scenario.directoryNameToUpload, 123, 456)

                    val output by runBeforeGroup {
                        val outputStream = ByteArrayOutputStream()
                        val stdout = outputStream.sink()

                        client.withNetwork { network ->
                            client.withContainer(creationRequestForContainer(image, network, listOf("sh", "-c", "stat -c 'UID: %u, GID: %g' ${scenario.targetDirectory}${scenario.directoryNameToUpload}"), useTTY = false)) { container ->
                                client.containers.upload(container, setOf(directory), scenario.targetDirectory)

                                client.runContainerAndWaitForCompletion(container, stdout, useTTY = false)
                            }
                        }

                        outputStream.toString()
                    }

                    it("creates the directory at the expected path and sets the UID and GID as expected") {
                        assertThat(output.trim(), equalTo("UID: 123, GID: 456"))
                    }
                }
            }
        }
    }
})
