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
import batect.execution.model.events.TemporaryDirectoryDeletedEvent
import batect.execution.model.events.TemporaryDirectoryDeletionFailedEvent
import batect.execution.model.steps.DeleteTemporaryDirectoryStep
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

object DeleteTemporaryDirectoryStepRunnerSpec : Spek({
    describe("running a 'delete temporary directory' step") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val directoryPath by createForEachTest { fileSystem.getPath("/temp-directory") }
        val step by createForEachTest { DeleteTemporaryDirectoryStep(directoryPath) }
        val eventSink by createForEachTest { mock<TaskEventSink>() }
        val runner by createForEachTest { DeleteTemporaryDirectoryStepRunner() }

        on("when deleting the directory succeeds") {
            beforeEachTest {
                Files.createDirectories(directoryPath)
                Files.write(directoryPath.resolve("some-file"), listOf("some file content"))

                runner.run(step, eventSink)
            }

            it("emits a 'temporary directory deleted' event") {
                verify(eventSink).postEvent(TemporaryDirectoryDeletedEvent(directoryPath))
            }

            it("deletes the directory") {
                assertThat(Files.exists(directoryPath), equalTo(false))
            }
        }

        on("when deleting the directory fails") {
            beforeEachTest { runner.run(step, eventSink) }

            it("emits a 'temporary directory deletion failed' event") {
                verify(eventSink).postEvent(TemporaryDirectoryDeletionFailedEvent(directoryPath, "java.nio.file.NoSuchFileException: /temp-directory"))
            }
        }
    }
})
