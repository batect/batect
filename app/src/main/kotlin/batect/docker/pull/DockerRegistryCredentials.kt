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

package batect.docker.pull

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.json

sealed class DockerRegistryCredentials {
    abstract val serverAddress: String
    abstract fun toJSON(): JsonElement
}

data class PasswordDockerRegistryCredentials(
    val username: String,
    val password: String,
    override val serverAddress: String,
    val email: String = ""
) : DockerRegistryCredentials() {
    override fun toJSON(): JsonElement = json {
        "username" to username
        "password" to password
        "serveraddress" to serverAddress
        "email" to email
    }
}

data class TokenDockerRegistryCredentials(
    val identityToken: String,
    override val serverAddress: String
) : DockerRegistryCredentials() {
    override fun toJSON(): JsonElement = json {
        "identitytoken" to identityToken
    }
}
