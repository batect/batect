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

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files

object DockerImageBuildContextSpec : Spek({
    describe("a Docker image build context factory") {
        describe("given the application is running on any operating system") {
            val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
            val contextDirectory by createForEachTest { fileSystem.getPath("/some-dir") }

            val ignoreList by createForEachTest {
                mock<DockerImageBuildIgnoreList> {
                    on { shouldIncludeInContext(any(), eq("Dockerfile")) } doReturn true
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
                    val context by runForEachTest { factory.createFromDirectory(contextDirectory, "Dockerfile") }

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
                    val context by runForEachTest { factory.createFromDirectory(contextDirectory, "Dockerfile") }

                    it("returns a list of entries containing that Dockerfile") {
                        assertThat(context, equalTo(DockerImageBuildContext(setOf(
                            DockerImageBuildContextEntry(dockerfilePath, "Dockerfile")
                        ))))
                    }

                    it("uses the relative path of the Dockerfile when checking if it should be included") {
                        verify(ignoreList).shouldIncludeInContext(fileSystem.getPath("Dockerfile"), "Dockerfile")
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
                    val context by runForEachTest { factory.createFromDirectory(contextDirectory, "Dockerfile") }

                    it("returns a list of entries containing both entries") {
                        assertThat(context, equalTo(DockerImageBuildContext(setOf(
                            DockerImageBuildContextEntry(file1, "file1"),
                            DockerImageBuildContextEntry(file2, "file2")
                        ))))
                    }

                    it("uses the relative paths of the files when checking if they should be included") {
                        verify(ignoreList).shouldIncludeInContext(fileSystem.getPath("file1"), "Dockerfile")
                        verify(ignoreList).shouldIncludeInContext(fileSystem.getPath("file2"), "Dockerfile")
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
                    val context by runForEachTest { factory.createFromDirectory(contextDirectory, "Dockerfile") }

                    it("returns a list of entries containing both entries and the directory") {
                        assertThat(context, equalTo(DockerImageBuildContext(setOf(
                            DockerImageBuildContextEntry(file1, "file1"),
                            DockerImageBuildContextEntry(subdirectory, "subdir"),
                            DockerImageBuildContextEntry(file2, "subdir/file2")
                        ))))
                    }

                    it("uses the relative paths of the files when checking if they should be included") {
                        verify(ignoreList).shouldIncludeInContext(fileSystem.getPath("file1"), "Dockerfile")
                        verify(ignoreList).shouldIncludeInContext(fileSystem.getPath("subdir"), "Dockerfile")
                        verify(ignoreList).shouldIncludeInContext(fileSystem.getPath("subdir/file2"), "Dockerfile")
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
                    val context by runForEachTest { factory.createFromDirectory(contextDirectory, "Dockerfile") }

                    it("returns a list of entries containing both entries") {
                        assertThat(context, equalTo(DockerImageBuildContext(setOf(
                            DockerImageBuildContextEntry(file1, "file1"),
                            DockerImageBuildContextEntry(file2, "file2")
                        ))))
                    }

                    it("uses the relative paths of the files when checking if they should be included") {
                        verify(ignoreList).shouldIncludeInContext(fileSystem.getPath("file1"), "Dockerfile")
                        verify(ignoreList).shouldIncludeInContext(fileSystem.getPath("file2"), "Dockerfile")
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
                    val context by runForEachTest { factory.createFromDirectory(contextDirectory, "Dockerfile") }

                    it("returns a list of entries containing both directories") {
                        assertThat(context, equalTo(DockerImageBuildContext(setOf(
                            DockerImageBuildContextEntry(directory1, "directory1"),
                            DockerImageBuildContextEntry(directory2, "directory2")
                        ))))
                    }

                    it("uses the relative paths of the files when checking if they should be included") {
                        verify(ignoreList).shouldIncludeInContext(fileSystem.getPath("directory1"), "Dockerfile")
                        verify(ignoreList).shouldIncludeInContext(fileSystem.getPath("directory2"), "Dockerfile")
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

                    whenever(ignoreList.shouldIncludeInContext(fileSystem.getPath("fileToExclude"), "Dockerfile")).thenReturn(false)
                }

                on("creating the build context") {
                    val context by runForEachTest { factory.createFromDirectory(contextDirectory, "Dockerfile") }

                    it("returns a list of entries containing only the entries permitted by the .dockerignore file") {
                        assertThat(context, equalTo(DockerImageBuildContext(setOf(
                            DockerImageBuildContextEntry(fileToInclude, "fileToInclude"),
                            DockerImageBuildContextEntry(dockerignoreFile, ".dockerignore")
                        ))))
                    }

                    it("uses the relative paths of the files when checking if they should be included") {
                        verify(ignoreList).shouldIncludeInContext(fileSystem.getPath("fileToInclude"), "Dockerfile")
                        verify(ignoreList).shouldIncludeInContext(fileSystem.getPath("fileToExclude"), "Dockerfile")
                        verify(ignoreList).shouldIncludeInContext(fileSystem.getPath(".dockerignore"), "Dockerfile")
                    }
                }
            }
        }

        describe("given the application is running on Windows") {
            val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.windows()) }
            val contextDirectory by createForEachTest { fileSystem.getPath("c:\\some-dir") }

            val ignoreList by createForEachTest {
                mock<DockerImageBuildIgnoreList> {
                    on { shouldIncludeInContext(any(), eq("Dockerfile")) } doReturn true
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
                    val context by runForEachTest { factory.createFromDirectory(contextDirectory, "Dockerfile") }

                    it("returns a list of entries containing both entries and the directory with Unix-style path separators") {
                        assertThat(context, equalTo(DockerImageBuildContext(setOf(
                            DockerImageBuildContextEntry(file1, "file1"),
                            DockerImageBuildContextEntry(subdirectory, "subdir"),
                            DockerImageBuildContextEntry(file2, "subdir/file2")
                        ))))
                    }

                    it("uses the relative paths of the files when checking if they should be included") {
                        verify(ignoreList).shouldIncludeInContext(fileSystem.getPath("file1"), "Dockerfile")
                        verify(ignoreList).shouldIncludeInContext(fileSystem.getPath("subdir"), "Dockerfile")
                        verify(ignoreList).shouldIncludeInContext(fileSystem.getPath("subdir\\file2"), "Dockerfile")
                    }
                }
            }
        }
    }
})
