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

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.nio.file.Files

object DockerImageBuildContextSpec : Spek({
    describe("a Docker image build context factory") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val contextDirectory by createForEachTest { fileSystem.getPath("/some-dir") }

        val ignoreList by createForEachTest {
            mock<DockerImageBuildIgnoreList> {
                on { shouldIncludeInContext(any()) } doReturn true
            }
        }

        val dockerIgnoreParser by createForEachTest {
            mock<DockerIgnoreParser> {
                on { parse(contextDirectory.resolve(".dockerignore")) } doReturn ignoreList
            }
        }

        val factory by createForEachTest { DockerImageBuildContextFactory(dockerIgnoreParser) }

        beforeEachTest {
            Files.createDirectories(contextDirectory)
        }

        given("the context directory is empty") {
            on("creating the build context") {
                val context = factory.createFromDirectory(contextDirectory)

                it("returns an empty list of entries") {
                    assertThat(context, equalTo(DockerImageBuildContext(emptySet())))
                }
            }
        }

        given("the context directory contains just a Dockerfile") {
            val dockerfilePath by createForEachTest { contextDirectory.resolve("Dockerfile") }

            beforeEachTest {
                Files.createFile(dockerfilePath)
            }

            on("creating the build context") {
                val context = factory.createFromDirectory(contextDirectory)

                it("returns a list of entries containing that Dockerfile") {
                    assertThat(context, equalTo(DockerImageBuildContext(setOf(
                        DockerImageBuildContextEntry(dockerfilePath, "Dockerfile")
                    ))))
                }
            }
        }

        given("the context directory contains multiple entries") {
            val file1 by createForEachTest { contextDirectory.resolve("file1") }
            val file2 by createForEachTest { contextDirectory.resolve("file2") }

            beforeEachTest {
                Files.createFile(file1)
                Files.createFile(file2)
            }

            on("creating the build context") {
                val context = factory.createFromDirectory(contextDirectory)

                it("returns a list of entries containing both entries") {
                    assertThat(context, equalTo(DockerImageBuildContext(setOf(
                        DockerImageBuildContextEntry(file1, "file1"),
                        DockerImageBuildContextEntry(file2, "file2")
                    ))))
                }
            }
        }

        given("the context directory contains entries in subdirectories") {
            val file1 by createForEachTest { contextDirectory.resolve("file1") }
            val subdirectory by createForEachTest { contextDirectory.resolve("subdir") }
            val file2 by createForEachTest { subdirectory.resolve("file2") }

            beforeEachTest {
                Files.createFile(file1)
                Files.createDirectories(subdirectory)
                Files.createFile(file2)
            }

            on("creating the build context") {
                val context = factory.createFromDirectory(contextDirectory)

                it("returns a list of entries containing both entries and the directory") {
                    assertThat(context, equalTo(DockerImageBuildContext(setOf(
                        DockerImageBuildContextEntry(file1, "file1"),
                        DockerImageBuildContextEntry(subdirectory, "subdir"),
                        DockerImageBuildContextEntry(file2, "subdir/file2")
                    ))))
                }
            }
        }

        given("the context directory contains a symbolic link to a file") {
            val file1 by createForEachTest { contextDirectory.resolve("file1") }
            val file2 by createForEachTest { contextDirectory.resolve("file2") }

            beforeEachTest {
                Files.createFile(file1)
                Files.createSymbolicLink(file2, file1)
            }

            on("creating the build context") {
                val context = factory.createFromDirectory(contextDirectory)

                it("returns a list of entries containing both entries") {
                    assertThat(context, equalTo(DockerImageBuildContext(setOf(
                        DockerImageBuildContextEntry(file1, "file1"),
                        DockerImageBuildContextEntry(file2, "file2")
                    ))))
                }
            }
        }

        given("the context directory contains a symbolic link to a directory") {
            val directory1 by createForEachTest { contextDirectory.resolve("directory1") }
            val directory2 by createForEachTest { contextDirectory.resolve("directory2") }

            beforeEachTest {
                Files.createDirectory(directory1)
                Files.createSymbolicLink(directory2, directory1)
            }

            on("creating the build context") {
                val context = factory.createFromDirectory(contextDirectory)

                it("returns a list of entries containing both directories") {
                    assertThat(context, equalTo(DockerImageBuildContext(setOf(
                        DockerImageBuildContextEntry(directory1, "directory1"),
                        DockerImageBuildContextEntry(directory2, "directory2")
                    ))))
                }
            }
        }

        given("the context directory contains a .dockerignore file") {
            val fileToInclude by createForEachTest { contextDirectory.resolve("fileToInclude") }
            val fileToExclude by createForEachTest { contextDirectory.resolve("fileToExclude") }
            val dockerignoreFile by createForEachTest { contextDirectory.resolve(".dockerignore") }

            beforeEachTest {
                Files.createFile(fileToInclude)
                Files.createFile(fileToExclude)
                Files.createFile(dockerignoreFile)

                whenever(ignoreList.shouldIncludeInContext(fileToExclude.toString())).thenReturn(false)
            }

            on("creating the build context") {
                val context = factory.createFromDirectory(contextDirectory)

                it("returns a list of entries containing only the entries permitted by the .dockerignore file") {
                    assertThat(context, equalTo(DockerImageBuildContext(setOf(
                        DockerImageBuildContextEntry(fileToInclude, "fileToInclude"),
                        DockerImageBuildContextEntry(dockerignoreFile, ".dockerignore")
                    ))))
                }
            }
        }
    }
})
