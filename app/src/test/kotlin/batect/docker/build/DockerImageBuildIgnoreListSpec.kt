/*
   Copyright 2017-2018 Charles Korn.

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

import batect.docker.DockerException
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

        describe("parsing a .dockerignore file") {
            val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
            val path by createForEachTest { fileSystem.getPath("/some-dir/.dockerignore") }

            beforeEachTest {
                Files.createDirectories(path.parent)
            }

            given("the file does not exist") {
                on("parsing the file") {
                    val ignoreList = DockerImageBuildIgnoreList.parse(path)

                    it("returns an empty list") {
                        assertThat(ignoreList, equalTo(DockerImageBuildIgnoreList(emptyList())))
                    }
                }
            }

            given("the file is empty") {
                beforeEachTest {
                    Files.createFile(path)
                }

                on("parsing the file") {
                    val ignoreList = DockerImageBuildIgnoreList.parse(path)

                    it("returns an empty list") {
                        assertThat(ignoreList, equalTo(DockerImageBuildIgnoreList(emptyList())))
                    }
                }
            }

            given("the file contains a single blank line") {
                beforeEachTest {
                    Files.write(path, listOf(""))
                }

                on("parsing the file") {
                    val ignoreList = DockerImageBuildIgnoreList.parse(path)

                    it("returns an empty list") {
                        assertThat(ignoreList, equalTo(DockerImageBuildIgnoreList(emptyList())))
                    }
                }
            }

            given("the file contains a single comment") {
                beforeEachTest {
                    Files.write(path, listOf("# this is a comment"))
                }

                on("parsing the file") {
                    val ignoreList = DockerImageBuildIgnoreList.parse(path)

                    it("returns an empty list") {
                        assertThat(ignoreList, equalTo(DockerImageBuildIgnoreList(emptyList())))
                    }
                }
            }

            given("the file contains a single exclusion pattern") {
                beforeEachTest {
                    Files.write(path, listOf("path/thing.go"))
                }

                on("parsing the file") {
                    val ignoreList = DockerImageBuildIgnoreList.parse(path)

                    it("returns a list with that pattern") {
                        assertThat(ignoreList, equalTo(DockerImageBuildIgnoreList(listOf(
                            DockerImageBuildIgnoreEntry("path/thing.go", false)
                        ))))
                    }
                }
            }

            given("the file contains a single inclusion pattern") {
                beforeEachTest {
                    Files.write(path, listOf("!path/thing.go"))
                }

                on("parsing the file") {
                    val ignoreList = DockerImageBuildIgnoreList.parse(path)

                    it("returns a list with that pattern") {
                        assertThat(ignoreList, equalTo(DockerImageBuildIgnoreList(listOf(
                            DockerImageBuildIgnoreEntry("path/thing.go", true)
                        ))))
                    }
                }
            }

            given("the file contains a single exclusion pattern with leading whitespace") {
                beforeEachTest {
                    Files.write(path, listOf("   path/thing.go"))
                }

                on("parsing the file") {
                    val ignoreList = DockerImageBuildIgnoreList.parse(path)

                    it("returns a list with that pattern with the whitespace removed") {
                        assertThat(ignoreList, equalTo(DockerImageBuildIgnoreList(listOf(
                            DockerImageBuildIgnoreEntry("path/thing.go", false)
                        ))))
                    }
                }
            }

            given("the file contains a single exclusion pattern with leading whitespace") {
                beforeEachTest {
                    Files.write(path, listOf("path/thing.go   "))
                }

                on("parsing the file") {
                    val ignoreList = DockerImageBuildIgnoreList.parse(path)

                    it("returns a list with that pattern with the whitespace removed") {
                        assertThat(ignoreList, equalTo(DockerImageBuildIgnoreList(listOf(
                            DockerImageBuildIgnoreEntry("path/thing.go", false)
                        ))))
                    }
                }
            }

            given("the file contains a single line with just whitespace") {
                beforeEachTest {
                    Files.write(path, listOf("   "))
                }

                on("parsing the file") {
                    val ignoreList = DockerImageBuildIgnoreList.parse(path)

                    it("returns an empty list") {
                        assertThat(ignoreList, equalTo(DockerImageBuildIgnoreList(emptyList())))
                    }
                }
            }

            given("the file contains multiple patterns") {
                beforeEachTest {
                    Files.write(path, listOf(
                        "path/thing.go",
                        "something/else",
                        "!not/this"
                    ))
                }

                on("parsing the file") {
                    val ignoreList = DockerImageBuildIgnoreList.parse(path)

                    it("returns a list with those patterns") {
                        assertThat(ignoreList, equalTo(DockerImageBuildIgnoreList(listOf(
                            DockerImageBuildIgnoreEntry("path/thing.go", false),
                            DockerImageBuildIgnoreEntry("something/else", false),
                            DockerImageBuildIgnoreEntry("not/this", true)
                        ))))
                    }
                }
            }

            given("the file contains the pattern '.'") {
                beforeEachTest {
                    Files.write(path, listOf("."))
                }

                on("parsing the file") {
                    val ignoreList = DockerImageBuildIgnoreList.parse(path)

                    it("ignores the pattern") {
                        assertThat(ignoreList, equalTo(DockerImageBuildIgnoreList(emptyList())))
                    }
                }
            }

            given("the file contains the pattern '!'") {
                beforeEachTest {
                    Files.write(path, listOf("!"))
                }

                on("parsing the file") {
                    it("throws an appropriate exception") {
                        assertThat({ DockerImageBuildIgnoreList.parse(path) }, throws<DockerException>(withMessage("The .dockerignore pattern '!' is invalid.")))
                    }
                }
            }

            given("the file contains an exclusion pattern with whitespace after the '!'") {
                beforeEachTest {
                    Files.write(path, listOf("!   path/thing.go"))
                }

                on("parsing the file") {
                    val ignoreList = DockerImageBuildIgnoreList.parse(path)

                    it("returns a list with that pattern with the whitespace removed") {
                        assertThat(ignoreList, equalTo(DockerImageBuildIgnoreList(listOf(
                            DockerImageBuildIgnoreEntry("path/thing.go", true)
                        ))))
                    }
                }
            }

            given("the file contains patterns with leading slashes") {
                beforeEachTest {
                    Files.write(path, listOf(
                        "/path/thing.go",
                        "!/path/otherthing.go"
                    ))
                }

                on("parsing the file") {
                    val ignoreList = DockerImageBuildIgnoreList.parse(path)

                    it("returns a list with those patterns with the slashes removed") {
                        assertThat(ignoreList, equalTo(DockerImageBuildIgnoreList(listOf(
                            DockerImageBuildIgnoreEntry("path/thing.go", false),
                            DockerImageBuildIgnoreEntry("path/otherthing.go", true)
                        ))))
                    }
                }
            }

            // The following tests are based on https://golang.org/src/path/filepath/path_test.go, which is what the Docker CLI uses.
            given("the file contains patterns with multiple consecutive slashes") {
                beforeEachTest {
                    Files.write(path, listOf(
                        "path//other///thing.go",
                        "!path//otherthing.go"
                    ))
                }

                on("parsing the file") {
                    val ignoreList = DockerImageBuildIgnoreList.parse(path)

                    it("returns a list with those patterns with the extra slashes removed") {
                        assertThat(ignoreList, equalTo(DockerImageBuildIgnoreList(listOf(
                            DockerImageBuildIgnoreEntry("path/other/thing.go", false),
                            DockerImageBuildIgnoreEntry("path/otherthing.go", true)
                        ))))
                    }
                }
            }

            given("the file contains patterns with references to parent directories") {
                mapOf(
                    ".." to "..",
                    "../.." to "../..",
                    "../../abc" to "../../abc",
                    "../" to "..",
                    "../../" to "../..",
                    "abc/def/ghi/../jkl" to "abc/def/jkl",
                    "abc/def/../ghi/../jkl" to "abc/jkl",
                    "abc/def/.." to "abc",
                    "abc/def/../.." to ".",
                    "/abc/def/../.." to ".",
                    "abc/def/../../.." to "..",
                    "/abc/def/../../.." to ".",
                    "abc/def/../../../ghi/jkl/../../../mno" to "../../mno",
                    "/../abc" to "abc"
                ).forEach { patternInFile, expectedCleanedPattern ->
                    given("the pattern is '$patternInFile'") {
                        beforeEachTest {
                            Files.write(path, listOf(patternInFile))
                        }

                        on("parsing the file") {
                            val ignoreList = DockerImageBuildIgnoreList.parse(path)

                            it("returns a list with the pattern simplified where possible") {
                                assertThat(ignoreList, equalTo(DockerImageBuildIgnoreList(listOf(
                                    DockerImageBuildIgnoreEntry(expectedCleanedPattern, false)
                                ))))
                            }
                        }
                    }
                }
            }

            given("the file contains patterns with references to the current directory") {
                mapOf(
                    "abc/./def" to "abc/def",
                    "/./abc/def" to "abc/def",
                    "abc/." to "abc"
                ).forEach { patternInFile, expectedCleanedPattern ->
                    given("the pattern is '$patternInFile'") {
                        beforeEachTest {
                            Files.write(path, listOf(patternInFile))
                        }

                        on("parsing the file") {
                            val ignoreList = DockerImageBuildIgnoreList.parse(path)

                            it("returns a list with the pattern simplified where possible") {
                                assertThat(ignoreList, equalTo(DockerImageBuildIgnoreList(listOf(
                                    DockerImageBuildIgnoreEntry(expectedCleanedPattern, false)
                                ))))
                            }
                        }
                    }
                }
            }

            given("the file contains patterns with references to both the current directory and parent directories") {
                mapOf(
                    "abc/./../def" to "def",
                    "abc//./../def" to "def",
                    "abc/../../././../def" to "../../def"
                ).forEach { patternInFile, expectedCleanedPattern ->
                    given("the pattern is '$patternInFile'") {
                        beforeEachTest {
                            Files.write(path, listOf(patternInFile))
                        }

                        on("parsing the file") {
                            val ignoreList = DockerImageBuildIgnoreList.parse(path)

                            it("returns a list with the pattern simplified where possible") {
                                assertThat(ignoreList, equalTo(DockerImageBuildIgnoreList(listOf(
                                    DockerImageBuildIgnoreEntry(expectedCleanedPattern, false)
                                ))))
                            }
                        }
                    }
                }
            }
        }
    }
})
