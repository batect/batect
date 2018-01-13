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

package batect.model

import batect.logging.LoggerFactory
import batect.model.steps.BeginTaskStep

class TaskStateMachineProvider(private val loggerFactory: LoggerFactory) {
    fun createStateMachine(graph: DependencyGraph, behaviourAfterFailure: BehaviourAfterFailure): TaskStateMachine {
        val stateMachine = TaskStateMachine(
            graph,
            behaviourAfterFailure,
            loggerFactory.createLoggerForClass(TaskStateMachine::class),
            loggerFactory)

        stateMachine.queueStep(BeginTaskStep)

        return stateMachine
    }
}
