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

package batect.telemetry

import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ConsentStateSpec : Spek({
    describe("telemetry consent state") {
        val json = Json(JsonConfiguration.Stable)
        val userId = UUID.fromString("00001111-2222-3333-4444-555566667777")

        describe("serializing to JSON") {
            given("the disabled state") {
                it("serializes to the expected JSON") {
                    assertThat(json.stringify(ConsentState.serializer(), ConsentState.Disabled), equivalentTo("""
                        { "type": "disabled" }
                    """.trimIndent()))
                }
            }

            given("an enabled state") {
                it("serializes to the expected JSON") {
                    assertThat(json.stringify(ConsentState.serializer(), ConsentState.Enabled(userId)), equivalentTo("""
                        { "type": "enabled", "userId": "00001111-2222-3333-4444-555566667777" }
                    """.trimIndent()))
                }
            }
        }

        describe("deserializing from JSON") {
            given("some JSON representing the disabled state") {
                val input = """
                    { "type": "disabled" }
                """.trimIndent()

                it("deserializes to the disabled state") {
                    assertThat(json.parse(ConsentState.serializer(), input), equalTo(ConsentState.Disabled))
                }
            }

            given("some JSON representing the enabled state") {
                val input = """
                    { "type": "enabled", "userId": "00001111-2222-3333-4444-555566667777" }
                """.trimIndent()

                it("deserializes to an enabled state") {
                    assertThat(json.parse(ConsentState.serializer(), input), equalTo(ConsentState.Enabled(userId)))
                }
            }
        }
    }
})
