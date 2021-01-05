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

import batect.io.ApplicationPaths
import batect.logging.Logger
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

class TelemetryUploadQueue(
    private val applicationPaths: ApplicationPaths,
    private val logger: Logger
) {
    private val telemetryDirectory = applicationPaths.rootLocalStorageDirectory.resolve("telemetry")
    private val json = Json.Default

    fun add(session: TelemetrySession): Path {
        val path = telemetryDirectory.resolve("session-${session.sessionId}.json")

        logger.info {
            message("Saving telemetry session to disk.")
            data("path", path)
        }

        val bytes = json.encodeToString(TelemetrySession.serializer(), session).toByteArray(Charsets.UTF_8)

        if (!Files.exists(path.parent)) {
            Files.createDirectories(path.parent)
        }

        Files.write(path, bytes)

        return path
    }

    fun getAll(): Set<Path> {
        if (!Files.exists(telemetryDirectory)) {
            return emptySet()
        }

        return Files.list(telemetryDirectory).asSequence()
            .filter {
                val fileName = it.fileName.toString()

                fileName.startsWith("session-") && fileName.endsWith(".json")
            }
            .toSet()
    }

    fun pop(path: Path) {
        if (!Files.exists(path)) {
            logger.warn {
                message("Couldn't delete telemetry session as it does not exist.")
                data("path", path)
            }

            return
        }

        logger.info {
            message("Deleting telemetry session from disk.")
            data("path", path)
        }

        Files.delete(path)
    }
}
