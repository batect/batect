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

package batect.execution.model.stages

import batect.execution.model.events.TaskEvent
import batect.execution.model.rules.cleanup.CleanupTaskStepRule
import batect.os.OperatingSystem

class CleanupStage(val rules: Set<CleanupTaskStepRule>, val operatingSystem: OperatingSystem) : Stage(rules) {
    val manualCleanupInstructions: List<String> = rules
        .map { it to it.getManualCleanupInstructionForOperatingSystem(operatingSystem) }
        .filter { (_, instruction) -> instruction != null }
        .sortedBy { (rule, _) -> rule.manualCleanupSortOrder }
        .map { (_, instruction) -> instruction!! }

    override fun determineIfStageIsComplete(pastEvents: Set<TaskEvent>, stepsStillRunning: Boolean) = !stepsStillRunning
}
