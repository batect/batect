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

package batect.config.includes

import batect.config.FileInclude
import batect.config.GitInclude
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.runForEachTest
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object IncludeResolverSpec : Spek({
    describe("an include resolver") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val gitRepositoryCache by createForEachTest { mock<GitRepositoryCache>() }
        val resolver by createForEachTest { IncludeResolver(gitRepositoryCache) }
        val listener by createForEachTest { mock<GitRepositoryCacheNotificationListener>() }

        val repositoryReference = GitRepositoryReference("https://myrepo.com/bundles/bundle.git", "v1.2.3")

        beforeEachTest {
            whenever(gitRepositoryCache.ensureCached(repositoryReference, listener)).doReturn(fileSystem.getPath("/repos/abc123"))
        }

        context("resolving the path to an include") {
            given("a file include") {
                val include by createForEachTest { FileInclude(fileSystem.getPath("/some/file.yml")) }

                it("returns the path from the include") {
                    assertThat(resolver.resolve(include, listener), equalTo(include.path))
                }
            }

            given("a Git include") {
                val include by createForEachTest { GitInclude(repositoryReference.remote, repositoryReference.ref, "file-1.yml") }

                given("no includes for this repository have been resolved previously") {
                    val path by runForEachTest { resolver.resolve(include, listener) }

                    it("returns the included path resolved relative to the root path from the cache") {
                        assertThat(path, equalTo(fileSystem.getPath("/repos/abc123/file-1.yml")))
                    }
                }

                given("the same include has been resolved previously") {
                    beforeEachTest { resolver.resolve(include, listener) }

                    val path by runForEachTest { resolver.resolve(include, listener) }

                    it("returns the included path resolved relative to the root path from the cache") {
                        assertThat(path, equalTo(fileSystem.getPath("/repos/abc123/file-1.yml")))
                    }

                    it("only requests the repository cache cache the repository once") {
                        verify(gitRepositoryCache, times(1)).ensureCached(repositoryReference, listener)
                    }
                }

                given("the same repo has been resolved previously with a different include path") {
                    beforeEachTest { resolver.resolve(include.copy(path = "file-2.yml"), listener) }

                    val path by runForEachTest { resolver.resolve(include, listener) }

                    it("returns the included path resolved relative to the root path from the cache") {
                        assertThat(path, equalTo(fileSystem.getPath("/repos/abc123/file-1.yml")))
                    }

                    it("only requests the repository cache cache the repository once") {
                        verify(gitRepositoryCache, times(1)).ensureCached(repositoryReference, listener)
                    }
                }

                given("the include is for a subdirectory") {
                    val path by runForEachTest { resolver.resolve(include.copy(path = "subdir/file-1.yml"), listener) }

                    it("returns the included path resolved relative to the root path from the cache") {
                        assertThat(path, equalTo(fileSystem.getPath("/repos/abc123/subdir/file-1.yml")))
                    }
                }
            }
        }

        context("getting the root path for a Git repository reference") {
            given("the root path for this repository has not been requested previously") {
                val rootPath by runForEachTest { resolver.rootPathFor(repositoryReference, listener) }

                it("returns the path from the repository cache") {
                    assertThat(rootPath, equalTo(fileSystem.getPath("/repos/abc123")))
                }
            }

            given("the root path for this repository has been requested previously") {
                beforeEachTest { resolver.rootPathFor(repositoryReference, listener) }

                val rootPath by runForEachTest { resolver.rootPathFor(repositoryReference, listener) }

                it("returns the path from the repository cache") {
                    assertThat(rootPath, equalTo(fileSystem.getPath("/repos/abc123")))
                }

                it("only requests the repository cache cache the repository once") {
                    verify(gitRepositoryCache, times(1)).ensureCached(repositoryReference, listener)
                }
            }
        }
    }
})
