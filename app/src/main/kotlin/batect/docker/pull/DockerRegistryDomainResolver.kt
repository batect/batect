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

package batect.docker.pull

import batect.docker.defaultRegistryName

// The logic for this is based on https://github.com/docker/distribution/blob/master/reference/normalize.go.
class DockerRegistryDomainResolver {
    fun resolveDomainForImage(imageName: String): String {
        val possibleRegistryName = imageName.substringBefore("/", defaultRegistryName)

        if (possibleRegistryName == "index.docker.io") {
            return defaultRegistryName
        }

        if (possibleRegistryName.contains('.') || possibleRegistryName.contains(':') || possibleRegistryName == "localhost") {
            return possibleRegistryName
        }

        return defaultRegistryName
    }
}
