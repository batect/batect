/*
    Copyright 2017-2022 Charles Korn.

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

import batect.docker.ContainerFilesystemItem
import batect.docker.DockerContainer
import batect.docker.DockerContainerRunResult
import batect.docker.DockerException
import batect.docker.api.ContainersAPI
import batect.docker.data
import batect.docker.run.ContainerIOStreamer
import batect.docker.run.ContainerTTYManager
import batect.docker.run.ContainerWaiter
import batect.docker.run.InputConnection
import batect.docker.run.OutputConnection
import batect.logging.Logger
import batect.os.ConsoleManager
import batect.os.Dimensions
import batect.primitives.CancellationContext
import okio.Sink
import okio.Source

class ContainersClient(
    private val api: ContainersAPI,
    private val consoleManager: ConsoleManager,
    private val waiter: ContainerWaiter,
    private val ioStreamer: ContainerIOStreamer,
    private val ttyManager: ContainerTTYManager,
    private val logger: Logger
) {
    fun stop(container: DockerContainer) = api.stop(container)
    fun remove(container: DockerContainer) = api.remove(container)
    fun upload(container: DockerContainer, source: Set<ContainerFilesystemItem>, destination: String) = api.upload(container, source, destination)

    fun run(container: DockerContainer, stdout: Sink?, stdin: Source?, usesTTY: Boolean, cancellationContext: CancellationContext, frameDimensions: Dimensions, onStarted: () -> Unit): DockerContainerRunResult {
        logger.info {
            message("Running container.")
            data("container", container)
        }

        if (stdout == null && stdin != null) {
            throw DockerException("Attempted to stream input to container without streaming container output.")
        }

        val exitCodeSource = waiter.startWaitingForContainerToExit(container, cancellationContext)

        connectContainerOutput(container, stdout, usesTTY).use { outputConnection ->
            connectContainerInput(container, stdin).use { inputConnection ->
                api.start(container)
                onStarted()

                cancellationContext.addCancellationCallback { onRunCancelled(container) }.use {
                    ttyManager.monitorForSizeChanges(container, frameDimensions).use {
                        startRawModeIfRequired(stdin, usesTTY).use {
                            ioStreamer.stream(outputConnection, inputConnection, cancellationContext)
                        }
                    }
                }
            }
        }

        val exitCode = exitCodeSource.get()

        logger.info {
            message("Container exited.")
            data("container", container)
            data("exitCode", exitCode)
        }

        return DockerContainerRunResult(exitCode)
    }

    private fun connectContainerOutput(container: DockerContainer, stdout: Sink?, isTTY: Boolean): OutputConnection {
        if (stdout == null) {
            return OutputConnection.Disconnected
        }

        return OutputConnection.Connected(api.attachToOutput(container, isTTY), stdout)
    }

    private fun connectContainerInput(container: DockerContainer, stdin: Source?): InputConnection {
        if (stdin == null) {
            return InputConnection.Disconnected
        }

        return InputConnection.Connected(stdin, api.attachToInput(container))
    }

    private fun startRawModeIfRequired(stdin: Source?, isTTY: Boolean): AutoCloseable {
        if (stdin == null || !isTTY) {
            return AutoCloseable { }
        }

        return consoleManager.enterRawMode()
    }

    private fun onRunCancelled(container: DockerContainer) {
        logger.info {
            message("Run cancelled, stopping container.")
            data("container", container)
        }

        api.stop(container)

        logger.info {
            message("Container stopped successfully.")
            data("container", container)
        }
    }
}
