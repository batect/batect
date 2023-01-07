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

package batect.telemetry

import batect.io.ApplicationPaths
import batect.logging.LogMessageBuilder
import batect.logging.Logger
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.util.UUID

class TelemetryConfigurationStore(
    private val applicationPaths: ApplicationPaths,
    private val logger: Logger,
) {
    private val configFilePath = applicationPaths.rootLocalStorageDirectory.resolve("telemetry").resolve("config.json")
    private val json = Json.Default
    private lateinit var cachedCurrentConfiguration: TelemetryConfiguration

    val currentConfiguration: TelemetryConfiguration
        get() {
            if (!::cachedCurrentConfiguration.isInitialized) {
                cachedCurrentConfiguration = loadConfiguration()
            }

            return cachedCurrentConfiguration
        }

    private fun loadConfiguration(): TelemetryConfiguration {
        if (!Files.exists(configFilePath)) {
            logger.info {
                message("Telemetry configuration file does not exist, creating a new one.")
                data("configFilePath", configFilePath)
            }

            val configuration = TelemetryConfiguration(UUID.randomUUID(), ConsentState.None)
            saveConfiguration(configuration)

            return configuration
        }

        val content = Files.readAllBytes(configFilePath).toString(Charsets.UTF_8)
        val config = json.decodeFromString(TelemetryConfiguration.serializer(), content)

        logger.info {
            message("Loaded telemetry configuration from disk.")
            data("configFilePath", configFilePath)
            data("config", config)
        }

        return config
    }

    fun saveConfiguration(newConfiguration: TelemetryConfiguration) {
        logger.info {
            message("Saving new telemetry configuration.")
            data("configFilePath", configFilePath)
            data("newConfiguration", newConfiguration)
        }

        val bytes = json.encodeToString(TelemetryConfiguration.serializer(), newConfiguration).toByteArray(Charsets.UTF_8)

        Files.createDirectories(configFilePath.parent)
        Files.write(configFilePath, bytes)

        cachedCurrentConfiguration = newConfiguration
    }

    private fun LogMessageBuilder.data(key: String, value: TelemetryConfiguration) = data(key, value, TelemetryConfiguration.serializer())
}
