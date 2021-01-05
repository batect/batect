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

package batect.execution

import batect.config.Container
import batect.config.Task
import batect.config.TaskRunConfiguration
import batect.os.Command

class ContainerEntrypointResolver {
    fun resolveEntrypoint(container: Container, task: Task): Command? {
        if (task.runConfiguration != null && isTaskContainer(container, task.runConfiguration) && task.runConfiguration.entrypoint != null) {
            return task.runConfiguration.entrypoint
        }

        return container.entrypoint
    }

    private fun isTaskContainer(container: Container, runConfiguration: TaskRunConfiguration): Boolean =
        container.name == runConfiguration.container
}
