/*
   Copyright 2017-2021 Charles Korn.

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

// Important: this class is security sensitive - the values returned by this class are used to determine which set of credentials to send to the registry.
// If we get this wrong, we could send the credentials for a different registry when pushing or pulling an image.
data class ImageReference(val originalReference: String) {
    init {
        if (originalReference == "") {
            throw DockerException("Image reference cannot be an empty string.")
        }
    }

    val registryDomain: String
        get() {
            val possibleRegistryName = originalReference.substringBefore("/", defaultRegistryName)

            if (possibleRegistryName == "index.docker.io") {
                return defaultRegistryName
            }

            if (possibleRegistryName.contains('.') || possibleRegistryName.contains(':') || possibleRegistryName == "localhost") {
                return possibleRegistryName
            }

            return defaultRegistryName
        }

    val registryIndex: String
        get() {
            if (registryDomain == defaultRegistryName) {
                return "https://index.docker.io/v1/"
            }

            return registryDomain
        }

    val normalizedReference: String
        get() {
            val imageName = originalReference.substringAfterLast('/')

            if (imageName.contains(':')) {
                return originalReference
            }

            return "$originalReference:latest"
        }

    companion object {
        private const val defaultRegistryName = "docker.io"
    }
}
