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

import batect.docker.DockerContainer
import batect.docker.api.ContainersAPI
import batect.execution.CancellationContext
import java.util.concurrent.Executors
import java.util.concurrent.Future

class ContainerWaiter(private val api: ContainersAPI) {
    fun startWaitingForContainerToExit(container: DockerContainer, cancellationContext: CancellationContext): Future<Int> {
        return Executors.newSingleThreadExecutor().submit<Int> {
            api.waitForExit(container, cancellationContext)
        }
    }
}
