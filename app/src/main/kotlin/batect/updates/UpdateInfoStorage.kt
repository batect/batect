/*
   Copyright 2017-2018 Charles Korn.

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

import batect.logging.Logger
import batect.os.SystemInfo
import batect.utils.Version
import java.nio.file.FileSystem
import java.nio.file.Files
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class UpdateInfoStorage(private val fileSystem: FileSystem, private val systemInfo: SystemInfo, private val logger: Logger) {
    private val updateInfoPath = fileSystem.getPath(systemInfo.homeDirectory, ".batect", "updates", "latestVersion")

    fun read(): UpdateInfo? {
        logger.info {
            message("Loading cached update information from disk.")
            data("source", updateInfoPath.toString())
        }

        if (!Files.exists(updateInfoPath)) {
            logger.info {
                message("No cached update information found on disk.")
                data("source", updateInfoPath.toString())
            }

            return null
        }

        val lines = Files.readAllLines(updateInfoPath)
        val values = lines.associate { it.substringBefore('=') to it.substringAfter('=') }

        val version = Version.parse(values.getValue("version"))
        val url = values.getValue("url")
        val lastUpdated = parseDate(values.getValue("lastUpdated"))
        val updateInfo = UpdateInfo(version, url, lastUpdated)

        logger.info {
            message("Loaded cached update information from disk.")
            data("source", updateInfoPath.toString())
            data("updateInfo", updateInfo)
        }

        return updateInfo
    }

    fun write(updateInfo: UpdateInfo) {
        logger.info {
            message("Writing update information cache to disk.")
            data("destination", updateInfoPath.toString())
            data("updateInfo", updateInfo)
        }

        ensureUpdateInfoDirectoryExists()

        val values = mapOf(
            "version" to updateInfo.version,
            "url" to updateInfo.url,
            "lastUpdated" to formatDate(updateInfo.lastUpdated)
        )

        val lines = values.map { (key, value) -> "$key=$value" }
        Files.write(updateInfoPath, lines)

        logger.info {
            message("Wrote update information cache to disk.")
            data("destination", updateInfoPath.toString())
            data("updateInfo", updateInfo)
        }
    }

    private fun ensureUpdateInfoDirectoryExists() {
        val updateInfoDirectory = updateInfoPath.parent
        Files.createDirectories(updateInfoDirectory)
    }

    private val formatter = DateTimeFormatter.ISO_DATE_TIME
    private fun parseDate(value: String): ZonedDateTime = ZonedDateTime.parse(value, formatter)
    private fun formatDate(value: ZonedDateTime): String = value.format(formatter)
}
