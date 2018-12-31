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

package batect.execution

import batect.execution.model.stages.CleanupStagePlanner
import batect.execution.model.stages.RunStagePlanner
import batect.logging.LoggerFactory
import batect.ui.FailureErrorMessageFormatter

class TaskStateMachineProvider(
    private val runStagePlanner: RunStagePlanner,
    private val cleanupStagePlanner: CleanupStagePlanner,
    private val failureErrorMessageFormatter: FailureErrorMessageFormatter,
    private val loggerFactory: LoggerFactory
) {
    fun createStateMachine(graph: ContainerDependencyGraph, runOptions: RunOptions): TaskStateMachine =
        TaskStateMachine(
            graph,
            runOptions,
            runStagePlanner,
            cleanupStagePlanner,
            failureErrorMessageFormatter,
            loggerFactory.createLoggerForClass(TaskStateMachine::class)
        )
}
