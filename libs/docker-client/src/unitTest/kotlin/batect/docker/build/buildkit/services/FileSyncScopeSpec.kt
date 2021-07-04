/*
   Copyright 2017-2021 Charles Korn.

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

package batect.docker.build.buildkit.services

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isEmpty
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files

object FileSyncScopeSpec : Spek({
    describe("a file sync scope") {
        mapOf(
            "Unix-style" to { Jimfs.newFileSystem(Configuration.unix()) },
            "Windows" to { Jimfs.newFileSystem(Configuration.windows()) }
        ).forEach { (description, fileSystemFactory) ->
            describe("when using a $description file system") {
                val fileSystem by createForEachTest { fileSystemFactory() }
                val rootDirectory by createForEachTest { Files.createDirectories(fileSystem.getPath("request", "root")) }

                given("an empty root directory") {
                    val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), emptySet()) }

                    it("returns an empty list of files to sync") {
                        assertThat(request.contents, isEmpty)
                    }
                }

                given("a directory with a single file") {
                    val filePath by createForEachTest { Files.createFile(rootDirectory.resolve("some-file")) }

                    given("there are no criteria included in the request") {
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), emptySet()) }

                        it("returns just that file to sync") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(filePath, "some-file")
                                )
                            )
                        }
                    }

                    given("the request contains an include pattern that matches the file") {
                        val includePatterns = setOf("some-file")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), includePatterns, emptySet()) }

                        it("returns just that file to sync") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(filePath, "some-file")
                                )
                            )
                        }
                    }

                    given("the request contains an include pattern that does not match the file") {
                        val includePatterns = setOf("some-other-file")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), includePatterns, emptySet()) }

                        it("returns an empty list of files to sync") {
                            assertThat(request.contents, isEmpty)
                        }
                    }

                    given("the request contains a follow path that matches the file") {
                        val followPaths = setOf("some-file")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), followPaths) }

                        it("returns just that file to sync") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(filePath, "some-file")
                                )
                            )
                        }
                    }

                    given("the request contains a follow path with a wildcard that matches the file") {
                        val followPaths = setOf("some-*")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), followPaths) }

                        it("returns just that file to sync") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(filePath, "some-file")
                                )
                            )
                        }
                    }

                    given("the request contains a follow path that does not match the file") {
                        val followPaths = setOf("some-other-file")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), followPaths) }

                        it("returns an empty list of files to sync") {
                            assertThat(request.contents, isEmpty)
                        }
                    }

                    given("the request contains a follow path with a wildcard that does not match the file") {
                        val followPaths = setOf("some-*-file")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), followPaths) }

                        it("returns an empty list of files to sync") {
                            assertThat(request.contents, isEmpty)
                        }
                    }

                    given("the request contains an exclude pattern that matches the file") {
                        val excludePatterns = listOf("some-file")
                        val request by createForEachTest { FileSyncScope(rootDirectory, excludePatterns, emptySet(), emptySet()) }

                        it("returns an empty list of files to sync") {
                            assertThat(request.contents, isEmpty)
                        }
                    }

                    given("the request contains an exclude pattern with a wildcard that matches the file") {
                        val excludePatterns = listOf("some-*")
                        val request by createForEachTest { FileSyncScope(rootDirectory, excludePatterns, emptySet(), emptySet()) }

                        it("returns an empty list of files to sync") {
                            assertThat(request.contents, isEmpty)
                        }
                    }

                    given("the request contains a negated exclude pattern that matches the file") {
                        val excludePatterns = listOf("!some-file")
                        val request by createForEachTest { FileSyncScope(rootDirectory, excludePatterns, emptySet(), emptySet()) }

                        it("returns just that file to sync") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(filePath, "some-file")
                                )
                            )
                        }
                    }

                    given("the request contains a negated exclude pattern with a wildcard that matches the file") {
                        val excludePatterns = listOf("!some-*")
                        val request by createForEachTest { FileSyncScope(rootDirectory, excludePatterns, emptySet(), emptySet()) }

                        it("returns just that file to sync") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(filePath, "some-file")
                                )
                            )
                        }
                    }

                    given("the request contains a multiple exclude patterns that exclude and then include the file") {
                        val excludePatterns = listOf("some-*", "!some-f*")
                        val request by createForEachTest { FileSyncScope(rootDirectory, excludePatterns, emptySet(), emptySet()) }

                        it("returns just that file to sync") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(filePath, "some-file")
                                )
                            )
                        }
                    }

                    given("the request contains a multiple exclude patterns that include and then exclude the file") {
                        val excludePatterns = listOf("some-*", "!some-f*", "some-fi*")
                        val request by createForEachTest { FileSyncScope(rootDirectory, excludePatterns, emptySet(), emptySet()) }

                        it("returns an empty list of files to sync") {
                            assertThat(request.contents, isEmpty)
                        }
                    }

                    given("the request contains a follow path and an exclude pattern that both match the file") {
                        val followPaths = setOf("some-file")
                        val excludePatterns = listOf("some-*")
                        val request by createForEachTest { FileSyncScope(rootDirectory, excludePatterns, emptySet(), followPaths) }

                        it("returns an empty list of files to sync") {
                            assertThat(request.contents, isEmpty)
                        }
                    }

                    given("the request contains an include pattern and an exclude pattern that both match the file") {
                        val includePatterns = setOf("some-file")
                        val excludePatterns = listOf("some-*")
                        val request by createForEachTest { FileSyncScope(rootDirectory, excludePatterns, includePatterns, emptySet()) }

                        it("returns an empty list of files to sync") {
                            assertThat(request.contents, isEmpty)
                        }
                    }
                }

                given("a directory containing multiple files") {
                    val firstFilePath by createForEachTest { Files.createFile(rootDirectory.resolve("first-file")) }
                    val secondFilePath by createForEachTest { Files.createFile(rootDirectory.resolve("second-file")) }

                    given("there are no criteria included in the request") {
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), emptySet()) }

                        it("returns all of the files in the directory") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(firstFilePath, "first-file"),
                                    FileSyncScopeEntry(secondFilePath, "second-file")
                                )
                            )
                        }
                    }

                    given("the request contains an include pattern that matches one file") {
                        val includePatterns = setOf("second-file")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), includePatterns, emptySet()) }

                        it("returns just that file to sync") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(secondFilePath, "second-file")
                                )
                            )
                        }
                    }

                    given("the request contains an include pattern that does not match either file") {
                        val includePatterns = setOf("some-file")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), includePatterns, emptySet()) }

                        it("returns an empty list of files to sync") {
                            assertThat(request.contents, isEmpty)
                        }
                    }

                    given("the request contains a follow path that matches one file") {
                        val followPaths = setOf("first-file")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), followPaths) }

                        it("returns just that file to sync") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(firstFilePath, "first-file")
                                )
                            )
                        }
                    }

                    given("the request contains a follow path with a wildcard that matches one file") {
                        val followPaths = setOf("first-*")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), followPaths) }

                        it("returns just that file to sync") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(firstFilePath, "first-file")
                                )
                            )
                        }
                    }

                    given("the request contains a follow path with a wildcard that matches all files in the directory") {
                        val followPaths = setOf("*-file")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), followPaths) }

                        it("returns all of the files in the directory") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(firstFilePath, "first-file"),
                                    FileSyncScopeEntry(secondFilePath, "second-file")
                                )
                            )
                        }
                    }

                    given("the request contains a follow path that does not match any files") {
                        val followPaths = setOf("something-else")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), followPaths) }

                        it("returns an empty list of files to sync") {
                            assertThat(request.contents, isEmpty)
                        }
                    }

                    given("the request contains a follow path with a wildcard that does not match any files") {
                        val followPaths = setOf("something-*")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), followPaths) }

                        it("returns an empty list of files to sync") {
                            assertThat(request.contents, isEmpty)
                        }
                    }

                    given("the request contains an exclude pattern that excludes all files in the directory") {
                        val excludePatterns = listOf("*-file")
                        val request by createForEachTest { FileSyncScope(rootDirectory, excludePatterns, emptySet(), emptySet()) }

                        it("returns an empty list of files to sync") {
                            assertThat(request.contents, isEmpty)
                        }
                    }

                    given("the request contains an exclude pattern that excludes all files and then includes some files in the directory") {
                        val excludePatterns = listOf("*-file", "!second-file")
                        val request by createForEachTest { FileSyncScope(rootDirectory, excludePatterns, emptySet(), emptySet()) }

                        it("returns only the files that are not excluded") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(secondFilePath, "second-file")
                                )
                            )
                        }
                    }

                    given("the request contains an exclude pattern that does not exclude any files in the directory") {
                        val excludePatterns = listOf("*.txt")
                        val request by createForEachTest { FileSyncScope(rootDirectory, excludePatterns, emptySet(), emptySet()) }

                        it("returns all of the files in the directory") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(firstFilePath, "first-file"),
                                    FileSyncScopeEntry(secondFilePath, "second-file")
                                )
                            )
                        }
                    }
                }

                given("a directory containing files and folders") {
                    val fileInRootDirectory by createForEachTest { Files.createFile(rootDirectory.resolve("some-file.txt")) }
                    val childDirectory by createForEachTest { Files.createDirectory(rootDirectory.resolve("the-directory")) }
                    val fileInChildDirectory by createForEachTest { Files.createFile(childDirectory.resolve("some-file.txt")) }

                    given("there are no criteria included in the request") {
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), emptySet()) }

                        it("returns all of the files and directories in the directory and its children") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(fileInRootDirectory, "some-file.txt"),
                                    FileSyncScopeEntry(childDirectory, "the-directory"),
                                    FileSyncScopeEntry(fileInChildDirectory, "the-directory/some-file.txt"),
                                )
                            )
                        }
                    }

                    given("the request contains a follow path that matches files in the root directory") {
                        val followPaths = setOf("some-file.txt")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), followPaths) }

                        it("returns matching files from the root directory") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(fileInRootDirectory, "some-file.txt")
                                )
                            )
                        }
                    }

                    given("the request contains a follow path that matches files in the child directory") {
                        val followPaths = setOf("the-directory/some-file.txt")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), followPaths) }

                        it("returns matching files from the child directory, as well as the child directory itself") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(childDirectory, "the-directory"),
                                    FileSyncScopeEntry(fileInChildDirectory, "the-directory/some-file.txt")
                                )
                            )
                        }
                    }

                    given("the request contains a follow path with a wildcard that matches files in the root directory") {
                        val followPaths = setOf("*.txt")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), followPaths) }

                        it("returns matching files from the root directory") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(fileInRootDirectory, "some-file.txt")
                                )
                            )
                        }
                    }

                    given("the request contains a follow path with a wildcard that matches files in the child directory") {
                        val followPaths = setOf("the-directory/*.txt")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), followPaths) }

                        it("returns matching files from the child directory, as well as the child directory itself") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(childDirectory, "the-directory"),
                                    FileSyncScopeEntry(fileInChildDirectory, "the-directory/some-file.txt")
                                )
                            )
                        }
                    }

                    given("the request contains an include pattern that matches the file in the root directory") {
                        val includePatterns = setOf("some-file.txt")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), includePatterns, emptySet()) }

                        it("returns matching files from the root directory") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(fileInRootDirectory, "some-file.txt")
                                )
                            )
                        }
                    }

                    given("the request contains an include pattern that matches files in the child directory") {
                        val includePatterns = setOf("the-directory/some-file.txt")
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), includePatterns, emptySet()) }

                        it("returns matching files from the child directory, as well as the child directory itself") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(childDirectory, "the-directory"),
                                    FileSyncScopeEntry(fileInChildDirectory, "the-directory/some-file.txt")
                                )
                            )
                        }
                    }

                    given("the request contains a follow path for the file in the child directory and an exclude pattern for the child directory") {
                        val excludePatterns = listOf("the-directory")
                        val followPaths = setOf("the-directory/some-file.txt")
                        val request by createForEachTest { FileSyncScope(rootDirectory, excludePatterns, emptySet(), followPaths) }

                        it("returns an empty list of files to sync") {
                            assertThat(request.contents, isEmpty)
                        }
                    }

                    given("the request contains an include pattern for the file in the child directory and an exclude pattern for the child directory") {
                        val excludePatterns = listOf("the-directory")
                        val includePatterns = setOf("the-directory/some-file.txt")
                        val request by createForEachTest { FileSyncScope(rootDirectory, excludePatterns, includePatterns, emptySet()) }

                        it("returns an empty list of files to sync") {
                            assertThat(request.contents, isEmpty)
                        }
                    }
                }

                given("a directory containing a symlink to another file") {
                    val targetFilePath by createForEachTest { Files.createFile(rootDirectory.resolve("target-file")) }
                    val symlinkPath by createForEachTest { Files.createSymbolicLink(rootDirectory.resolve("the-symlink"), targetFilePath) }

                    given("there are no criteria included in the request") {
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), emptySet()) }

                        it("returns both the symlink and the target file to sync") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(targetFilePath, "target-file"),
                                    FileSyncScopeEntry(symlinkPath, "the-symlink"),
                                )
                            )
                        }
                    }
                }

                given("a directory containing a symlink to a child directory") {
                    val targetDirectoryPath by createForEachTest { Files.createDirectory(rootDirectory.resolve("target-directory")) }
                    val childFilePath by createForEachTest { Files.createFile(targetDirectoryPath.resolve("the-file")) }
                    val symlinkPath by createForEachTest { Files.createSymbolicLink(rootDirectory.resolve("the-symlink"), targetDirectoryPath) }

                    given("there are no criteria included in the request") {
                        val request by createForEachTest { FileSyncScope(rootDirectory, emptyList(), emptySet(), emptySet()) }

                        it("returns both the symlink and the target directory to sync") {
                            assertThat(
                                request.contents,
                                containsInOrder(
                                    FileSyncScopeEntry(targetDirectoryPath, "target-directory"),
                                    FileSyncScopeEntry(childFilePath, "target-directory/the-file"),
                                    FileSyncScopeEntry(symlinkPath, "the-symlink"),
                                )
                            )
                        }
                    }
                }
            }
        }
    }
})

private fun <T> containsInOrder(vararg contents: T): Matcher<List<T>> = equalTo(listOf(*contents))
