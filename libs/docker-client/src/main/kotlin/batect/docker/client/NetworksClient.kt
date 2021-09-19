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

package batect.docker.client

import batect.docker.DockerNetwork
import batect.docker.api.NetworksAPI

class NetworksClient(private val api: NetworksAPI) {
    fun create(name: String, driver: String): DockerNetwork = api.create(name, driver)
    fun delete(network: DockerNetwork) = api.delete(network)
    fun getByNameOrId(identifier: String): DockerNetwork = api.getByNameOrId(identifier)
}
