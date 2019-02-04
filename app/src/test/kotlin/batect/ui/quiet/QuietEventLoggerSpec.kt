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

package batect.ui.quiet

import batect.execution.RunOptions
import batect.execution.model.events.TaskFailedEvent
import batect.testutils.createForEachTest
import batect.ui.Console
import batect.ui.FailureErrorMessageFormatter
import batect.ui.text.TextRun
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.time.Duration

object QuietEventLoggerSpec : Spek({
    describe("a quiet event logger") {
        val failureErrorMessageFormatter by createForEachTest { mock<FailureErrorMessageFormatter>() }
        val runOptions by createForEachTest { mock<RunOptions>() }
        val errorConsole by createForEachTest { mock<Console>() }

        val logger by createForEachTest { QuietEventLogger(failureErrorMessageFormatter, runOptions, errorConsole) }

        on("when a 'task failed' event is posted") {
            val event = mock<TaskFailedEvent>()
            whenever(failureErrorMessageFormatter.formatErrorMessage(event, runOptions)).doReturn(TextRun("Something went wrong."))

            logger.postEvent(event)

            it("prints the message to the console") {
                inOrder(errorConsole) {
                    verify(errorConsole).println()
                    verify(errorConsole).println(TextRun("Something went wrong."))
                }
            }
        }

        on("when a task fails") {
            logger.onTaskFailed("some-task", TextRun("Some cleanup instructions"))

            it("does not print anything to the console") {
                verifyZeroInteractions(errorConsole)
            }
        }

        on("when a task starts") {
            logger.onTaskStarting("some-task")

            it("does not print anything to the console") {
                verifyZeroInteractions(errorConsole)
            }
        }

        on("when a task finishes") {
            logger.onTaskFinished("some-task", 123, Duration.ofNanos(456))

            it("does not print anything to the console") {
                verifyZeroInteractions(errorConsole)
            }
        }
    }
})
