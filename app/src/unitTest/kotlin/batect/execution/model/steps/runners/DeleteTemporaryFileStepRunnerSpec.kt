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
import batect.execution.model.events.TemporaryFileDeletedEvent
import batect.execution.model.events.TemporaryFileDeletionFailedEvent
import batect.execution.model.steps.DeleteTemporaryFileStep
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.on
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files

object DeleteTemporaryFileStepRunnerSpec : Spek({
    describe("running a 'delete temporary file' step") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val filePath by createForEachTest { fileSystem.getPath("/temp-file") }
        val step by createForEachTest { DeleteTemporaryFileStep(filePath) }
        val eventSink by createForEachTest { mock<TaskEventSink>() }
        val runner by createForEachTest { DeleteTemporaryFileStepRunner() }

        on("when deleting the file succeeds") {
            beforeEachTest {
                Files.write(filePath, listOf("test file contents"))

                runner.run(step, eventSink)
            }

            it("emits a 'temporary file deleted' event") {
                verify(eventSink).postEvent(TemporaryFileDeletedEvent(filePath))
            }

            it("deletes the file") {
                assertThat(Files.exists(filePath), equalTo(false))
            }
        }

        on("when deleting the file fails") {
            beforeEachTest { runner.run(step, eventSink) }

            it("emits a 'temporary file deletion failed' event") {
                verify(eventSink).postEvent(TemporaryFileDeletionFailedEvent(filePath, "java.nio.file.NoSuchFileException: /temp-file"))
            }
        }
    }
})
