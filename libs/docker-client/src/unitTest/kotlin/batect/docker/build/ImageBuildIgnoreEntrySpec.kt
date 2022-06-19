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

package batect.docker.build

import batect.docker.DockerException
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ImageBuildIgnoreEntrySpec : Spek({
    describe("a Docker image build ignore entry") {
        describe("matching patterns") {
            given("a wildcard pattern") {
                val entry = ImageBuildIgnoreEntry("*", false)

                it("should match anything") {
                    assertThat(entry.matches("fileutils.go"), equalTo(MatchResult.MatchedExclude))
                }
            }

            given("an inverted wildcard pattern") {
                val entry = ImageBuildIgnoreEntry("*", true)

                it("should match nothing") {
                    assertThat(entry.matches("fileutils.go"), equalTo(MatchResult.MatchedInclude))
                }
            }

            given("a partial wildcard pattern") {
                val entry = ImageBuildIgnoreEntry("*.go", false)

                it("should match anything that ends in the same suffix") {
                    assertThat(entry.matches("fileutils.go"), equalTo(MatchResult.MatchedExclude))
                }

                it("should match anything that just has the same suffix") {
                    assertThat(entry.matches(".go"), equalTo(MatchResult.MatchedExclude))
                }

                it("should not match anything that does not have the same suffix") {
                    assertThat(entry.matches("something.else"), equalTo(MatchResult.NoMatch))
                }
            }

            given("a pattern that matches a directory") {
                val entry = ImageBuildIgnoreEntry("docs", false)

                it("should match that directory") {
                    assertThat(entry.matches("docs"), equalTo(MatchResult.MatchedExclude))
                }

                it("should match anything inside that directory") {
                    assertThat(entry.matches("docs/README.md"), equalTo(MatchResult.MatchedExclude))
                }

                it("should not match a different directory") {
                    assertThat(entry.matches("docs2"), equalTo(MatchResult.NoMatch))
                }

                it("should not match anything in a different directory") {
                    assertThat(entry.matches("docs2/README.md"), equalTo(MatchResult.NoMatch))
                }
            }

            given("a pattern that matches a directory with a trailing slash") {
                val entry = ImageBuildIgnoreEntry("docs/", false)

                it("should match the directory, even if it is not given with a trailing slash") {
                    assertThat(entry.matches("docs"), equalTo(MatchResult.MatchedExclude))
                }

                it("should match anything inside that directory") {
                    assertThat(entry.matches("docs/README.md"), equalTo(MatchResult.MatchedExclude))
                }

                it("should not match a different directory") {
                    assertThat(entry.matches("docs2"), equalTo(MatchResult.NoMatch))
                }

                it("should not match anything in a different directory") {
                    assertThat(entry.matches("docs2/README.md"), equalTo(MatchResult.NoMatch))
                }
            }

            // See note at end of section at https://docs.docker.com/engine/reference/builder/#dockerignore-file:
            // "For historical reasons, the pattern . is ignored."
            given("a pattern consisting of a single dot") {
                val entry = ImageBuildIgnoreEntry(".", false)

                it("should not match anything") {
                    assertThat(entry.matches("fileutils.go"), equalTo(MatchResult.NoMatch))
                    assertThat(entry.matches("."), equalTo(MatchResult.NoMatch))
                }
            }

            listOf(
                "[",
                "[^",
                "[^]",
                "[^bc",
                "\\",
                "[\\",
                "[a-",
                "[a-]",
                "[-x]",
                "[a-b-c]",
                "[]",
                "[]a]",
                "[-]",
                "a["
            ).forEach { pattern ->
                given("the invalid pattern '$pattern'") {
                    it("should throw an appropriate exception when creating the entry") {
                        assertThat(
                            { ImageBuildIgnoreEntry(pattern, false) },
                            throws<DockerException>(withMessage("The .dockerignore pattern '$pattern' is invalid."))
                        )
                    }
                }
            }

            // These tests are all based on https://github.com/docker/engine/blob/master/pkg/fileutils/fileutils_test.go
            mapOf(
                "**" to mapOf(
                    "file" to true,
                    "file/" to true,
                    "/" to true,
                    "dir/file" to true,
                    "dir/file/" to true
                ),
                "**/" to mapOf(
                    "file" to true,
                    "file/" to true,
                    "/" to true,
                    "dir/file" to true,
                    "dir/file/" to true
                ),
                "**/**" to mapOf(
                    "dir/file" to true,
                    "dir/file/" to true
                ),
                "dir/**" to mapOf(
                    "dir/file" to true,
                    "dir/file/" to true,
                    "dir/dir2/file" to true,
                    "dir/dir2/file/" to true
                ),
                "**/dir2/*" to mapOf(
                    "dir/dir2/file" to true,
                    "dir/dir2/file/" to true,
                    "dir/dir2/dir3/file" to true,
                    "dir/dir2/dir3/file/" to true
                ),
                "**file" to mapOf(
                    "file" to true,
                    "dir/file" to true,
                    "dir/dir/file" to true
                ),
                "**/file" to mapOf(
                    "dir/file" to true,
                    "dir/dir/file" to true
                ),
                "**/file*" to mapOf(
                    "dir/dir/file" to true,
                    "dir/dir/file.txt" to true
                ),
                "**/file*txt" to mapOf(
                    "dir/dir/file.txt" to true
                ),
                "**/file*.txt" to mapOf(
                    "dir/dir/file.txt" to true,
                    "dir/dir/file.txt" to true
                ),
                "**/**/*.txt" to mapOf(
                    "dir/dir/file.txt" to true
                ),
                "**/**/*.txt2" to mapOf(
                    "dir/dir/file.txt" to false
                ),
                "**/*.txt" to mapOf(
                    "file.txt" to true
                ),
                "**/**/*.txt" to mapOf(
                    "file.txt" to true
                ),
                "a**/*.txt" to mapOf(
                    "a/file.txt" to true,
                    "a/dir/file.txt" to true,
                    "a/dir/dir/file.txt" to true
                ),
                "a/*.txt" to mapOf(
                    "a/dir/file.txt" to false,
                    "a/file.txt" to true
                ),
                "a/*.txt**" to mapOf(
                    "a/file.txt" to true
                ),
                "a[b-d]e" to mapOf(
                    "ae" to false,
                    "ace" to true,
                    "aae" to false
                ),
                "a[^b-d]e" to mapOf(
                    "aze" to true
                ),
                ".*" to mapOf(
                    ".foo" to true,
                    "foo" to false
                ),
                "abc.def" to mapOf(
                    "abcdef" to false,
                    "abc.def" to true,
                    "abcZdef" to false
                ),
                "abc?def" to mapOf(
                    "abcZdef" to true,
                    "abcdef" to false
                ),
                "a\\\\" to mapOf(
                    "a\\" to true
                ),
                "a\\b" to mapOf(
                    "ab" to true,
                    "ac" to false
                ),
                "**/foo/bar" to mapOf(
                    "foo/bar" to true,
                    "dir/foo/bar" to true,
                    "dir/dir2/foo/bar" to true
                ),
                "abc/**" to mapOf(
                    "abc" to false,
                    "abc/def" to true,
                    "abc/def/ghi" to true
                ),
                "**/.foo" to mapOf(
                    ".foo" to true,
                    "bar.foo" to false
                ),
                "*c" to mapOf(
                    "abc" to true
                ),
                "a*" to mapOf(
                    "a" to true,
                    "abc" to true,
                    "ab/c" to true
                ),
                "a*/b" to mapOf(
                    "abc/b" to true,
                    "a/c/b" to false
                ),
                "a*b*c*d*e*/f" to mapOf(
                    "axbxcxdxe/f" to true,
                    "axbxcxdxexxx/f" to true,
                    "axbxcxdxe/xxx/f" to false,
                    "axbxcxdxexxx/fff" to false
                ),
                "a*b?c*x" to mapOf(
                    "abxbbxdbxebxczzx" to true,
                    "abxbbxdbxebxczzy" to false
                ),
                "ab[c]" to mapOf(
                    "abc" to true
                ),
                "ab[b-d]" to mapOf(
                    "abc" to true
                ),
                "ab[e-g]" to mapOf(
                    "abc" to false
                ),
                "ab[^c]" to mapOf(
                    "abc" to false
                ),
                "ab[^b-d]" to mapOf(
                    "abc" to false
                ),
                "ab[^e-g]" to mapOf(
                    "abc" to true
                ),
                "a\\*b" to mapOf(
                    "a*b" to true,
                    "ab" to false
                ),
                "a?b" to mapOf(
                    "a☺b" to true
                ),
                "a[^a]b" to mapOf(
                    "a☺b" to true
                ),
                "a???b" to mapOf(
                    "a☺b" to false
                ),
                "a[^a][^a][^a]b" to mapOf(
                    "a☺b" to false
                ),
                "[a-ζ]*" to mapOf(
                    "α" to true
                ),
                "*[a-ζ]" to mapOf(
                    "A" to false
                ),
                "a?b" to mapOf(
                    "a/b" to false
                ),
                "a*b" to mapOf(
                    "a/b" to false
                ),
                "[\\]a]" to mapOf(
                    "]" to true
                ),
                "[\\-]" to mapOf(
                    "-" to true
                ),
                "[x\\-]" to mapOf(
                    "x" to true,
                    "-" to true,
                    "z" to false
                ),
                "[\\-x]" to mapOf(
                    "x" to true,
                    "-" to true,
                    "a" to false
                ),
                "*x" to mapOf(
                    "xxx" to true
                ),
                "  docs" to mapOf(
                    "docs" to true
                ),
                "docs  " to mapOf(
                    "docs" to true
                ),
                "docs/  " to mapOf(
                    "docs" to true,
                    "docs/thing" to true
                )
            ).forEach { (pattern, testCases) ->
                given("the pattern '$pattern'") {
                    val entry = ImageBuildIgnoreEntry(pattern, false)

                    testCases.forEach { (path, shouldMatch) ->
                        if (shouldMatch) {
                            it("should match the path '$path'") {
                                assertThat(entry.matches(path), equalTo(MatchResult.MatchedExclude))
                            }
                        } else {
                            it("should not match the path '$path'") {
                                assertThat(entry.matches(path), equalTo(MatchResult.NoMatch))
                            }
                        }
                    }
                }
            }
        }

        describe("cleaning patterns") {
            setOf(
                "abc",
                "abc/def",
                "a/b/c",
                ".",
                "..",
                "../..",
                "../../abc",
            ).forEach { pattern ->
                given("the already clean pattern '$pattern'") {
                    it("uses the pattern as-is") {
                        assertThat(ImageBuildIgnoreEntry.withUncleanPattern(pattern, false), equalTo(ImageBuildIgnoreEntry(pattern, false)))
                    }
                }
            }

            mapOf(
                "" to ".",
                "/" to ".",
                "abc/" to "abc",
                "abc/def/" to "abc/def",
                "a/b/c/" to "a/b/c",
                "./" to ".",
                "../" to "..",
                "../../" to "../..",
                "/abc/" to "abc",
                "/abc" to "abc",
                "abc//def//ghi" to "abc/def/ghi",
                "//abc" to "abc",
                "///abc" to "abc",
                "//abc//" to "abc",
                "abc//" to "abc",
                "abc/./def" to "abc/def",
                "/./abc/def" to "abc/def",
                "abc/." to "abc",
                "abc/def/ghi/../jkl" to "abc/def/jkl",
                "abc/def/../ghi/../jkl" to "abc/jkl",
                "abc/def/.." to "abc",
                "abc/def/../.." to ".",
                "/abc/def/../.." to ".",
                "abc/def/../../.." to "..",
                "/abc/def/../../.." to ".",
                "abc/def/../../../ghi/jkl/../../../mno" to "../../mno",
                "abc/./../def" to "def",
                "abc//./../def" to "def",
                "abc/../../././../def" to "../../def",
            ).forEach { (uncleanPattern, cleanPattern) ->
                given("the unclean pattern '$uncleanPattern'") {
                    it("cleans the pattern to '$cleanPattern'") {
                        assertThat(ImageBuildIgnoreEntry.withUncleanPattern(uncleanPattern, false), equalTo(ImageBuildIgnoreEntry(cleanPattern, false)))
                    }
                }
            }
        }
    }
})
