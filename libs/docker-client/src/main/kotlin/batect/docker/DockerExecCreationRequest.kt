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

package batect.docker

import batect.logging.LogMessageBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.json

@Serializable
data class DockerExecCreationRequest(
    val attachStdin: Boolean,
    val attachStdout: Boolean,
    val attachStderr: Boolean,
    val attachTty: Boolean,
    val environmentVariables: Map<String, String>,
    val command: List<String>,
    val privileged: Boolean,
    val userAndGroup: UserAndGroup?,
    val workingDirectory: String?
) {
    fun toJson(): String = json {
        "AttachStdin" to attachStdin
        "AttachStdout" to attachStdout
        "AttachStderr" to attachStderr
        "Tty" to attachTty
        "Env" to environmentVariables.toDockerFormatJsonArray()
        "Cmd" to command.toJsonArray()
        "Privileged" to privileged
        "WorkingDir" to workingDirectory

        if (userAndGroup != null) {
            "User" to "${userAndGroup.userId}:${userAndGroup.groupId}"
        }
    }.toString()
}

fun LogMessageBuilder.data(key: String, value: DockerExecCreationRequest) = this.data(key, value, DockerExecCreationRequest.serializer())
