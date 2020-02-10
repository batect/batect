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

package batect.cli.options

import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.os.PathResolverFactory
import batect.os.PathType
import batect.testutils.equalTo
import batect.testutils.given
import batect.ui.OutputStyle
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ValueConvertersSpec : Spek({
    describe("value converters") {
        describe("string value converter") {
            it("returns the value passed to it") {
                assertThat(ValueConverters.string("some-value"),
                    equalTo(ValueConversionResult.ConversionSucceeded("some-value")))
            }
        }

        describe("boolean value converter") {
            listOf(
                "1",
                "yes",
                "YES",
                "true",
                "TRUE"
            ).forEach { value ->
                given("the value '$value'") {
                    it("returns the value 'true'") {
                        assertThat(ValueConverters.boolean(value), equalTo(ValueConversionResult.ConversionSucceeded(true)))
                    }
                }
            }

            listOf(
                "0",
                "no",
                "NO",
                "false",
                "FALSE"
            ).forEach { value ->
                given("the value '$value'") {
                    it("returns the value 'false'") {
                        assertThat(ValueConverters.boolean(value), equalTo(ValueConversionResult.ConversionSucceeded(false)))
                    }
                }
            }

            given("an invalid value") {
                it("returns an appropriate error") {
                    assertThat(ValueConverters.boolean("blah"), equalTo(ValueConversionResult.ConversionFailed("Value is not a recognised boolean value.")))
                }
            }
        }

        describe("positive integer value converter") {
            given("a positive integer") {
                it("returns the parsed representation of that integer") {
                    assertThat(ValueConverters.positiveInteger("1"),
                        equalTo(ValueConversionResult.ConversionSucceeded(1)))
                }
            }

            given("zero") {
                it("returns an error") {
                    assertThat(ValueConverters.positiveInteger("0"),
                        equalTo(ValueConversionResult.ConversionFailed("Value must be positive.")))
                }
            }

            given("a negative integer") {
                it("returns an error") {
                    assertThat(ValueConverters.positiveInteger("-1"),
                        equalTo(ValueConversionResult.ConversionFailed("Value must be positive.")))
                }
            }

            given("an empty string") {
                it("returns an error") {
                    assertThat(ValueConverters.positiveInteger(""),
                        equalTo(ValueConversionResult.ConversionFailed("Value is not a valid integer.")))
                }
            }

            given("a hexadecimal number") {
                it("returns an error") {
                    assertThat(ValueConverters.positiveInteger("123AAA"),
                        equalTo(ValueConversionResult.ConversionFailed("Value is not a valid integer.")))
                }
            }

            given("something that is not a number") {
                it("returns an error") {
                    assertThat(ValueConverters.positiveInteger("x"),
                        equalTo(ValueConversionResult.ConversionFailed("Value is not a valid integer.")))
                }
            }
        }

        describe("optional enum value converter") {
            val converter = ValueConverters.optionalEnum<OutputStyle>()

            given("a valid value") {
                it("returns the equivalent enum constant") {
                    assertThat(converter("simple"), equalTo(ValueConversionResult.ConversionSucceeded(OutputStyle.Simple)))
                }
            }

            given("an empty string") {
                it("returns an error") {
                    assertThat(converter(""), equalTo(ValueConversionResult.ConversionFailed("Value must be one of 'all', 'fancy', 'quiet' or 'simple'.")))
                }
            }

            given("an invalid value") {
                it("returns an error") {
                    assertThat(converter("nonsense"), equalTo(ValueConversionResult.ConversionFailed("Value must be one of 'all', 'fancy', 'quiet' or 'simple'.")))
                }
            }
        }

        describe("path to file converter") {
            val fileSystem = Jimfs.newFileSystem(Configuration.unix())

            val pathResolver = mock<PathResolver> {
                on { resolve("file") } doReturn PathResolutionResult.Resolved("file", fileSystem.getPath("/resolved/file"), PathType.File)
                on { resolve("directory") } doReturn PathResolutionResult.Resolved("directory", fileSystem.getPath("/resolved/directory"), PathType.Directory)
                on { resolve("other") } doReturn PathResolutionResult.Resolved("other", fileSystem.getPath("/resolved/other"), PathType.Other)
                on { resolve("does-not-exist") } doReturn PathResolutionResult.Resolved("does-not-exist", fileSystem.getPath("/resolved/does-not-exist"), PathType.DoesNotExist)
                on { resolve("invalid") } doReturn PathResolutionResult.InvalidPath("invalid")
            }

            val pathResolverFactory = mock<PathResolverFactory> {
                on { createResolverForCurrentDirectory() } doReturn pathResolver
            }

            given("the file is not required to exist") {
                val converter = ValueConverters.pathToFile(pathResolverFactory, mustExist = false)

                given("a path to a file that exists") {
                    it("returns the resolved path") {
                        assertThat(converter("file"), equalTo(ValueConversionResult.ConversionSucceeded(fileSystem.getPath("/resolved/file"))))
                    }
                }

                given("a path to a directory") {
                    it("returns an error") {
                        assertThat(converter("directory"), equalTo(ValueConversionResult.ConversionFailed("The path 'directory' (resolved to '/resolved/directory') refers to a directory.")))
                    }
                }

                given("a path to something other than a file or directory") {
                    it("returns an error") {
                        assertThat(converter("other"), equalTo(ValueConversionResult.ConversionFailed("The path 'other' (resolved to '/resolved/other') refers to something other than a file.")))
                    }
                }

                given("a path to a file that does not exist") {
                    it("returns the resolved path") {
                        assertThat(converter("does-not-exist"), equalTo(ValueConversionResult.ConversionSucceeded(fileSystem.getPath("/resolved/does-not-exist"))))
                    }
                }

                given("an invalid path") {
                    it("returns an error") {
                        assertThat(converter("invalid"), equalTo(ValueConversionResult.ConversionFailed("'invalid' is not a valid path.")))
                    }
                }
            }

            given("the file is required to exist") {
                val converter = ValueConverters.pathToFile(pathResolverFactory, mustExist = true)

                given("a path to a file that exists") {
                    it("returns the resolved path") {
                        assertThat(converter("file"), equalTo(ValueConversionResult.ConversionSucceeded(fileSystem.getPath("/resolved/file"))))
                    }
                }

                given("a path to a directory") {
                    it("returns an error") {
                        assertThat(converter("directory"), equalTo(ValueConversionResult.ConversionFailed("The path 'directory' (resolved to '/resolved/directory') refers to a directory.")))
                    }
                }

                given("a path to something other than a file or directory") {
                    it("returns an error") {
                        assertThat(converter("other"), equalTo(ValueConversionResult.ConversionFailed("The path 'other' (resolved to '/resolved/other') refers to something other than a file.")))
                    }
                }

                given("a path to a file that does not exist") {
                    it("returns an error") {
                        assertThat(converter("does-not-exist"), equalTo(ValueConversionResult.ConversionFailed("The file 'does-not-exist' (resolved to '/resolved/does-not-exist') does not exist.")))
                    }
                }

                given("an invalid path") {
                    it("returns an error") {
                        assertThat(converter("invalid"), equalTo(ValueConversionResult.ConversionFailed("'invalid' is not a valid path.")))
                    }
                }
            }
        }

        describe("path to directory converter") {
            val fileSystem = Jimfs.newFileSystem(Configuration.unix())

            val pathResolver = mock<PathResolver> {
                on { resolve("file") } doReturn PathResolutionResult.Resolved("file", fileSystem.getPath("/resolved/file"), PathType.File)
                on { resolve("directory") } doReturn PathResolutionResult.Resolved("directory", fileSystem.getPath("/resolved/directory"), PathType.Directory)
                on { resolve("other") } doReturn PathResolutionResult.Resolved("other", fileSystem.getPath("/resolved/other"), PathType.Other)
                on { resolve("does-not-exist") } doReturn PathResolutionResult.Resolved("does-not-exist", fileSystem.getPath("/resolved/does-not-exist"), PathType.DoesNotExist)
                on { resolve("invalid") } doReturn PathResolutionResult.InvalidPath("invalid")
            }

            val pathResolverFactory = mock<PathResolverFactory> {
                on { createResolverForCurrentDirectory() } doReturn pathResolver
            }

            given("the directory is not required to exist") {
                val converter = ValueConverters.pathToDirectory(pathResolverFactory, mustExist = false)

                given("a path to a file") {
                    it("returns an error") {
                        assertThat(converter("file"), equalTo(ValueConversionResult.ConversionFailed("The path 'file' (resolved to '/resolved/file') refers to a file.")))
                    }
                }

                given("a path to a directory") {
                    it("returns the resolved path") {
                        assertThat(converter("directory"), equalTo(ValueConversionResult.ConversionSucceeded(fileSystem.getPath("/resolved/directory"))))
                    }
                }

                given("a path to something other than a file or directory") {
                    it("returns an error") {
                        assertThat(converter("other"), equalTo(ValueConversionResult.ConversionFailed("The path 'other' (resolved to '/resolved/other') refers to something other than a directory.")))
                    }
                }

                given("a path to a directory that does not exist") {
                    it("returns the resolved path") {
                        assertThat(converter("does-not-exist"), equalTo(ValueConversionResult.ConversionSucceeded(fileSystem.getPath("/resolved/does-not-exist"))))
                    }
                }

                given("an invalid path") {
                    it("returns an error") {
                        assertThat(converter("invalid"), equalTo(ValueConversionResult.ConversionFailed("'invalid' is not a valid path.")))
                    }
                }
            }

            given("the file is required to exist") {
                val converter = ValueConverters.pathToDirectory(pathResolverFactory, mustExist = true)

                given("a path to a file") {
                    it("returns an error") {
                        assertThat(converter("file"), equalTo(ValueConversionResult.ConversionFailed("The path 'file' (resolved to '/resolved/file') refers to a file.")))
                    }
                }

                given("a path to a directory") {
                    it("returns the resolved path") {
                        assertThat(converter("directory"), equalTo(ValueConversionResult.ConversionSucceeded(fileSystem.getPath("/resolved/directory"))))
                    }
                }

                given("a path to something other than a file or directory") {
                    it("returns an error") {
                        assertThat(converter("other"), equalTo(ValueConversionResult.ConversionFailed("The path 'other' (resolved to '/resolved/other') refers to something other than a directory.")))
                    }
                }

                given("a path to a directory that does not exist") {
                    it("returns an error") {
                        assertThat(converter("does-not-exist"), equalTo(ValueConversionResult.ConversionFailed("The directory 'does-not-exist' (resolved to '/resolved/does-not-exist') does not exist.")))
                    }
                }

                given("an invalid path") {
                    it("returns an error") {
                        assertThat(converter("invalid"), equalTo(ValueConversionResult.ConversionFailed("'invalid' is not a valid path.")))
                    }
                }
            }
        }
    }
})
