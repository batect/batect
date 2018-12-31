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

package batect.docker.run

import batect.docker.ContainerStoppedException
import batect.docker.DockerAPI
import batect.docker.DockerContainer
import batect.logging.Logger
import batect.os.SignalListener
import batect.ui.ConsoleInfo
import jnr.constants.platform.Signal

class ContainerTTYManager(
    private val api: DockerAPI,
    private val consoleInfo: ConsoleInfo,
    private val signalListener: SignalListener,
    private val logger: Logger
) {
    fun monitorForSizeChanges(container: DockerContainer): AutoCloseable {
        if (!consoleInfo.stdinIsTTY) {
            return object : AutoCloseable {
                override fun close() {}
            }
        }

        val cleanup = signalListener.start(Signal.SIGWINCH) {
            sendCurrentDimensionsToContainer(container)
        }

        sendCurrentDimensionsToContainer(container)

        return cleanup
    }

    private fun sendCurrentDimensionsToContainer(container: DockerContainer) {
        val currentDimensions = consoleInfo.dimensions

        if (currentDimensions != null) {
            try {
                api.resizeContainerTTY(container, currentDimensions)
            } catch (e: ContainerStoppedException) {
                logger.warn {
                    message("Resizing container failed because the container is stopped.")
                    exception(e)
                }
            }
        }
    }
}
