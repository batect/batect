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

package batect.docker.build.legacy

import batect.logging.PathSerializer
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

@Serializable
data class ImageBuildContext(val entries: Set<ImageBuildContextEntry>)

@Serializable
data class ImageBuildContextEntry(
    @Serializable(with = PathSerializer::class) val localPath: Path,
    val contextPath: String
)

class ImageBuildContextFactory(private val ignoreParser: DockerIgnoreParser) {
    fun createFromDirectory(contextDirectory: Path, dockerfilePath: String): ImageBuildContext {
        val ignoreList = ignoreParser.parse(contextDirectory.resolve(".dockerignore"))

        Files.walk(contextDirectory).use { stream ->
            val files = stream
                .filter { it != contextDirectory }
                .map { it to contextDirectory.relativize(it) }
                .filter { (_, contextPath) -> ignoreList.shouldIncludeInContext(contextPath, dockerfilePath) }
                // We intentionally use the Unix-style path separator below to ensure consistency across operating systems.
                .map { (localPath, contextPath) -> ImageBuildContextEntry(localPath, contextPath.joinToString("/")) }
                .collect(Collectors.toSet())

            return ImageBuildContext(files)
        }
    }
}
