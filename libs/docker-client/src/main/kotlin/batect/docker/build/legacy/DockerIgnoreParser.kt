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

package batect.docker.build.legacy

import batect.docker.DockerException
import batect.docker.build.ImageBuildIgnoreEntry
import java.nio.file.Files
import java.nio.file.Path

class DockerIgnoreParser {
    // Based on https://github.com/docker/cli/blob/master/cli/command/image/build/dockerignore.go and
    // https://github.com/docker/engine/blob/master/builder/dockerignore/dockerignore.go
    fun parse(path: Path): ImageBuildIgnoreList {
        if (Files.notExists(path)) {
            return ImageBuildIgnoreList(emptyList())
        }

        val lines = Files.readAllLines(path)

        val entries = lines
            .filterNot { it.startsWith('#') }
            .map { it.trim() }
            .filterNot { it.isEmpty() }
            .filterNot { it == "." }
            .map {
                if (it == "!") {
                    throw DockerException("The .dockerignore pattern '$it' is invalid.")
                }

                if (it.startsWith('!')) {
                    Pair(it.substring(1).trimStart(), true)
                } else {
                    Pair(it, false)
                }
            }.map { (pattern, inverted) ->
                ImageBuildIgnoreEntry.withUncleanPattern(pattern, inverted)
            }

        return ImageBuildIgnoreList(entries)
    }
}
