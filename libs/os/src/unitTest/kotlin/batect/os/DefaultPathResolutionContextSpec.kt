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

package batect.os

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DefaultPathResolutionContextSpec : Spek({
    describe("a path resolution context") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val context by createForEachTest { DefaultPathResolutionContext(fileSystem.getPath("/root")) }

        describe("generating a description of how the path was resolved") {
            it("returns the absolute path") {
                assertThat(context.getResolutionDescription(fileSystem.getPath("/some/path")), equalTo("resolved to '/some/path'"))
            }
        }

        describe("formatting a path for display") {
            it("returns the absolute path in single quotes") {
                assertThat(context.getPathForDisplay(fileSystem.getPath("/some/path")), equalTo("'/some/path'"))
            }
        }
    }
})
