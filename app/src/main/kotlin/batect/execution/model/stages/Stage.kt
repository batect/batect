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

package batect.execution.model.stages

import batect.execution.model.events.TaskEvent
import batect.execution.model.rules.TaskStepRule
import batect.execution.model.rules.TaskStepRuleEvaluationResult

abstract class Stage(rules: Set<TaskStepRule>) {
    private val remainingRules = rules.toMutableSet()

    fun popNextStep(pastEvents: Set<TaskEvent>, stepsStillRunning: Boolean): NextStepResult {
        if (remainingRules.isEmpty() && determineIfStageIsComplete(pastEvents, stepsStillRunning)) {
            return StageComplete
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

    protected abstract fun determineIfStageIsComplete(pastEvents: Set<TaskEvent>, stepsStillRunning: Boolean): Boolean
}
