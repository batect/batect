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

package batect.execution

import batect.cli.CommandLineOptions
import batect.config.BuildImage
import batect.config.Container

class ImageTaggingValidator(
    private val commandLineOptions: CommandLineOptions
) {
    private val usedContainerNames = mutableSetOf<String>()

    fun notifyContainersUsed(containers: Set<Container>) {
        containers.forEach { container ->
            if (commandLineOptions.imageTags.keys.contains(container.name) && container.imageSource !is BuildImage) {
                throw ContainerUsesPulledImageException(container.name)
            }

            usedContainerNames += container.name
        }
    }

    fun checkForUntaggedContainers(): Set<String> {
        return commandLineOptions.imageTags.keys - usedContainerNames
    }
}
