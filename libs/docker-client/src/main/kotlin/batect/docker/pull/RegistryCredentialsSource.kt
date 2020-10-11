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

package batect.docker.pull

import batect.docker.DockerRegistryCredentialsException
import batect.docker.Json
import batect.os.ExecutableDoesNotExistException
import batect.os.ProcessRunner
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.Charset
import java.util.Base64

sealed class RegistryCredentialsSource {
    abstract fun load(): RegistryCredentials?
}

data class BasicCredentialsSource(val encodedCredentials: String, val serverAddress: String) : RegistryCredentialsSource() {
    override fun load(): RegistryCredentials {
        if (encodedCredentials == "") {
            throw DockerRegistryCredentialsException("Credentials for '$serverAddress' are empty.")
        }

        try {
            val decoded = Base64.getDecoder().decode(encodedCredentials).toString(Charset.defaultCharset())

            val parts = decoded.split(":", limit = 2)

            if (parts.size != 2) {
                throw DockerRegistryCredentialsException("Decoded credentials for '$serverAddress' are not valid.")
            }

            if (parts[0] == "<token>") {
                return TokenRegistryCredentials(parts[1], serverAddress)
            } else {
                return PasswordRegistryCredentials(parts[0], parts[1], serverAddress)
            }
        } catch (_: IllegalArgumentException) {
            throw DockerRegistryCredentialsException("Could not decode credentials for '$serverAddress'.")
        }
    }
}

data class HelperBasedCredentialsSource(val helperName: String, val serverAddress: String, private val processRunner: ProcessRunner) : RegistryCredentialsSource() {
    private val gcpTokenUsername = "_dcgcloud_token"

    override fun load(): RegistryCredentials? {
        try {
            val command = listOf(helperName, "get")
            val result = processRunner.runAndCaptureOutput(command, serverAddress)
            val output = result.output.trim()

            if (result.exitCode != 0) {
                if (output == "credentials not found in native keychain") {
                    return null
                }

                throw DockerRegistryCredentialsException("Could not load credentials for '$serverAddress' because the credential helper executable '$helperName' exited with code ${result.exitCode} and output: $output")
            }

            return convertOutputToCredentials(output)
        } catch (e: ExecutableDoesNotExistException) {
            throw DockerRegistryCredentialsException("Could not load credentials for '$serverAddress' because the credential helper executable '$helperName' does not exist.", e)
        }
    }

    private fun convertOutputToCredentials(output: String): RegistryCredentials {
        val parsed = parseCredentials(output)
        val username = parsed.getStringMember("Username")
        val secret = parsed.getStringMember("Secret")

        if (username == gcpTokenUsername) {
            return PasswordRegistryCredentials(username, secret, serverAddress)
        }

        val serverUrl = parsed.getStringMember("ServerURL")

        if (username == "<token>") {
            return TokenRegistryCredentials(secret, serverUrl)
        } else {
            return PasswordRegistryCredentials(username, secret, serverUrl)
        }
    }

    private fun parseCredentials(credentials: String): JsonObject {
        try {
            return Json.default.parseToJsonElement(credentials).jsonObject
        } catch (e: Throwable) {
            throw DockerRegistryCredentialsException("The credentials returned for '$serverAddress' by the credential helper executable '$helperName' are invalid: ${e.message}", e)
        }
    }

    private fun JsonObject.getStringMember(key: String): String {
        if (this.containsKey(key)) {
            return this.getValue(key).jsonPrimitive.content
        }

        throw DockerRegistryCredentialsException("The credentials returned for '$serverAddress' by the credential helper executable '$helperName' are invalid: there is no '$key' field.")
    }
}
