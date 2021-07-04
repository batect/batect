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

package batect.docker.build.buildkit.services

import batect.docker.build.ImageBuildIgnoreEntry
import batect.docker.build.MatchResult
import batect.primitives.mapToSet
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.streams.toList

// Exclude patterns: patterns of files to exclude, possibly with negation - order matters as last matching pattern wins
// Include patterns: literal file names (not patterns) to include
// Follow paths: patterns of files to include, no negation
//
// Yes, 'include patterns' and 'follow paths' seem around the wrong way, but that's how it's implemented... :shrug:
class FileSyncScope(
    private val rootDirectory: Path,
    excludePatterns: List<String>,
    private val includePatterns: Set<String>,
    followPaths: Set<String>
) {
    private val parsedFollowPathPatterns = followPaths.mapToSet { ImageBuildIgnoreEntry(it, true) }
    private val parsedExcludePatterns = excludePatterns.map { pattern ->
        if (pattern.isNotEmpty() && pattern[0] == '!') {
            ImageBuildIgnoreEntry(pattern.substring(1), true)
        } else {
            ImageBuildIgnoreEntry(pattern, false)
        }
    }

    // Why a list and not a set? BuildKit cares about the order of entries - parents must be sent before their children,
    // and children must be sorted alphabetically.
    val contents: List<FileSyncScopeEntry> = resolveContents()

    private fun resolveContents(): List<FileSyncScopeEntry> {
        return walkDirectory(rootDirectory).sortedBy { it.relativePath }
    }

    // TODO: don't bother walking a directory that could never be included by includePatterns / followPaths
    // TODO: don't bother walking a directory that could never be included due to excludePatterns
    private fun walkDirectory(directory: Path): List<FileSyncScopeEntry> {
        val contents = Files.list(directory).toList()
        val entries = mutableListOf<FileSyncScopeEntry>()

        contents.forEach { child ->
            val entry = createEntry(child)
            val childEntries = if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) { walkDirectory(child) } else { emptyList() }

            if (childEntries.isNotEmpty()) {
                entries += entry
                entries += childEntries
            } else if (isIncluded(entry) && !isExcluded(entry)) {
                entries += entry
            }
        }

        return entries
    }

    private fun createEntry(path: Path): FileSyncScopeEntry {
        val relativePath = rootDirectory.relativize(path).toUnixStyleString()

        return FileSyncScopeEntry(path, relativePath)
    }

    private fun isIncluded(entry: FileSyncScopeEntry): Boolean {
        if (includePatterns.isEmpty() && parsedFollowPathPatterns.isEmpty()) {
            return true
        }

        if (includePatterns.contains(entry.relativePath)) {
            return true
        }

        return parsedFollowPathPatterns.any { it.matches(entry.relativePath) == MatchResult.MatchedInclude }
    }

    private fun isExcluded(entry: FileSyncScopeEntry): Boolean {
        for (pattern in parsedExcludePatterns.reversed()) {
            val result = pattern.matches(entry.relativePath)

            if (result == MatchResult.MatchedExclude) {
                return true
            } else if (result == MatchResult.MatchedInclude) {
                return false
            }
        }

        return false
    }

    private fun Path.toUnixStyleString() = this.joinToString("/")
}
