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

package batect.utils

import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.greaterThan
import com.natpryce.hamkrest.throws
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object VersionSpec : Spek({
    describe("a version") {
        describe("comparing two versions") {
            val version = Version(1, 2, 3)

            on("when the two versions are the same") {
                it("returns zero") {
                    assertThat(version.compareTo(Version(1, 2, 3)), equalTo(0))
                }
            }

            on("when the major and minor parts are the same") {
                val otherVersion = Version(1, 2, 4)

                it("gives the version with the higher patch level as greater than the other version") {
                    assertThat(otherVersion, greaterThan(version))
                }
            }

            on("when the major parts are the same") {
                val otherVersion = Version(1, 3, 2)

                it("gives the version with the higher minor version as greater than the other version") {
                    assertThat(otherVersion, greaterThan(version))
                }
            }

            on("when the major versions are different") {
                val otherVersion = Version(2, 1, 0)

                it("gives the version with the higher major version as greater than the other version") {
                    assertThat(otherVersion, greaterThan(version))
                }
            }
        }

        describe("converting to string") {
            on("when only the major part is non-zero") {
                val version = Version(1, 0, 0)

                it("formats it in the format major.minor") {
                    assertThat(version.toString(), equalTo("1.0"))
                }
            }

            on("when both major and minor parts are non-zero") {
                val version = Version(1, 2, 0)

                it("formats it in the format major.minor") {
                    assertThat(version.toString(), equalTo("1.2"))
                }
            }

            on("when all parts are non-zero") {
                val version = Version(1, 2, 3)

                it("formats it in the format major.minor.patch") {
                    assertThat(version.toString(), equalTo("1.2.3"))
                }
            }
        }

        describe("parsing") {
            on("when only a major version is provided") {
                val version = Version.parse("1")

                it("gives the other two parts as zero") {
                    assertThat(version, equalTo(Version(1, 0, 0)))
                }
            }

            on("when major and minor versions are provided") {
                val version = Version.parse("1.2")

                it("gives the patch level as zero") {
                    assertThat(version, equalTo(Version(1, 2, 0)))
                }
            }

            on("when major, minor and patch versions are provided") {
                val version = Version.parse("1.2.3")

                it("gives all three values from the string") {
                    assertThat(version, equalTo(Version(1, 2, 3)))
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
                "a.b.c"
            ).forEach { value ->
                on("when the value '$value' is parsed") {
                    it("throws an appropriate exception") {
                        assertThat({ Version.parse(value) }, throws<IllegalArgumentException>(withMessage("The value '$value' is not recognised as a valid version.")))
                    }
                }
            }
        }
    }
})
