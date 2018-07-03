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

package batect.docker

import batect.utils.Version

data class DockerVersionInfo(val client: DockerClientVersionInfo, val server: DockerServerVersionInfo) {
    override fun toString(): String = "Client: $client, server: $server"
}

data class DockerClientVersionInfo(val version: Version, val apiVersion: String, val gitCommit: String) {
    override fun toString(): String = "$version (API: $apiVersion, commit: $gitCommit)"
}

data class DockerServerVersionInfo(val version: Version, val apiVersion: String, val minAPIVersion: String, val gitCommit: String) {
    override fun toString(): String = "$version (API: $apiVersion, minimum supported API: $minAPIVersion, commit: $gitCommit)"
}
