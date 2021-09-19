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

package batect.config

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.on
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ProjectPathsSpec : Spek({
    describe("a set of project-related paths") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val configurationFilePath by createForEachTest { fileSystem.getPath("some-dir/batect.yml") }
        val paths by createForEachTest { ProjectPaths(configurationFilePath) }

        on("getting the project root directory") {
            it("returns the absolute path to the directory containing the configuration file") {
                assertThat(paths.projectRootDirectory, equalTo(fileSystem.getPath("/work/some-dir")))
            }
        }

        on("getting the project Batect directory") {
            it("returns the absolute path to '.batect' in the project root directory") {
                assertThat(paths.batectDirectory, equalTo(fileSystem.getPath("/work/some-dir/.batect")))
            }
        }

        on("getting the project cache directory") {
            it("returns the absolute path to 'caches' in the project Batect directory") {
                assertThat(paths.cacheDirectory, equalTo(fileSystem.getPath("/work/some-dir/.batect/caches")))
            }
        }
    }
})
