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

package batect.execution.model.stages

import batect.execution.model.events.TaskEvent
import batect.execution.model.rules.TaskStepRule
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.rules.cleanup.CleanupTaskStepRule

sealed class Stage(rules: Set<TaskStepRule>) {
    private val remainingRules = rules.toMutableSet()

    fun popNextStep(pastEvents: Set<TaskEvent>): NextStepResult {
        if (remainingRules.isEmpty()) {
            return NoStepsRemaining
        }

        remainingRules.forEach { rule ->
            val result = rule.evaluate(pastEvents)

            if (result is TaskStepRuleEvaluationResult.Ready) {
                remainingRules.remove(rule)
                return StepReady(result.step)
            }
        }

        return NoStepsReady
    }
}

class RunStage(val rules: Set<TaskStepRule>) : Stage(rules)
class CleanupStage(val rules: Set<CleanupTaskStepRule>) : Stage(rules) {
    val manualCleanupCommands: List<String> = rules
        .filter { it.manualCleanupInstruction != null }
        .sortedBy { it.manualCleanupSortOrder.order }
        .map { it.manualCleanupInstruction!! }
}
