/*
   Copyright 2017-2019 Charles Korn.

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

import batect.docker.ImageBuildFailedException
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.withMessage
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.nio.file.Files

object DockerfileParserSpec : Spek({
    describe("a Dockerfile parser") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val path by createForEachTest { fileSystem.getPath("/some-dir/Dockerfile") }

        beforeEachTest {
            Files.createDirectories(path.parent)
        }

        val parser = DockerfileParser()

        given("an empty Dockerfile") {
            beforeEachTest {
                Files.createFile(path)
            }

            on("getting the base image") {
                it("throws an appropriate exception") {
                    assertThat({ parser.extractBaseImageName(path) }, throws<ImageBuildFailedException>(withMessage("The Dockerfile '/some-dir/Dockerfile' is invalid: there is no FROM instruction.")))
                }
            }
        }

        given("a Dockerfile with no FROM instruction") {
            beforeEachTest {
                Files.write(path, listOf("RUN install-stuff.sh"))
            }

            on("getting the base image") {
                it("throws an appropriate exception") {
                    assertThat({ parser.extractBaseImageName(path) }, throws<ImageBuildFailedException>(withMessage("The Dockerfile '/some-dir/Dockerfile' is invalid: there is no FROM instruction.")))
                }
            }
        }

        given("a Dockerfile with a FROM instruction on the first line") {
            beforeEachTest {
                Files.write(path, listOf("FROM the-image"))
            }

            on("getting the base image") {
                val baseImage = parser.extractBaseImageName(path)

                it("returns the base image name") {
                    assertThat(baseImage, equalTo("the-image"))
                }
            }
        }

        given("a Dockerfile with some comments before the FROM instruction") {
            beforeEachTest {
                Files.write(path, listOf(
                    "# A comment",
                    "# Another",
                    "FROM some-image"
                ))
            }

            on("getting the base image") {
                val baseImage = parser.extractBaseImageName(path)

                it("returns the base image name") {
                    assertThat(baseImage, equalTo("some-image"))
                }
            }
        }
    }
})
