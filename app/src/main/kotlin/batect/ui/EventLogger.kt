/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.ui

import batect.execution.PostTaskManualCleanup
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskEventSink
import batect.ui.containerio.ContainerIOStreamingOptions
import java.time.Duration

interface EventLogger : TaskEventSink {
    fun onTaskStarting(taskName: String)
    fun onTaskFinished(taskName: String, exitCode: Long, duration: Duration)
    fun onTaskFinishedWithCleanupDisabled(postTaskManualCleanup: PostTaskManualCleanup.Required, allEvents: Set<TaskEvent>)
    fun onTaskFailed(taskName: String, postTaskManualCleanup: PostTaskManualCleanup, allEvents: Set<TaskEvent>)

    val ioStreamingOptions: ContainerIOStreamingOptions
}
