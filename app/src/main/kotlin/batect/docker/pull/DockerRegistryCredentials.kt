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

package batect.docker.pull

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JSON

sealed class DockerRegistryCredentials {
    abstract fun toJSON(): String
}

@Serializable
data class PasswordDockerRegistryCredentials(
    val username: String,
    val password: String,
    @SerialName("serveraddress") val serverAddress: String,
    val email: String = ""
) : DockerRegistryCredentials() {
    override fun toJSON(): String = JSON.plain.stringify(this)
}

@Serializable
data class TokenDockerRegistryCredentials(
    @SerialName("identitytoken") val identityToken: String
) : DockerRegistryCredentials() {
    override fun toJSON(): String = JSON.plain.stringify(this)
}
