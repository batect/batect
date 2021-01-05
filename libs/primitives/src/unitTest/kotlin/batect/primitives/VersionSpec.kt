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

package batect.primitives

import batect.testutils.given
import batect.testutils.on
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.greaterThan
import com.natpryce.hamkrest.lessThan
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object VersionSpec : Spek({
    describe("a version") {
        describe("comparing two versions") {
            val version = Version(1, 2, 3, "suffix")

            on("when the two versions are the same") {
                it("returns zero") {
                    assertThat(version.compareTo(Version(1, 2, 3, "suffix")), equalTo(0))
                }
            }

            given("when the major, minor and patch parts are the same") {
                on("when both versions have a suffix") {
                    val otherVersion = Version(1, 2, 3, "tuffix")

                    it("gives the version with the higher suffix in ASCII sort order as greater than the other version") {
                        assertThat(otherVersion, greaterThan(version))
                    }
                }

                on("when one version does not have a suffix and the other does") {
                    val versionWithoutSuffix = Version(1, 2, 3)

                    on("when comparing Docker versions") {
                        // This breaks the semver convention (things with suffixes should come before things without suffixes,
                        // eg. 1.0.0-alpha comes before 1.0.0), but Docker for Mac breaks this rule.
                        it("gives the version with no suffix as less than the other version") {
                            assertThat(versionWithoutSuffix.compareTo(version, VersionComparisonMode.DockerStyle), lessThan(0))
                        }
                    }

                    on("when comparing normal versions") {
                        it("gives the version with no suffix as greater than the other version") {
                            assertThat(versionWithoutSuffix, greaterThan(version))
                        }

                        it("gives the version with a suffix as less than the other version") {
                            assertThat(version, lessThan(versionWithoutSuffix))
                        }
                    }
                }
            }

            on("when the major and minor parts are the same") {
                val otherVersion = Version(1, 2, 4, "aaa")

                it("gives the version with the higher patch level as greater than the other version") {
                    assertThat(otherVersion, greaterThan(version))
                }
            }

            on("when the major parts are the same") {
                val otherVersion = Version(1, 3, 2, "aaa")

                it("gives the version with the higher minor version as greater than the other version") {
                    assertThat(otherVersion, greaterThan(version))
                }
            }

            on("when the major versions are different") {
                val otherVersion = Version(2, 1, 0, "aaa")

                it("gives the version with the higher major version as greater than the other version") {
                    assertThat(otherVersion, greaterThan(version))
                }
            }
        }

        describe("converting to string") {
            on("when only the major part is non-zero and the suffix is empty") {
                val version = Version(1, 0, 0, "")

                it("formats it in the format major.minor") {
                    assertThat(version.toString(), equalTo("1.0"))
                }
            }

            on("when both major and minor parts are non-zero and both the suffix and metadata are empty") {
                val version = Version(1, 2, 0, "")

                it("formats it in the format major.minor") {
                    assertThat(version.toString(), equalTo("1.2"))
                }
            }

            on("when all parts are non-zero and both the suffix and metadata are empty") {
                val version = Version(1, 2, 3, "")

                it("formats it in the format major.minor.patch") {
                    assertThat(version.toString(), equalTo("1.2.3"))
                }
            }

            on("when the suffix is non-empty and no metadata is provided") {
                val version = Version(1, 0, 0, "thing")

                it("formats it in the format major.minor.patch-suffix") {
                    assertThat(version.toString(), equalTo("1.0.0-thing"))
                }
            }

            on("when the suffix is empty and metadata is provided") {
                val version = Version(1, 0, 0, "", "extra")

                it("formats it in the format major.minor.patch+metadata") {
                    assertThat(version.toString(), equalTo("1.0.0+extra"))
                }
            }

            on("when both the suffix and metadata are non-empty") {
                val version = Version(1, 0, 0, "thing", "extra")

                it("formats it in the format major.minor.patch-suffix+metadata") {
                    assertThat(version.toString(), equalTo("1.0.0-thing+extra"))
                }
            }
        }

        describe("parsing") {
            on("when only a major version is provided without a suffix") {
                val version = Version.parse("1")

                it("gives the other two parts as zero") {
                    assertThat(version, equalTo(Version(1, 0, 0, "")))
                }
            }

            on("when major and minor versions are provided without a suffix") {
                val version = Version.parse("1.2")

                it("gives the patch level as zero") {
                    assertThat(version, equalTo(Version(1, 2, 0, "")))
                }
            }

            on("when major, minor and patch versions are provided without a suffix") {
                val version = Version.parse("1.2.3")

                it("gives all three values from the string") {
                    assertThat(version, equalTo(Version(1, 2, 3, "")))
                }
            }

            on("when major, minor and patch versions and a suffix are provided") {
                val version = Version.parse("1.2.3-abc-def.12")

                it("gives all four values from the string") {
                    assertThat(version, equalTo(Version(1, 2, 3, "abc-def.12")))
                }
            }

            on("when major, minor and patch versions and both a suffix and metadata are provided") {
                val version = Version.parse("1.2.3-abc-def.12+ghi-jkl.34")

                it("gives all four values from the string") {
                    assertThat(version, equalTo(Version(1, 2, 3, "abc-def.12", "ghi-jkl.34")))
                }
            }

            on("when major, minor and patch versions and metadata are provided") {
                val version = Version.parse("1.2.3+ghi-jkl.34")

                it("gives all four values from the string") {
                    assertThat(version, equalTo(Version(1, 2, 3, "", "ghi-jkl.34")))
                }
            }

            on("when major, minor and patch versions are provided with leading zeroes") {
                val version = Version.parse("01.02.03")

                it("gives all three values from the string") {
                    assertThat(version, equalTo(Version(1, 2, 3, "")))
                }
            }

            setOf(
                "1.",
                "1.2.",
                "1.2.3.",
                "1.2.3.4",
                "",
                "-1.2.3",
                "a",
                "a.b.c",
                "1-",
                "1-thing",
                "1.2-",
                "1.2-thing",
                "1.2.3-"
            ).forEach { value ->
                on("when the value '$value' is parsed") {
                    it("throws an appropriate exception") {
                        assertThat({ Version.parse(value) }, throws<VersionParseException>(withMessage("The value '$value' is not recognised as a valid version.")))
                    }
                }
            }
        }
    }
})
