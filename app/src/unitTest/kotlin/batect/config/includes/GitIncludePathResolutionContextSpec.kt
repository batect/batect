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

package batect.config.includes

import batect.config.GitInclude
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object GitIncludePathResolutionContextSpec : Spek({
    describe("a Git include path resolution context") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val repoRootDirectory by createForEachTest { fileSystem.getPath("/git/some-repo") }
        val include by createForEachTest { GitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3") }
        val context by createForEachTest { GitIncludePathResolutionContext(fileSystem.getPath("/some-dir"), repoRootDirectory, include) }

        given("the path is at the root of the repository") {
            val resolvedPath by createForEachTest { fileSystem.getPath("/git/some-repo/the-thing") }

            it("returns a description describing the path relative to the root of the repository") {
                assertThat(context.getResolutionDescription(resolvedPath), equalTo("'the-thing' from https://myrepo.com/bundles/bundle.git@v1.2.3"))
            }

            it("returns the same description when formatting the path for display") {
                assertThat(context.getPathForDisplay(resolvedPath), equalTo("'the-thing' from https://myrepo.com/bundles/bundle.git@v1.2.3"))
            }
        }

        given("the path is in a subdirectory of the repository") {
            val resolvedPath by createForEachTest { fileSystem.getPath("/git/some-repo/some-directory/the-thing") }

            it("returns a description describing the path relative to the root of the repository") {
                assertThat(context.getResolutionDescription(resolvedPath), equalTo("'some-directory/the-thing' from https://myrepo.com/bundles/bundle.git@v1.2.3"))
            }

            it("returns the same description when formatting the path for display") {
                assertThat(context.getPathForDisplay(resolvedPath), equalTo("'some-directory/the-thing' from https://myrepo.com/bundles/bundle.git@v1.2.3"))
            }
        }

        given("the path is outside the repository") {
            val resolvedPath by createForEachTest { fileSystem.getPath("/git/some-thing") }

            it("returns a description describing the absolute path") {
                assertThat(context.getResolutionDescription(resolvedPath), equalTo("resolved to '/git/some-thing'"))
            }

            it("returns the absolute path in single quotes when formatting the path for display") {
                assertThat(context.getPathForDisplay(resolvedPath), equalTo("'/git/some-thing'"))
            }
        }
    }
})
