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

package batect.docker.run

import batect.docker.DockerAPI
import batect.docker.DockerContainer
import batect.ui.ConsoleInfo
import jnr.constants.platform.Signal
import jnr.posix.POSIX

class ContainerTTYManager(
    private val api: DockerAPI,
    private val consoleInfo: ConsoleInfo,
    private val posix: POSIX
) {
    fun monitorForSizeChanges(container: DockerContainer): AutoCloseable {
        if (!consoleInfo.stdinIsTTY) {
            return object : AutoCloseable {
                override fun close() {}
            }
        }

        val originalHandler = posix.signal(Signal.SIGWINCH) {
            sendCurrentDimensionsToContainer(container)
        }

        sendCurrentDimensionsToContainer(container)

        return object : AutoCloseable {
            override fun close() {
                posix.signal(Signal.SIGWINCH, originalHandler)
            }
        }
    }

    private fun sendCurrentDimensionsToContainer(container: DockerContainer) {
        val currentDimensions = consoleInfo.dimensions

        if (currentDimensions != null) {
            api.resizeContainerTTY(container, currentDimensions)
        }
    }
}
