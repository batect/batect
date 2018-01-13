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

package batect.model.events

import batect.logging.Logger
import batect.model.steps.DisplayTaskFailureStep

data class TaskNetworkDeletionFailedEvent(val message: String) : TaskEvent() {
    override fun apply(context: TaskEventContext, logger: Logger) {
        val network = context.getSinglePastEventOfType<TaskNetworkCreatedEvent>()!!.network

        val message = "${situationDescription(context)}, the network '${network.id}' could not be deleted: $message\n\n" +
                "This network may not have been removed, so you may need to clean up this network yourself by running 'docker network rm ${network.id}'.\n"

        context.queueStep(DisplayTaskFailureStep(message))

        if (!context.isAborting) {
            context.abort()
        }
    }

    private fun situationDescription(context: TaskEventContext): String {
        if (context.isAborting) {
            return "During clean up after the previous failure"
        } else {
            val exitCode = context.getSinglePastEventOfType<RunningContainerExitedEvent>()!!.exitCode

            return "After the task exited with exit code $exitCode"
        }
    }

    override fun toString() = "${this::class.simpleName}(message: '$message')"
}
