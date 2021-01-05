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

package batect.cli.options.defaultvalues

import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.os.PathResolverFactory
import batect.os.PathType
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object FileDefaultValueProviderSpec : Spek({
    describe("a file default value provider") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }

        val pathResolver by createForEachTest {
            mock<PathResolver> {
                on { resolve("file-that-exists") } doReturn PathResolutionResult.Resolved("file-that-exists", fileSystem.getPath("/resolved/file-that-exists"), PathType.File, "described as file by resolver")
                on { resolve("not-found") } doReturn PathResolutionResult.Resolved("not-found", fileSystem.getPath("/resolved/not-found"), PathType.DoesNotExist, "described as not found by resolver")
                on { resolve("directory") } doReturn PathResolutionResult.Resolved("directory", fileSystem.getPath("/resolved/directory"), PathType.Directory, "described as directory by resolver")
            }
        }

        val pathResolverFactory by createForEachTest {
            mock<PathResolverFactory> {
                on { createResolverForCurrentDirectory() } doReturn pathResolver
            }
        }

        given("the default path resolves to a file that exists") {
            val provider by createForEachTest { FileDefaultValueProvider("file-that-exists", pathResolverFactory) }

            it("provides the resolved path to the default value") {
                assertThat(provider.value, equalTo(PossibleValue.Valid(fileSystem.getPath("/resolved/file-that-exists"))))
            }

            it("provides a description of the value") {
                assertThat(provider.description, equalTo("Defaults to 'file-that-exists' if that file exists (which it currently does)."))
            }
        }

        given("the default path resolves to a file that does not exist") {
            val provider by createForEachTest { FileDefaultValueProvider("not-found", pathResolverFactory) }

            it("provides no path as the default value") {
                assertThat(provider.value, equalTo(PossibleValue.Valid(null)))
            }

            it("provides a description of the value") {
                assertThat(provider.description, equalTo("Defaults to 'not-found' if that file exists (which it currently does not)."))
            }
        }

        given("the default path resolves to something other than a file") {
            val provider by createForEachTest { FileDefaultValueProvider("directory", pathResolverFactory) }

            it("returns an error for the default value") {
                assertThat(provider.value, equalTo(PossibleValue.Invalid("The path 'directory' (described as directory by resolver) is not a file.")))
            }

            it("provides a description of the value") {
                assertThat(provider.description, equalTo("Defaults to 'directory' if that file exists (which it currently does not)."))
            }
        }
    }
})
