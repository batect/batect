/*
   Copyright 2017-2021 Charles Korn.

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

package batect.execution.model.steps.runners

import batect.execution.model.events.TaskEventSink
import batect.execution.model.events.TemporaryDirectoryDeletedEvent
import batect.execution.model.events.TemporaryDirectoryDeletionFailedEvent
import batect.execution.model.steps.DeleteTemporaryDirectoryStep
import java.io.IOException
import java.nio.file.Files

class DeleteTemporaryDirectoryStepRunner {
    fun run(step: DeleteTemporaryDirectoryStep, eventSink: TaskEventSink) {
        try {
            Files.walk(step.directoryPath)
                .sorted { p1, p2 -> -p1.nameCount.compareTo(p2.nameCount) }
                .forEach { Files.delete(it) }

            eventSink.postEvent(TemporaryDirectoryDeletedEvent(step.directoryPath))
        } catch (e: IOException) {
            eventSink.postEvent(TemporaryDirectoryDeletionFailedEvent(step.directoryPath, e.toString()))
        }
    }
}
