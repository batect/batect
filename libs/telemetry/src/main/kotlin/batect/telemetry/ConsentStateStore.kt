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

import batect.io.ApplicationPaths
import batect.logging.LogMessageBuilder
import batect.logging.Logger
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

class ConsentStateStore(
    private val applicationPaths: ApplicationPaths,
    private val logger: Logger
) {
    private val consentStateFilePath = applicationPaths.rootLocalStorageDirectory.resolve("telemetry").resolve("consent.json")
    private val json = Json(JsonConfiguration.Stable)

    var consentState: ConsentState = loadConsentState()
        private set

    private fun loadConsentState(): ConsentState {
        if (!Files.exists(consentStateFilePath)) {
            logger.info {
                message("Consent state file does not exist.")
                data("consentStateFilePath", consentStateFilePath)
            }

            return ConsentState.None
        }

        val content = Files.readAllBytes(consentStateFilePath).toString(Charsets.UTF_8)
        val state = json.parse(ConsentState.serializer(), content)

        logger.info {
            message("Loaded consent state from disk.")
            data("consentStateFilePath", consentStateFilePath)
            data("consentState", state)
        }

        return state
    }

    fun saveConsentState(newState: ConsentState) {
        if (newState == ConsentState.None) {
            throw RuntimeException("Cannot save the 'none' consent state.")
        }

        logger.info {
            message("Saving new consent state.")
            data("consentStateFilePath", consentStateFilePath)
            data("newState", newState)
        }

        val bytes = json.stringify(ConsentState.serializer(), newState).toByteArray(Charsets.UTF_8)

        Files.createDirectories(consentStateFilePath.parent)
        Files.write(consentStateFilePath, bytes)

        consentState = newState
    }

    private fun LogMessageBuilder.data(key: String, value: ConsentState) = data(key, value, ConsentState.serializer())
}
