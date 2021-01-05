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

package batect.docker

import batect.logging.LogMessageBuilder
import batect.primitives.Version
import kotlinx.serialization.Serializable

@Serializable
data class DockerVersionInfo(val version: Version, val apiVersion: String, val minAPIVersion: String, val gitCommit: String, val operatingSystem: String, val experimental: Boolean) {
    override fun toString(): String = "$version (API version: $apiVersion, minimum supported API version: $minAPIVersion, commit: $gitCommit, operating system: '$operatingSystem', experimental: $experimental)"
}

fun LogMessageBuilder.data(key: String, value: DockerVersionInfo) = this.data(key, value, DockerVersionInfo.serializer())
