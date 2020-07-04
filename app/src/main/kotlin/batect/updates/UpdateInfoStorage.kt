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

package batect.updates

import batect.io.ApplicationPaths
import batect.logging.Logger
import batect.utils.Json
import java.nio.file.Files

class UpdateInfoStorage(private val applicationPaths: ApplicationPaths, private val logger: Logger) {
    private val updateInfoPath = applicationPaths.rootLocalStorageDirectory.resolve("updates").resolve("v2").resolve("latestVersion")

    fun read(): UpdateInfo? {
        logger.info {
            message("Loading cached update information from disk.")
            data("source", updateInfoPath)
        }

        if (!Files.exists(updateInfoPath)) {
            logger.info {
                message("No cached update information found on disk.")
                data("source", updateInfoPath)
            }

            return null
        }

        val data = Files.readAllLines(updateInfoPath).joinToString("\n")
        val updateInfo = Json.ignoringUnknownKeys.parse(UpdateInfo.serializer(), data)

        logger.info {
            message("Loaded cached update information from disk.")
            data("source", updateInfoPath)
            data("updateInfo", updateInfo)
        }

        return updateInfo
    }

    fun write(updateInfo: UpdateInfo) {
        logger.info {
            message("Writing update information cache to disk.")
            data("destination", updateInfoPath)
            data("updateInfo", updateInfo)
        }

        ensureUpdateInfoDirectoryExists()

        val json = Json.ignoringUnknownKeys.stringify(UpdateInfo.serializer(), updateInfo)
        Files.write(updateInfoPath, json.toByteArray())

        logger.info {
            message("Wrote update information cache to disk.")
            data("destination", updateInfoPath)
            data("updateInfo", updateInfo)
        }
    }

    private fun ensureUpdateInfoDirectoryExists() {
        val updateInfoDirectory = updateInfoPath.parent
        Files.createDirectories(updateInfoDirectory)
    }
}
