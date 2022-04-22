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

import batect.docker.ImageBuildFailedException
import batect.docker.ImageReference
import batect.primitives.mapToSet
import java.nio.file.Files
import java.nio.file.Path

class DockerfileParser {
    fun extractBaseImageNames(dockerfilePath: Path): Set<ImageReference> {
        val lines = Files.readAllLines(dockerfilePath)
        val fromInstructions = lines.filter { it.startsWith("FROM") }

        if (fromInstructions.isEmpty()) {
            throw ImageBuildFailedException("The Dockerfile '$dockerfilePath' is invalid: there is no FROM instruction.")
        }

        return fromInstructions.mapToSet { ImageReference(it.substringAfter("FROM ").substringBefore(" AS").trim()) }
    }
}
