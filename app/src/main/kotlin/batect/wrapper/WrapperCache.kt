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

package batect.wrapper

import batect.VersionInfo
import batect.logging.Logger
import batect.logging.data
import batect.os.HostEnvironmentVariables
import batect.primitives.Version
import batect.primitives.VersionParseException
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import kotlin.streams.toList

class WrapperCache(
    fileSystem: FileSystem,
    environmentVariables: HostEnvironmentVariables,
    private val logger: Logger,
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
                data("versionDirectory", versionDirectory)
            }

            return
        }

        val lastUsedFile = versionDirectory.resolve("lastUsed")
        val timeInUTC = time.withZoneSameInstant(ZoneOffset.UTC)

        Files.write(lastUsedFile, listOf(timeInUTC.toString()), Charsets.UTF_8)
    }

    fun getCachedVersions(): Set<CachedWrapperVersion> {
        if (cacheDirectory == null) {
            logger.warn {
                message("Wrapper cache directory environment variable ($cacheDirectoryEnvironmentVariableName) not set, returning empty list of versions.")
            }

            return emptySet()
        }

        if (!Files.exists(cacheDirectory)) {
            logger.warn {
                message("Cache directory does not exist, returning empty list of versions.")
                data("cacheDirectory", cacheDirectory)
            }

            return emptySet()
        }

        return Files.list(cacheDirectory).toList()
            .filter { Files.isDirectory(it) }
            .mapNotNull { versionDirectory -> loadCachedVersionFromDirectory(versionDirectory) }
            .toSet()
    }

    private fun loadCachedVersionFromDirectory(versionDirectory: Path): CachedWrapperVersion? {
        val version = try {
            Version.parse(versionDirectory.fileName.toString())
        } catch (e: VersionParseException) {
            logger.warn {
                message("Directory name cannot be parsed as a version, ignoring directory.")
                data("directory", versionDirectory)
            }

            return null
        }

        val lastUsed = loadLastUsedTimeFromDirectory(versionDirectory, version)

        return CachedWrapperVersion(version, lastUsed, versionDirectory)
    }

    private fun loadLastUsedTimeFromDirectory(versionDirectory: Path, version: Version): ZonedDateTime? {
        val lastUsedFilePath = versionDirectory.resolve("lastUsed")

        if (!Files.exists(lastUsedFilePath)) {
            logger.warn {
                message("Version cache directory does not contain a last used time file.")
                data("version", version)
                data("versionDirectory", versionDirectory)
            }

            return null
        }

        val contents = Files.readAllLines(lastUsedFilePath, Charsets.UTF_8).joinToString("\n")

        return try {
            ZonedDateTime.parse(contents)
        } catch (e: DateTimeParseException) {
            logger.warn {
                message("Last used time file does not contain a valid time, ignoring.")
                exception(e)
                data("version", version)
                data("versionDirectory", versionDirectory)
                data("lastUsedFilePath", lastUsedFilePath)
            }

            null
        }
    }

    // Note: this assumes the version directory contains only files and will break if the directory contains subdirectories.
    fun delete(cachedWrapper: CachedWrapperVersion) {
        logger.info {
            message("Deleting cached version.")
            data("version", cachedWrapper.version)
            data("versionDirectory", cachedWrapper.cacheDirectory)
            data("lastUsed", cachedWrapper.lastUsed)
        }

        val contents = Files.list(cachedWrapper.cacheDirectory)

        contents
            .filter { !it.endsWith("lastUsed") }
            .forEach { Files.delete(it) }

        // Delete the last used time file last so that it is preserved (where possible) if this method is interrupted
        Files.deleteIfExists(cachedWrapper.cacheDirectory.resolve("lastUsed"))
        Files.delete(cachedWrapper.cacheDirectory)

        logger.info {
            message("Cached version deleted.")
            data("version", cachedWrapper.version)
            data("versionDirectory", cachedWrapper.cacheDirectory)
        }
    }

    companion object {
        private const val cacheDirectoryEnvironmentVariableName: String = "BATECT_WRAPPER_CACHE_DIR"
    }
}
