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

import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

// These tests are based on https://github.com/docker/engine/blob/master/pkg/fileutils/fileutils_test.go
object DockerImageBuildIgnoreListSpec : Spek({
    describe("a Docker image build ignore list") {
        describe("determining if a file should be included in the build context") {
            given("an empty list of ignore patterns") {
                val ignoreList = DockerImageBuildIgnoreList(emptyList())

                it("includes all files") {
                    assertThat(ignoreList.shouldIncludeInContext("/some/path"), equalTo(true))
                }
            }

            given("a list of patterns that ignores the .dockerignore file") {
                val ignoreList = DockerImageBuildIgnoreList(listOf(DockerImageBuildIgnoreEntry(".dockerignore", false)))

                it("still includes the .dockerignore file") {
                    assertThat(ignoreList.shouldIncludeInContext(".dockerignore"), equalTo(true))
                }
            }

            given("a list of patterns that ignores the Dockerfile file") {
                val ignoreList = DockerImageBuildIgnoreList(listOf(DockerImageBuildIgnoreEntry("Dockerfile", false)))

                it("still includes the Dockerfile file") {
                    assertThat(ignoreList.shouldIncludeInContext("Dockerfile"), equalTo(true))
                }
            }

            given("a list of patterns that ignores some files") {
                val ignoreList = DockerImageBuildIgnoreList(listOf(DockerImageBuildIgnoreEntry("thing.go", false)))

                it("excludes files matching the pattern") {
                    assertThat(ignoreList.shouldIncludeInContext("thing.go"), equalTo(false))
                }

                it("includes files not matching the pattern") {
                    assertThat(ignoreList.shouldIncludeInContext("somethingelse.go"), equalTo(true))
                }
            }

            given("a list of patterns that includes some files") {
                val ignoreList = DockerImageBuildIgnoreList(listOf(DockerImageBuildIgnoreEntry("thing.go", true)))

                it("includes files matching the pattern") {
                    assertThat(ignoreList.shouldIncludeInContext("thing.go"), equalTo(true))
                }

                it("includes files not matching the pattern") {
                    assertThat(ignoreList.shouldIncludeInContext("somethingelse.go"), equalTo(true))
                }
            }

            given("a list of patterns that excludes some files and has an exception for other files") {
                val ignoreList = DockerImageBuildIgnoreList(listOf(
                    DockerImageBuildIgnoreEntry("docs", false),
                    DockerImageBuildIgnoreEntry("docs/README.md", true)
                ))

                it("includes files that match the exception") {
                    assertThat(ignoreList.shouldIncludeInContext("docs/README.md"), equalTo(true))
                }

                it("excludes files that match the first rule") {
                    assertThat(ignoreList.shouldIncludeInContext("docs/thing"), equalTo(false))
                }

                it("includes files that match neither rule") {
                    assertThat(ignoreList.shouldIncludeInContext("thing"), equalTo(true))
                }
            }
        }
    }
})
