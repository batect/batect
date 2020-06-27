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

package batect.docker.client

import batect.docker.DockerContainer
import batect.docker.DockerException
import batect.docker.DockerExecCreationRequest
import batect.docker.DockerExecResult
import batect.docker.Tee
import batect.docker.UserAndGroup
import batect.docker.api.ExecAPI
import batect.docker.data
import batect.docker.run.ContainerIOStreamer
import batect.docker.run.InputConnection
import batect.docker.run.OutputConnection
import batect.execution.CancellationContext
import batect.logging.Logger
import batect.os.Command
import okio.Sink
import okio.sink
import java.io.ByteArrayOutputStream

class DockerExecClient(
    private val api: ExecAPI,
    private val ioStreamer: ContainerIOStreamer,
    private val logger: Logger
) {
    fun run(
        command: Command,
        container: DockerContainer,
        environmentVariables: Map<String, String>,
        privileged: Boolean,
        userAndGroup: UserAndGroup?,
        workingDirectory: String?,
        outputStream: Sink?,
        cancellationContext: CancellationContext
    ): DockerExecResult {
        logger.info {
            message("Executing command in container.")
            data("command", command.parsedCommand)
            data("container", container)
        }

        val creationRequest = DockerExecCreationRequest(
            false,
            true,
            true,
            true,
            environmentVariables,
            command.parsedCommand,
            privileged,
            userAndGroup,
            workingDirectory
        )

        val instance = api.create(container, creationRequest)
        val stream = api.start(creationRequest, instance)
        val output = ByteArrayOutputStream()

        val outputDestination = if (outputStream == null) {
            output.sink()
        } else {
            Tee(output.sink(), outputStream)
        }

        ioStreamer.stream(OutputConnection.Connected(stream, outputDestination), InputConnection.Disconnected, cancellationContext)

        val state = api.inspect(instance)

        if (state.running) {
            throw DockerException("Command started with exec is still running after output finished")
        }

        val result = DockerExecResult(state.exitCode!!, output.toString())

        logger.info {
            message("Finished executing command in container.")
            data("command", command.parsedCommand)
            data("container", container)
            data("result", result)
        }

        return result
    }
}
