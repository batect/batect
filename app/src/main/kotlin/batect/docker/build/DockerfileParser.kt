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

package batect.docker.build

import batect.docker.ImageBuildFailedException
import java.nio.file.Files
import java.nio.file.Path

class DockerfileParser {
    fun extractBaseImageName(dockerfilePath: Path): String {
        val lines = Files.readAllLines(dockerfilePath)

        val fromInstruction = lines.firstOrNull { it.startsWith("FROM") }

        if (fromInstruction == null) {
            throw ImageBuildFailedException("The Dockerfile '$dockerfilePath' is invalid: there is no FROM instruction.")
        }

        return fromInstruction.substringAfter("FROM ")
    }
}
