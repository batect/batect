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

import batect.config.Container
import batect.execution.model.events.RunningContainerExitedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.rules.TaskStepRule

class RunStage(val rules: Set<TaskStepRule>, val taskContainer: Container) : Stage(rules) {
    override fun determineIfStageIsComplete(pastEvents: Set<TaskEvent>, stepsStillRunning: Boolean): Boolean {
        if (!stepsStillRunning) {
            return true
        }

        if (pastEvents.any { it is RunningContainerExitedEvent && it.container == taskContainer }) {
            return true
        }

        return false
    }
}
