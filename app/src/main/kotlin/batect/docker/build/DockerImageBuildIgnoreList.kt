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

data class DockerImageBuildIgnoreList(private val entries: List<DockerImageBuildIgnoreEntry>) {
    fun shouldIncludeInContext(pathToTest: String): Boolean {
        if (pathToTest == ".dockerignore" || pathToTest == "Dockerfile") {
            return true
        }

        for (entry in entries.reversed()) {
            val result = entry.matches(pathToTest)

            if (result == MatchResult.MatchedExclude) {
                return false
            } else if (result == MatchResult.MatchedInclude) {
                return true
            }
        }

        return true
    }
}
