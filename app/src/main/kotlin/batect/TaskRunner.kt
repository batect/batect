/*
   Copyright 2017 Charles Korn.

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

package batect

import batect.config.Configuration
import batect.config.Task
import batect.logging.Logger
import batect.model.DependencyGraphProvider
import batect.model.TaskStateMachineProvider
import batect.ui.EventLoggerProvider

data class TaskRunner(
        private val eventLoggerProvider: EventLoggerProvider,
        private val graphProvider: DependencyGraphProvider,
        private val stateMachineProvider: TaskStateMachineProvider,
        private val executionManagerProvider: ParallelExecutionManagerProvider,
        private val logger: Logger
) {
    fun run(config: Configuration, task: Task, maximumConcurrentSteps: Int): Int {
        logger.info {
            message("Preparing task.")
            data("task", task.name)
        }

        val graph = graphProvider.createGraph(config, task)
        val eventLogger = eventLoggerProvider.getEventLogger(graph)
        eventLogger.onTaskStarting(task.name)

        val stateMachine = stateMachineProvider.createStateMachine(graph)
        val executionManager = executionManagerProvider.createParallelExecutionManager(eventLogger, stateMachine, task.name, maximumConcurrentSteps)

        logger.info {
            message("Preparation complete, starting task.")
            data("task", task.name)
        }

        return executionManager.run()
    }
}
