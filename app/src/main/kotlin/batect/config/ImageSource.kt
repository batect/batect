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

package batect.config

import java.nio.file.Path

sealed class ImageSource

data class BuildImage(val buildDirectory: Path, val buildArgs: Map<String, Expression> = emptyMap(), val dockerfilePath: String = "Dockerfile") : ImageSource() {
    override fun toString(): String = "${this.javaClass.simpleName}(" +
        "build directory: '$buildDirectory', " +
        "build args: [${buildArgs.map { "${it.key}=${it.value}" }.joinToString(", ")}], " +
        "Dockerfile path: '$dockerfilePath')"
}

data class PullImage(val imageName: String) : ImageSource() {
    override fun toString(): String = "${this.javaClass.simpleName}(image: '$imageName')"
}
