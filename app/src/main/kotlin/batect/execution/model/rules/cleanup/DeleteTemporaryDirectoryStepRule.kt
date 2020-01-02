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

import batect.config.Container
import batect.execution.model.events.ContainerRemovedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.DeleteTemporaryDirectoryStep
import batect.os.OperatingSystem
import java.nio.file.Path

data class DeleteTemporaryDirectoryStepRule(val path: Path, val containerThatMustBeRemovedFirst: Container?) : CleanupTaskStepRule() {
    override fun evaluate(pastEvents: Set<TaskEvent>): TaskStepRuleEvaluationResult {
        if (containerThatMustBeRemovedFirst == null || containerHasBeenRemoved(pastEvents, containerThatMustBeRemovedFirst)) {
            return TaskStepRuleEvaluationResult.Ready(DeleteTemporaryDirectoryStep(path))
        }

        return TaskStepRuleEvaluationResult.NotReady
    }

    private fun containerHasBeenRemoved(pastEvents: Set<TaskEvent>, container: Container): Boolean =
        pastEvents.contains(ContainerRemovedEvent(container))

    override fun getManualCleanupInstructionForOperatingSystem(operatingSystem: OperatingSystem): String? = when (operatingSystem) {
        OperatingSystem.Windows -> "Remove-Item -Recurse $path (if using PowerShell) or rmdir /s /q $path (if using Command Prompt)"
        else -> "rm -rf $path"
    }

    override val manualCleanupSortOrder: ManualCleanupSortOrder = ManualCleanupSortOrder.DeleteTemporaryFiles
    override fun toString() = "${this::class.simpleName}(path: '$path', container that must be removed first: ${if (containerThatMustBeRemovedFirst == null) "null" else "'${containerThatMustBeRemovedFirst.name}'"})"
}
