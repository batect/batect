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

package batect.execution.model.rules.run

import batect.execution.model.events.TaskEvent
import batect.execution.model.rules.TaskStepRule
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.BuildImageStep

data class BuildImageStepRule(val buildDirectory: String, val buildArgs: Map<String, String>, val imageTags: Set<String>) : TaskStepRule() {
    override fun evaluate(pastEvents: Set<TaskEvent>): TaskStepRuleEvaluationResult {
        return TaskStepRuleEvaluationResult.Ready(BuildImageStep(buildDirectory, buildArgs, imageTags))
    }

    override fun toString() = "${this::class.simpleName}(" +
        "build directory: '$buildDirectory', " +
        "build args: [${buildArgs.map { "${it.key}=${it.value}" }.joinToString(", ")}], " +
        "image tags: $imageTags)"
}
