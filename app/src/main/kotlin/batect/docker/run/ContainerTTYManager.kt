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

package batect.docker.run

import batect.docker.api.ContainerStoppedException
import batect.docker.DockerContainer
import batect.docker.api.ContainersAPI
import batect.logging.Logger
import batect.os.Dimensions
import batect.ui.ConsoleDimensions

class ContainerTTYManager(
    private val api: ContainersAPI,
    private val consoleDimensions: ConsoleDimensions,
    private val logger: Logger
) {
    fun monitorForSizeChanges(container: DockerContainer, frameDimensions: Dimensions): AutoCloseable {
        val cleanup = consoleDimensions.registerListener {
            sendCurrentDimensionsToContainer(container, frameDimensions)
        }

        sendCurrentDimensionsToContainer(container, frameDimensions)

        return cleanup
    }

    private fun sendCurrentDimensionsToContainer(container: DockerContainer, frameDimensions: Dimensions) {
        val currentDimensions = consoleDimensions.current

        if (currentDimensions != null) {
            try {
                api.resizeTTY(container, currentDimensions - frameDimensions)
            } catch (e: ContainerStoppedException) {
                logger.warn {
                    message("Resizing container failed because the container is stopped.")
                    exception(e)
                }
            }
        }
    }
}
