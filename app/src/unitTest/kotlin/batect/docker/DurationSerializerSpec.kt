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

package batect.docker

import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import kotlinx.serialization.Decoder
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration

object DurationSerializerSpec : Spek({
    describe("a duration serializer") {
        given("an integer value") {
            val input = mock<Decoder> {
                on { decodeLong() } doReturn 100
            }

            on("deserializing that value") {
                val result = DurationSerializer.deserialize(input)

                it("returns that value as a duration in nanoseconds") {
                    assertThat(result, equalTo(Duration.ofNanos(100)))
                }
            }
        }
    }
})
