/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.config.includes

import batect.VersionInfo
import batect.git.LockingRepositoryCloner
import batect.io.ApplicationPaths
import batect.primitives.Version
import batect.testutils.createForEachTest
import batect.testutils.doesNotThrow
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.throws
import org.araqnid.hamkrest.json.equivalentTo
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset
import java.time.ZonedDateTime

object GitRepositoryCacheSpec : Spek({
    describe("a cache of Git repositories") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val rootLocalStorageDirectory by createForEachTest { fileSystem.getPath("/some/.batect/dir") }
        val paths by createForEachTest { ApplicationPaths(rootLocalStorageDirectory) }
        val cloner by createForEachTest { mock<LockingRepositoryCloner>() }
        val versionInfo by createForEachTest {
            mock<VersionInfo> {
                on { version } doReturn Version(1, 2, 3)
            }
        }

        val currentTime = ZonedDateTime.of(2020, 7, 5, 1, 2, 3, 456789012, ZoneOffset.UTC)
        val cache by createForEachTest { GitRepositoryCache(paths, cloner, versionInfo, { currentTime }) }

        describe("ensuring a repository is cached") {
            val listener by createForEachTest { mock<GitRepositoryCacheNotificationListener>() }
            val repo = GitRepositoryReference("https://github.com/me/my-bundle.git", "my-tag")
            val expectedWorkingCopyDirectory by createForEachTest { fileSystem.getPath("/some/.batect/dir/incl/${repo.cacheKey}") }
            val expectedInfoFile by createForEachTest { fileSystem.getPath("/some/.batect/dir/incl/${repo.cacheKey}.json") }

            fun createWorkingCopy() {
                Files.createDirectories(expectedWorkingCopyDirectory)
            }

            fun createExistingInfoFile() {
                Files.createDirectories(expectedInfoFile.parent)

                Files.write(
                    expectedInfoFile,
                    """
                    |{
                    |    "type": "git",
                    |    "repo": {
                    |        "remote": "https://github.com/me/my-bundle.git",
                    |        "ref": "my-tag",
                    |        "someOtherInfo": "some other value"
                    |    },
                    |    "lastUsed": "2001-01-01T01:02:03.456789012Z",
                    |    "otherInfo": "some value",
                    |    "clonedWithVersion": "4.5.6"
                    |}
                    """.trimMargin().toByteArray(Charsets.UTF_8),
                )
            }

            fun Suite.itClonesTheRepository() {
                it("clones the repository into the expected directory") {
                    verify(cloner).clone(repo.remote, repo.ref, expectedWorkingCopyDirectory)
                }

                it("notifies the listener that the repository is being cloned before cloning the repository, and after the clone has finished") {
                    inOrder(listener, cloner) {
                        verify(listener).onCloning(repo)
                        verify(cloner).clone(any(), any(), any())
                        verify(listener).onCloneComplete()
                    }
                }
            }

            fun Suite.itDoesNotCloneTheRepository() {
                it("does not clone the repository") {
                    verify(cloner, never()).clone(any(), any(), any())
                }

                it("does not notify the listener that the repository is being cloned or has finished cloning") {
                    verifyNoInteractions(listener)
                }
            }

            fun Suite.itReturnsThePathToTheWorkingCopy(workingCopyPath: () -> Path) {
                it("returns the path to the working copy") {
                    assertThat(workingCopyPath(), equalTo(expectedWorkingCopyDirectory))
                }
            }

            fun Suite.itCreatesTheInfoFile() {
                it("creates the info file with details of the repository and the current time") {
                    assertThat(
                        Files.readAllBytes(expectedInfoFile).toString(Charsets.UTF_8),
                        equivalentTo(
                            """
                            |{
                            |    "type": "git",
                            |    "repo": {
                            |        "remote": "https://github.com/me/my-bundle.git",
                            |        "ref": "my-tag"
                            |    },
                            |    "lastUsed": "2020-07-05T01:02:03.456789012Z",
                            |    "clonedWithVersion": "1.2.3"
                            |}
                            """.trimMargin(),
                        ),
                    )
                }
            }

            fun Suite.itUpdatesTheInfoFile() {
                it("updates the info file with details of the repository and the current time, preserving any other information in the file") {
                    assertThat(
                        Files.readAllBytes(expectedInfoFile).toString(Charsets.UTF_8),
                        equivalentTo(
                            """
                            |{
                            |    "type": "git",
                            |    "repo": {
                            |        "remote": "https://github.com/me/my-bundle.git",
                            |        "ref": "my-tag",
                            |        "someOtherInfo": "some other value"
                            |    },
                            |    "lastUsed": "2020-07-05T01:02:03.456789012Z",
                            |    "otherInfo": "some value",
                            |    "clonedWithVersion": "4.5.6"
                            |}
                            """.trimMargin(),
                        ),
                    )
                }
            }

            given("neither the repository folder nor the info file are present") {
                val cachedRepo by runForEachTest { cache.ensureCached(repo, listener) }

                itClonesTheRepository()
                itReturnsThePathToTheWorkingCopy { cachedRepo }
                itCreatesTheInfoFile()
            }

            given("the repository folder is present but the info file is not") {
                beforeEachTest { createWorkingCopy() }

                val cachedRepo by runForEachTest { cache.ensureCached(repo, listener) }

                itDoesNotCloneTheRepository()
                itReturnsThePathToTheWorkingCopy { cachedRepo }
                itCreatesTheInfoFile()
            }

            given("the info file is present but the repository folder is not") {
                beforeEachTest { createExistingInfoFile() }

                val cachedRepo by runForEachTest { cache.ensureCached(repo, listener) }

                itClonesTheRepository()
                itReturnsThePathToTheWorkingCopy { cachedRepo }
                itUpdatesTheInfoFile()
            }

            given("the repository folder and the info file are both present") {
                beforeEachTest {
                    createWorkingCopy()
                    createExistingInfoFile()
                }

                val cachedRepo by runForEachTest { cache.ensureCached(repo, listener) }

                itDoesNotCloneTheRepository()
                itReturnsThePathToTheWorkingCopy { cachedRepo }
                itUpdatesTheInfoFile()
            }
        }

        describe("listing cached repositories") {
            given("the Git cache directory doesn't exist") {
                val cachedRepos by runForEachTest { cache.listAll() }

                it("returns an empty set of cached repositories") {
                    assertThat(cachedRepos, isEmpty)
                }
            }

            given("the Git cache directory exists") {
                beforeEachTest { Files.createDirectories(fileSystem.getPath("/some/.batect/dir/incl")) }

                given("the Git cache directory contains no files") {
                    val cachedRepos by runForEachTest { cache.listAll() }

                    it("returns an empty set of cached repositories") {
                        assertThat(cachedRepos, isEmpty)
                    }
                }

                given("the Git cache directory contains a single info file") {
                    val infoPath by createForEachTest { fileSystem.getPath("/some/.batect/dir/incl/some-key.json") }

                    given("the info file is well-formed") {
                        beforeEachTest {
                            Files.write(
                                infoPath,
                                """
                                    |{
                                    |    "repo": {
                                    |        "remote": "https://github.com/me/my-bundle.git",
                                    |        "ref": "my-tag"
                                    |    },
                                    |    "lastUsed": "2020-07-05T01:02:03.456789012Z",
                                    |    "otherInfo": "some value"
                                    |}
                                """.trimMargin().toByteArray(Charsets.UTF_8),
                            )
                        }

                        given("the Git cache directory contains no other files or corresponding directory") {
                            val cachedRepos by runForEachTest { cache.listAll() }

                            it("returns the details from that file") {
                                assertThat(
                                    cachedRepos,
                                    equalTo(
                                        setOf(
                                            CachedGitRepository(GitRepositoryReference("https://github.com/me/my-bundle.git", "my-tag"), ZonedDateTime.of(2020, 7, 5, 1, 2, 3, 456789012, ZoneOffset.UTC), null, infoPath),
                                        ),
                                    ),
                                )
                            }
                        }

                        given("the Git cache directory contains a corresponding working copy directory") {
                            val workingCopyPath by createForEachTest { fileSystem.getPath("/some/.batect/dir/incl/some-key") }
                            beforeEachTest { Files.createDirectories(workingCopyPath) }

                            val cachedRepos by runForEachTest { cache.listAll() }

                            it("returns the details from the info file and the directory") {
                                assertThat(
                                    cachedRepos,
                                    equalTo(
                                        setOf(
                                            CachedGitRepository(GitRepositoryReference("https://github.com/me/my-bundle.git", "my-tag"), ZonedDateTime.of(2020, 7, 5, 1, 2, 3, 456789012, ZoneOffset.UTC), workingCopyPath, infoPath),
                                        ),
                                    ),
                                )
                            }
                        }

                        given("the Git cache directory contains another file") {
                            beforeEachTest {
                                Files.write(fileSystem.getPath("/some/.batect/dir/incl/something-else.txt"), "Hello".toByteArray(Charsets.UTF_8))
                            }

                            val cachedRepos by runForEachTest { cache.listAll() }

                            it("returns the details from just the info file") {
                                assertThat(
                                    cachedRepos,
                                    equalTo(
                                        setOf(
                                            CachedGitRepository(GitRepositoryReference("https://github.com/me/my-bundle.git", "my-tag"), ZonedDateTime.of(2020, 7, 5, 1, 2, 3, 456789012, ZoneOffset.UTC), null, infoPath),
                                        ),
                                    ),
                                )
                            }
                        }
                    }

                    given("the info file is not valid JSON") {
                        beforeEachTest {
                            Files.write(infoPath, "blah".toByteArray(Charsets.UTF_8))
                        }

                        it("throws an appropriate exception") {
                            assertThat({ cache.listAll() }, throws<GitRepositoryCacheException>(withMessage("The file /some/.batect/dir/incl/some-key.json could not be loaded: Element class kotlinx.serialization.json.JsonLiteral is not a JsonObject")))
                        }
                    }

                    given("the info file is missing an attribute") {
                        beforeEachTest {
                            Files.write(
                                infoPath,
                                """
                                    |{
                                    |    "repo": {
                                    |        "ref": "my-tag"
                                    |    },
                                    |    "lastUsed": "2020-07-05T01:02:03.456789012Z"
                                    |}
                                """.trimMargin().toByteArray(Charsets.UTF_8),
                            )
                        }

                        it("throws an appropriate exception") {
                            assertThat({ cache.listAll() }, throws<GitRepositoryCacheException>(withMessage("The file /some/.batect/dir/incl/some-key.json could not be loaded: Key remote is missing in the map.")))
                        }
                    }
                }

                given("the Git cache directory contains multiple info files") {
                    val infoPath1 by createForEachTest { fileSystem.getPath("/some/.batect/dir/incl/some-key-1.json") }
                    val infoPath2 by createForEachTest { fileSystem.getPath("/some/.batect/dir/incl/some-key-2.json") }

                    beforeEachTest {
                        Files.write(
                            infoPath1,
                            """
                                |{
                                |    "repo": {
                                |        "remote": "https://github.com/me/my-bundle-1.git",
                                |        "ref": "my-tag"
                                |    },
                                |    "lastUsed": "2020-07-05T01:02:03.456789012Z"
                                |}
                            """.trimMargin().toByteArray(Charsets.UTF_8),
                        )

                        Files.write(
                            infoPath2,
                            """
                                |{
                                |    "repo": {
                                |        "remote": "https://github.com/me/my-bundle-2.git",
                                |        "ref": "my-tag"
                                |    },
                                |    "lastUsed": "2020-07-05T01:02:03.456789012Z"
                                |}
                            """.trimMargin().toByteArray(Charsets.UTF_8),
                        )
                    }

                    given("neither has a corresponding working copy directory") {
                        val cachedRepos by runForEachTest { cache.listAll() }

                        it("returns the details from both info files") {
                            assertThat(
                                cachedRepos,
                                equalTo(
                                    setOf(
                                        CachedGitRepository(GitRepositoryReference("https://github.com/me/my-bundle-1.git", "my-tag"), ZonedDateTime.of(2020, 7, 5, 1, 2, 3, 456789012, ZoneOffset.UTC), null, infoPath1),
                                        CachedGitRepository(GitRepositoryReference("https://github.com/me/my-bundle-2.git", "my-tag"), ZonedDateTime.of(2020, 7, 5, 1, 2, 3, 456789012, ZoneOffset.UTC), null, infoPath2),
                                    ),
                                ),
                            )
                        }
                    }

                    given("both have a corresponding working copy directory") {
                        val workingCopyPath1 by createForEachTest { fileSystem.getPath("/some/.batect/dir/incl/some-key-1") }
                        val workingCopyPath2 by createForEachTest { fileSystem.getPath("/some/.batect/dir/incl/some-key-2") }

                        beforeEachTest {
                            Files.createDirectories(workingCopyPath1)
                            Files.createDirectories(workingCopyPath2)
                        }

                        val cachedRepos by runForEachTest { cache.listAll() }

                        it("returns the details from both info files with the corresponding working copy directories") {
                            assertThat(
                                cachedRepos,
                                equalTo(
                                    setOf(
                                        CachedGitRepository(GitRepositoryReference("https://github.com/me/my-bundle-1.git", "my-tag"), ZonedDateTime.of(2020, 7, 5, 1, 2, 3, 456789012, ZoneOffset.UTC), workingCopyPath1, infoPath1),
                                        CachedGitRepository(GitRepositoryReference("https://github.com/me/my-bundle-2.git", "my-tag"), ZonedDateTime.of(2020, 7, 5, 1, 2, 3, 456789012, ZoneOffset.UTC), workingCopyPath2, infoPath2),
                                    ),
                                ),
                            )
                        }
                    }
                }

                given("the Git cache directory contains a directory but no corresponding info file") {
                    val workingCopyPath by createForEachTest { fileSystem.getPath("/some/.batect/dir/incl/some-key") }

                    beforeEachTest { Files.createDirectories(workingCopyPath) }

                    val cachedRepos by runForEachTest { cache.listAll() }

                    // Why not return a result with no information?
                    // The primary use of listAll() is to work out which repos haven't been used recently and can be deleted.
                    // If we're cloning a repository at the same time that listAll() is called, we might pick up that incomplete clone
                    // (the info file is only created once the clone is completed), and then try to delete it while we're in the process of cloning it.
                    it("returns an empty set of cached repositories") {
                        assertThat(cachedRepos, isEmpty)
                    }
                }
            }
        }

        describe("deleting a repository") {
            val infoPath by createForEachTest { fileSystem.getPath("/some/.batect/dir/incl/some-key.json") }
            val workingCopyPath by createForEachTest { fileSystem.getPath("/some/.batect/dir/incl/some-key") }

            beforeEachTest { Files.createDirectories(fileSystem.getPath("/some/.batect/dir/incl")) }

            fun createInfoFile() {
                Files.createFile(infoPath)
            }

            fun createWorkingCopyDirectory() {
                Files.createDirectories(workingCopyPath)
                Files.createFile(workingCopyPath.resolve("some-file"))
            }

            given("the request contains both a working copy path and an info file path") {
                val repo by createForEachTest { CachedGitRepository(GitRepositoryReference("https://github.com/me/my-bundle.git", "my-tag"), ZonedDateTime.of(2020, 7, 5, 1, 2, 3, 456789012, ZoneOffset.UTC), workingCopyPath, infoPath) }

                given("neither the working copy directory and the info file exist") {
                    it("does not throw") {
                        assertThat({ cache.delete(repo) }, doesNotThrow())
                    }
                }

                given("only the info file exists") {
                    beforeEachTest {
                        createInfoFile()

                        cache.delete(repo)
                    }

                    it("deletes the info file") {
                        assertThat(Files.exists(infoPath), equalTo(false))
                    }
                }

                given("only the working copy directory exists") {
                    beforeEachTest {
                        createWorkingCopyDirectory()

                        cache.delete(repo)
                    }

                    it("deletes the working copy") {
                        assertThat(Files.exists(workingCopyPath), equalTo(false))
                    }
                }

                given("both the working copy directory and the info file exist") {
                    beforeEachTest {
                        createInfoFile()
                        createWorkingCopyDirectory()

                        cache.delete(repo)
                    }

                    it("deletes the info file") {
                        assertThat(Files.exists(infoPath), equalTo(false))
                    }

                    it("deletes the working copy") {
                        assertThat(Files.exists(workingCopyPath), equalTo(false))
                    }
                }
            }

            given("the request contains only an info file path") {
                val repo by createForEachTest { CachedGitRepository(GitRepositoryReference("https://github.com/me/my-bundle.git", "my-tag"), ZonedDateTime.of(2020, 7, 5, 1, 2, 3, 456789012, ZoneOffset.UTC), null, infoPath) }

                given("the info file exists") {
                    beforeEachTest {
                        createInfoFile()
                        cache.delete(repo)
                    }

                    it("deletes the info file") {
                        assertThat(Files.exists(infoPath), equalTo(false))
                    }
                }

                given("the info file does not exist") {
                    it("does not throw") {
                        assertThat({ cache.delete(repo) }, doesNotThrow())
                    }
                }
            }
        }
    }
})
