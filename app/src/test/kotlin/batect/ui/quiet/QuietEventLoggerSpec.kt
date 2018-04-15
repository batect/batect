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

package batect.ui.quiet

import batect.execution.RunOptions
import batect.execution.model.events.TaskFailedEvent
import batect.testutils.createForEachTest
import batect.ui.Console
import batect.ui.ConsoleColor
import batect.ui.ConsolePrintStatements
import batect.ui.FailureErrorMessageFormatter
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object QuietEventLoggerSpec : Spek({
    describe("a quiet event logger") {
        val failureErrorMessageFormatter by createForEachTest { mock<FailureErrorMessageFormatter>() }
        val runOptions by createForEachTest { mock<RunOptions>() }

        val redErrorConsole by createForEachTest { mock<Console>() }
        val errorConsole by createForEachTest {
            mock<Console> {
                on { withColor(eq(ConsoleColor.Red), any()) } doAnswer {
                    val printStatements = it.getArgument<ConsolePrintStatements>(1)
                    printStatements(redErrorConsole)
                }
            }
        }

        val logger by createForEachTest { QuietEventLogger(failureErrorMessageFormatter, runOptions, errorConsole) }

        on("when a 'task failed' event is posted") {
            val event = mock<TaskFailedEvent>()
            whenever(failureErrorMessageFormatter.formatErrorMessage(event, runOptions)).doReturn("Something went wrong.")

            logger.postEvent(event)

            it("prints the message to the console") {
                inOrder(redErrorConsole) {
                    verify(redErrorConsole).println()
                    verify(redErrorConsole).println("Something went wrong.")
                }
            }
        }

        on("when a task fails") {
            logger.onTaskFailed("some-task", "Some cleanup instructions")

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
    }
})
