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

package batect.os.unix

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files

object ApplicationResolverSpec : Spek({
    describe("an application resolver") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val resolver by createForEachTest { ApplicationResolver(fileSystem) }

        given("/bin/stty exists") {
            beforeEachTest {
                val sttyPath = fileSystem.getPath("/bin", "stty")
                Files.createDirectories(sttyPath.parent)
                Files.createFile(sttyPath)
            }

            it("returns '/bin/stty' as the stty to use") {
                assertThat(resolver.stty, equalTo("/bin/stty"))
            }
        }

        given("/bin/stty does not exist") {
            it("returns 'stty' as the stty to use") {
                assertThat(resolver.stty, equalTo("stty"))
            }
        }
    }
})
