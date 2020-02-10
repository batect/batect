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

package batect.execution.model.rules.cleanup

import batect.execution.model.rules.TaskStepRule
import batect.os.OperatingSystem

abstract class CleanupTaskStepRule : TaskStepRule() {
    abstract fun getManualCleanupInstructionForOperatingSystem(operatingSystem: OperatingSystem): String?

    abstract val manualCleanupSortOrder: ManualCleanupSortOrder
}

enum class ManualCleanupSortOrder(val order: Int) {
    RemoveContainers(1),
    DeleteTemporaryFiles(RemoveContainers.order + 1),
    DeleteTaskNetwork(DeleteTemporaryFiles.order + 1)
}
