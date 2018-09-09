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

package batect.docker.pull

import batect.docker.defaultRegistryName

// The logic for this is based on https://github.com/docker/cli/blob/master/cli/trust/trust.go
// I don't know why it returns a URL in one case and a domain name in the other, but we have to follow what it does.
class DockerRegistryIndexResolver {
    fun resolveRegistryIndex(registryDomain: String): String {
        if (registryDomain == defaultRegistryName) {
            return "https://index.docker.io/v1/"
        }

        return registryDomain
    }
}
