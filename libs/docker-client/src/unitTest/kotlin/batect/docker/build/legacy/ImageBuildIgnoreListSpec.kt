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

package batect.docker.build.legacy

import batect.docker.build.ImageBuildIgnoreEntry
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

// These tests are based on https://github.com/docker/engine/blob/master/pkg/fileutils/fileutils_test.go
object ImageBuildIgnoreListSpec : Spek({
    describe("a Docker image build ignore list") {
        val unixFileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val windowsFileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.windows()) }

        describe("determining if a file should be included in the build context") {
            given("an empty list of ignore patterns") {
                val ignoreList = ImageBuildIgnoreList(emptyList())

                it("includes all files with Unix-style paths") {
                    assertThat(ignoreList.shouldIncludeInContext(unixFileSystem.getPath("/some/path"), "Dockerfile"), equalTo(true))
                }

                it("includes all files with Windows-style paths") {
                    assertThat(ignoreList.shouldIncludeInContext(windowsFileSystem.getPath("some\\path"), "Dockerfile"), equalTo(true))
                }
            }

            given("a list of patterns that ignores the .dockerignore file") {
                val ignoreList = ImageBuildIgnoreList(listOf(ImageBuildIgnoreEntry(".dockerignore", false)))

                it("still includes the .dockerignore file") {
                    assertThat(ignoreList.shouldIncludeInContext(unixFileSystem.getPath(".dockerignore"), "Dockerfile"), equalTo(true))
                }
            }

            given("a list of patterns that ignores the Dockerfile file") {
                val ignoreList = ImageBuildIgnoreList(listOf(ImageBuildIgnoreEntry("Dockerfile", false)))

                it("still includes the Dockerfile file") {
                    assertThat(ignoreList.shouldIncludeInContext(unixFileSystem.getPath("Dockerfile"), "Dockerfile"), equalTo(true))
                }
            }

            given("a list of patterns that ignores a custom Dockerfile and the standard Dockerfile file") {
                val ignoreList = ImageBuildIgnoreList(
                    listOf(
                        ImageBuildIgnoreEntry("CustomDockerfile", false),
                        ImageBuildIgnoreEntry("Dockerfile", false)
                    )
                )

                it("still includes the custom Dockerfile file") {
                    assertThat(ignoreList.shouldIncludeInContext(unixFileSystem.getPath("CustomDockerfile"), "CustomDockerfile"), equalTo(true))
                }

                it("does not include a file called Dockerfile") {
                    assertThat(ignoreList.shouldIncludeInContext(unixFileSystem.getPath("Dockerfile"), "CustomDockerfile"), equalTo(false))
                }
            }

            given("a list of patterns that ignores a custom Dockerfile in a subdirectory") {
                val ignoreList = ImageBuildIgnoreList(
                    listOf(
                        ImageBuildIgnoreEntry("Dockerfiles/CustomDockerfile", false)
                    )
                )

                it("still includes the custom Dockerfile file when given as a Unix-style path") {
                    assertThat(ignoreList.shouldIncludeInContext(unixFileSystem.getPath("Dockerfiles/CustomDockerfile"), "Dockerfiles/CustomDockerfile"), equalTo(true))
                }

                it("still includes the custom Dockerfile file when given as a Windows-style path") {
                    assertThat(ignoreList.shouldIncludeInContext(windowsFileSystem.getPath("Dockerfiles\\CustomDockerfile"), "Dockerfiles/CustomDockerfile"), equalTo(true))
                }
            }

            given("a list of patterns that ignores some files") {
                val ignoreList = ImageBuildIgnoreList(listOf(ImageBuildIgnoreEntry("thing.go", false)))

                it("excludes files matching the pattern") {
                    assertThat(ignoreList.shouldIncludeInContext(unixFileSystem.getPath("thing.go"), "Dockerfile"), equalTo(false))
                }

                it("includes files not matching the pattern") {
                    assertThat(ignoreList.shouldIncludeInContext(unixFileSystem.getPath("somethingelse.go"), "Dockerfile"), equalTo(true))
                }
            }

            given("a list of patterns that includes some files") {
                val ignoreList = ImageBuildIgnoreList(listOf(ImageBuildIgnoreEntry("thing.go", true)))

                it("includes files matching the pattern") {
                    assertThat(ignoreList.shouldIncludeInContext(unixFileSystem.getPath("thing.go"), "Dockerfile"), equalTo(true))
                }

                it("includes files not matching the pattern") {
                    assertThat(ignoreList.shouldIncludeInContext(unixFileSystem.getPath("somethingelse.go"), "Dockerfile"), equalTo(true))
                }
            }

            given("a list of patterns that excludes some files and has an exception for other files") {
                val ignoreList = ImageBuildIgnoreList(
                    listOf(
                        ImageBuildIgnoreEntry("docs", false),
                        ImageBuildIgnoreEntry("docs/README.md", true)
                    )
                )

                it("includes files with Unix-style paths that match the exception") {
                    assertThat(ignoreList.shouldIncludeInContext(unixFileSystem.getPath("docs/README.md"), "Dockerfile"), equalTo(true))
                }

                it("excludes files with Unix-style paths that match the first rule") {
                    assertThat(ignoreList.shouldIncludeInContext(unixFileSystem.getPath("docs/thing"), "Dockerfile"), equalTo(false))
                }

                it("includes files with Windows-style paths that match the exception") {
                    assertThat(ignoreList.shouldIncludeInContext(windowsFileSystem.getPath("docs\\README.md"), "Dockerfile"), equalTo(true))
                }

                it("excludes files with Windows-style paths that match the first rule") {
                    assertThat(ignoreList.shouldIncludeInContext(windowsFileSystem.getPath("docs\\thing"), "Dockerfile"), equalTo(false))
                }

                it("includes files that match neither rule") {
                    assertThat(ignoreList.shouldIncludeInContext(unixFileSystem.getPath("thing"), "Dockerfile"), equalTo(true))
                }
            }
        }
    }
})
