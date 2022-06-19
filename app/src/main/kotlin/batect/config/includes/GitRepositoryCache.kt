/*
    Copyright 2017-2022 Charles Korn.

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

package batect.config.includes

import batect.VersionInfo
import batect.git.GitClient
import batect.io.ApplicationPaths
import batect.os.deleteDirectory
import batect.primitives.mapToSet
import batect.utils.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors
import java.util.stream.Stream

class GitRepositoryCache(
    private val applicationPaths: ApplicationPaths,
    private val gitClient: GitClient,
    private val versionInfo: VersionInfo,
    private val timeSource: TimeSource = ZonedDateTime::now
) {
    private val gitCacheDirectory = applicationPaths.rootLocalStorageDirectory.resolve("incl").toAbsolutePath()

    fun ensureCached(repo: GitRepositoryReference, listener: GitRepositoryCacheNotificationListener): Path {
        Files.createDirectories(gitCacheDirectory)

        val workingCopyPath = gitCacheDirectory.resolve(repo.cacheKey)
        val infoPath = gitCacheDirectory.resolve("${repo.cacheKey}.json")
        val now = timeSource()

        cloneRepoIfMissing(repo, workingCopyPath, listener)
        updateInfoFile(repo, infoPath, now)

        return workingCopyPath
    }

    private fun cloneRepoIfMissing(repo: GitRepositoryReference, workingCopyPath: Path, listener: GitRepositoryCacheNotificationListener) {
        if (!Files.exists(workingCopyPath)) {
            listener.onCloning(repo)
            gitClient.clone(repo.remote, repo.ref, workingCopyPath)
            listener.onCloneComplete()
        }
    }

    private fun updateInfoFile(repo: GitRepositoryReference, infoPath: Path, lastUsed: ZonedDateTime) {
        val existingContent = if (Files.exists(infoPath)) {
            Json.default.parseToJsonElement(Files.readAllBytes(infoPath).toString(Charsets.UTF_8)).jsonObject
        } else {
            buildJsonObject {
                put("type", "git")

                putJsonObject("repo") {
                    put("remote", repo.remote)
                    put("ref", repo.ref)
                }

                put("clonedWithVersion", versionInfo.version.toString())
            }
        }

        val info = JsonObject(
            existingContent + mapOf(
                "lastUsed" to JsonPrimitive(lastUsed.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            )
        )

        Files.write(infoPath, info.toString().toByteArray(Charsets.UTF_8))
    }

    fun listAll(): Set<CachedGitRepository> {
        if (!Files.isDirectory(gitCacheDirectory)) {
            return emptySet()
        }

        val cacheDirectoryContents = Files.list(gitCacheDirectory).toSet()
        val infoFiles = cacheDirectoryContents.filter { it.fileName.toString().endsWith(".json") }

        return infoFiles.mapToSet { loadInfoFile(it) }
    }

    private fun loadInfoFile(infoFilePath: Path): CachedGitRepository {
        try {
            val infoFileContent = Files.readAllBytes(infoFilePath).toString(Charsets.UTF_8)
            val infoFileJson = Json.default.parseToJsonElement(infoFileContent).jsonObject
            val repoJson = infoFileJson.getValue("repo").jsonObject
            val repo = GitRepositoryReference(repoJson.getValue("remote").jsonPrimitive.content, repoJson.getValue("ref").jsonPrimitive.content)
            val lastUsed = ZonedDateTime.parse(infoFileJson.getValue("lastUsed").jsonPrimitive.content, DateTimeFormatter.ISO_OFFSET_DATE_TIME)

            val expectedWorkingCopyPath = infoFilePath.parent.resolve(infoFilePath.fileName.toString().removeSuffix(".json"))
            val haveWorkingCopy = Files.exists(expectedWorkingCopyPath)

            return CachedGitRepository(
                repo,
                lastUsed,
                if (haveWorkingCopy) expectedWorkingCopyPath else { null },
                infoFilePath
            )
        } catch (e: IllegalArgumentException) {
            throw GitRepositoryCacheException("The file $infoFilePath could not be loaded: ${e.message}", e)
        } catch (e: NoSuchElementException) {
            throw GitRepositoryCacheException("The file $infoFilePath could not be loaded: ${e.message}", e)
        }
    }

    fun delete(repo: CachedGitRepository) {
        if (repo.workingCopyPath != null) {
            deleteDirectory(repo.workingCopyPath)
        }

        if (Files.exists(repo.infoPath)) {
            Files.delete(repo.infoPath)
        }
    }

    private fun <T> Stream<T>.toSet(): Set<T> = collect(Collectors.toSet<T>())
}

data class CachedGitRepository(val repo: GitRepositoryReference, val lastUsed: ZonedDateTime, val workingCopyPath: Path?, val infoPath: Path)
class GitRepositoryCacheException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

typealias TimeSource = () -> ZonedDateTime
