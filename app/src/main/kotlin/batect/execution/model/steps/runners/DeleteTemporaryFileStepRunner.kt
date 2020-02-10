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

package batect.execution.model.steps.runners

import batect.execution.model.events.TaskEventSink
import batect.execution.model.events.TemporaryFileDeletedEvent
import batect.execution.model.events.TemporaryFileDeletionFailedEvent
import batect.execution.model.steps.DeleteTemporaryFileStep
import java.io.IOException
import java.nio.file.Files

class DeleteTemporaryFileStepRunner {
    fun run(step: DeleteTemporaryFileStep, eventSink: TaskEventSink) {
        try {
            Files.delete(step.filePath)
            eventSink.postEvent(TemporaryFileDeletedEvent(step.filePath))
        } catch (e: IOException) {
            eventSink.postEvent(TemporaryFileDeletionFailedEvent(step.filePath, e.toString()))
        }
    }
}
