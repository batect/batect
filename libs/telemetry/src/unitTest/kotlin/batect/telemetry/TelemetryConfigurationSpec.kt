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

package batect.telemetry

import batect.testutils.equalTo
import com.natpryce.hamkrest.assertion.assertThat
import kotlinx.serialization.json.Json
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

object TelemetryConfigurationSpec : Spek({
    describe("telemetry configuration") {
        val parser = Json.Default
        val userId = UUID.fromString("00001111-2222-3333-4444-555566667777")
        val config = TelemetryConfiguration(userId, ConsentState.TelemetryAllowed)

        val json = """
            { "state": "allowed", "userId": "00001111-2222-3333-4444-555566667777" }
        """.trimIndent()

        describe("serializing to JSON") {
            it("serializes to the expected JSON") {
                assertThat(parser.encodeToString(TelemetryConfiguration.serializer(), config), equivalentTo(json))
            }
        }

        describe("deserializing from JSON") {
            it("deserializes to the expected object") {
                assertThat(parser.decodeFromString(TelemetryConfiguration.serializer(), json), equalTo(config))
            }
        }
    }
})
