/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.config.io.deserializers

import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.on
import batect.testutils.runForEachTest
import com.charleskorn.kaml.Yaml
import com.natpryce.hamkrest.assertion.assertThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PathDeserializerSpec : Spek({
    describe("a path deserializer") {
        val expectedResult by createForEachTest { mock<PathResolutionResult>() }
        val resolver by createForEachTest {
            mock<PathResolver> {
                on { resolve("the-path") } doReturn expectedResult
            }
        }

        val deserializer by createForEachTest { PathDeserializer(resolver) }

        on("parsing some input directly from YAML") {
            val input = "the-path"
            val result by runForEachTest { Yaml.default.decodeFromString(deserializer, input) }

            it("returns the result of resolving that input as a path") {
                assertThat(result, equalTo(expectedResult))
            }
        }
    }
})
