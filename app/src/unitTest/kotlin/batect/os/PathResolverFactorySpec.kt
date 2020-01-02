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

package batect.os

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.on
import batect.testutils.runForEachTest
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PathResolverFactorySpec : Spek({
    describe("a path resolver factory") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val factory by createForEachTest { PathResolverFactory(fileSystem) }

        on("getting a resolver for a particular path") {
            val resolver by runForEachTest { factory.createResolver(fileSystem.getPath("some-path")) }

            it("returns a resolver relative to that path") {
                assertThat(resolver, equalTo(PathResolver(fileSystem.getPath("some-path"))))
            }
        }

        on("getting a resolver for the current directory") {
            val resolver by runForEachTest { factory.createResolverForCurrentDirectory() }

            it("returns a resolver relative to the current directory") {
                assertThat(resolver, equalTo(PathResolver(fileSystem.getPath("."))))
            }
        }
    }
})
