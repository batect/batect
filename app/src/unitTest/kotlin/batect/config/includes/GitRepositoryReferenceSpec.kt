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

package batect.config.includes

import batect.testutils.equalTo
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object GitRepositoryReferenceSpec : Spek({
    describe("a Git repository reference") {
        // What's the purpose of this key?
        // We need a stable way to identify different repository clones on disk that is filename safe.
        // Git repository URLs can have characters like '/' or ':' in them, which makes them unsuitable for use as a directory name,
        // and branches and tags can have anything allowed by https://git-scm.com/docs/git-check-ref-format.
        // Furthermore, we need this key to be reasonably short - otherwise there's a risk that we'll run into path length issues on Windows,
        // especially if the repository itself contains long paths.
        describe("generating cache keys") {
            describe("given an instance") {
                // These values have been carefully chosen so that if the ordinary (non-filename-safe) version of base64 is used, the
                // test below will fail.
                val reference = GitRepositoryReference("https://github.com/me/my-bundle.git", "my-branch-2")

                it("returns the truncated filename-safe base64-encoded version of the SHA512/224 hash of the repository URL and reference") {
                    // This should be equivalent to base64UrlSafe(sha51224("git https://github.com/me/my-bundle.git @my-branch-2".utf8Bytes)), without trailing '=' characters
                    assertThat(reference.cacheKey, equalTo("glpCHeGNoaQ-MPzEoOEycnITrl98zPLzpt_kFA"))
                }
            }

            describe("given two instances with the same repository and reference") {
                val reference1 = GitRepositoryReference("https://github.com/me/my-bundle.git", "my-branch")
                val reference2 = GitRepositoryReference("https://github.com/me/my-bundle.git", "my-branch")

                it("returns the same key for both") {
                    assertThat(reference1.cacheKey, equalTo(reference2.cacheKey))
                }
            }

            describe("given two instances with the same repository but different references") {
                val reference1 = GitRepositoryReference("https://github.com/me/my-bundle.git", "my-branch")
                val reference2 = GitRepositoryReference("https://github.com/me/my-bundle.git", "my-other-branch")

                it("returns different keys for both") {
                    assertThat(reference1.cacheKey, !equalTo(reference2.cacheKey))
                }
            }

            describe("given two instances with the same reference but different repositories") {
                val reference1 = GitRepositoryReference("https://github.com/me/my-bundle.git", "my-branch")
                val reference2 = GitRepositoryReference("https://github.com/me/my-other-bundle.git", "my-branch")

                it("returns different keys for both") {
                    assertThat(reference1.cacheKey, !equalTo(reference2.cacheKey))
                }
            }

            describe("given two instances with different references and repositories") {
                val reference1 = GitRepositoryReference("https://github.com/me/my-bundle.git", "my-branch")
                val reference2 = GitRepositoryReference("https://github.com/me/my-other-bundle.git", "my-other-branch")

                it("returns different keys for both") {
                    assertThat(reference1.cacheKey, !equalTo(reference2.cacheKey))
                }
            }
        }
    }
})
