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

package batect.docker.build

import batect.docker.DockerException
import java.nio.file.Files
import java.nio.file.Path

class DockerIgnoreParser {
    // Based on https://github.com/docker/cli/blob/master/cli/command/image/build/dockerignore.go and
    // https://github.com/docker/engine/blob/master/builder/dockerignore/dockerignore.go
    fun parse(path: Path): DockerImageBuildIgnoreList {
        if (Files.notExists(path)) {
            return DockerImageBuildIgnoreList(emptyList())
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
                DockerImageBuildIgnoreEntry(cleanPattern(pattern), inverted)
            }

        return DockerImageBuildIgnoreList(entries)
    }

    // This needs to match the behaviour of Golang's filepath.Clean()
    private fun cleanPattern(pattern: String): String {
        val normalisedPattern = pattern
            .split("/")
            .filterNot { it == "" }
            .filterNot { it == "." }
            .fold(emptyList<String>()) { soFar, nextSegment ->
                if (nextSegment != "..") {
                    soFar + nextSegment
                } else if (soFar.isEmpty()) {
                    if (pattern.startsWith("/")) {
                        emptyList()
                    } else {
                        listOf(nextSegment)
                    }
                } else if (soFar.last() == "..") {
                    soFar + nextSegment
                } else {
                    soFar.dropLast(1)
                }
            }
            .joinToString("/")

        if (normalisedPattern.isEmpty()) {
            return "."
        }

        return normalisedPattern
    }
}
