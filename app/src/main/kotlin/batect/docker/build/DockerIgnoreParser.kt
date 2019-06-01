/*
   Copyright 2017-2019 Charles Korn.

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

package batect.docker.build

import batect.docker.DockerException
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path

class DockerIgnoreParser {
    // Based on https://github.com/docker/cli/blob/master/cli/command/image/build/dockerignore.go and
    // https://github.com/docker/engine/blob/master/builder/dockerignore/dockerignore.go
    fun parse(path: Path): DockerImageBuildIgnoreList {
        if (Files.notExists(path)) {
            return DockerImageBuildIgnoreList(emptyList())
        }

        val fileSystem = path.fileSystem
        val lines = Files.readAllLines(path)

        val entries = lines
            .filterNot { it.startsWith('#') }
            .map { it.trim() }
            .filterNot { it.isEmpty() }
            .filterNot { it.equals(".") }
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
                DockerImageBuildIgnoreEntry(cleanPattern(pattern, fileSystem), inverted)
            }

        return DockerImageBuildIgnoreList(entries)
    }

    private fun cleanPattern(pattern: String, fileSystem: FileSystem): String {
        val patternToClean = fileSystem.getPath(pattern).normalize().toString()

        if (patternToClean.isEmpty() || patternToClean == "/") {
            return "."
        } else if (patternToClean.startsWith('/')) {
            return patternToClean.substring(1)
        } else {
            return patternToClean
        }
    }
}
