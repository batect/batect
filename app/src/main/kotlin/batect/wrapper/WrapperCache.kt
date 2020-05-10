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

package batect.wrapper

import batect.VersionInfo
import batect.logging.Logger
import batect.os.HostEnvironmentVariables
import batect.utils.Version
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset
import java.time.ZonedDateTime

class WrapperCache(
    fileSystem: FileSystem,
    environmentVariables: HostEnvironmentVariables,
    private val logger: Logger
) {
    private val cacheDirectory: Path? = resolveCacheDirectory(fileSystem, environmentVariables)

    private fun resolveCacheDirectory(fileSystem: FileSystem, environmentVariables: Map<String, String>): Path? {
        val path = environmentVariables[cacheDirectoryEnvironmentVariableName] ?: return null

        return fileSystem.getPath(path).toAbsolutePath()
    }

    fun setLastUsedForCurrentVersion() = setLastUsedForVersion(VersionInfo().version, ZonedDateTime.now(ZoneOffset.UTC))

    fun setLastUsedForVersion(version: Version, time: ZonedDateTime) {
        if (cacheDirectory == null) {
            logger.warn {
                message("Wrapper cache directory environment variable ($cacheDirectoryEnvironmentVariableName) not set, not storing last used time.")
            }

            return
        }

        val versionDirectory = cacheDirectory.resolve(version.toString())

        if (!Files.exists(versionDirectory)) {
            logger.warn {
                message("Cache directory for version does not exist, not storing last used time.")
                data("version", version)
                data("directory", versionDirectory)
            }

            return
        }

        val lastUsedFile = versionDirectory.resolve("lastUsed")
        val timeInUTC = time.withZoneSameInstant(ZoneOffset.UTC)

        Files.write(lastUsedFile, listOf(timeInUTC.toString()), Charsets.UTF_8)
    }

    companion object {
        private const val cacheDirectoryEnvironmentVariableName: String = "BATECT_WRAPPER_CACHE_DIR"
    }
}
