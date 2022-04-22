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

import batect.cli.CommandLineOptionsParser
import batect.utils.asHumanReadableList

class UntaggedImagesException(val containerNames: Set<String>) : RuntimeException(generateMessage(containerNames)) {
    override fun toString(): String = message!!

    private companion object {
        private fun generateMessage(containerNames: Set<String>): String {
            if (containerNames.isEmpty()) {
                throw IllegalArgumentException("Cannot create a UntaggedImagesException with an empty set of container names.")
            }

            if (containerNames.size == 1) {
                return "The image for container '${containerNames.single()}' was requested to be tagged with --${CommandLineOptionsParser.imageTagsOptionName}, but this container did not run as part of the task or its prerequisites."
            }

            val formattedNames = containerNames.map { "'$it'" }.asHumanReadableList()

            return "The images for containers $formattedNames were requested to be tagged with --${CommandLineOptionsParser.imageTagsOptionName}, but these containers did not run as part of the task or its prerequisites."
        }
    }
}
