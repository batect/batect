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

package batect.ui

import batect.model.DependencyGraph
import batect.model.events.TaskEvent
import batect.model.steps.TaskStep
import batect.testutils.CreateForEachTest
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object EventLoggerSpec : Spek({
    describe("an event logger") {
        val redErrorConsole by CreateForEachTest(this) { mock<Console>() }
        val errorConsole by CreateForEachTest(this) {
            mock<Console> {
                on { withColor(eq(ConsoleColor.Red), any()) } doAnswer {
                    val printStatements = it.getArgument<Console.() -> Unit>(1)
                    printStatements(redErrorConsole)
                }
            }
        }

        val logger by CreateForEachTest(this) {
            object : EventLogger(errorConsole) {
                override fun onDependencyGraphCreated(graph: DependencyGraph) = throw NotImplementedError()
                override fun onStartingTaskStep(step: TaskStep) = throw NotImplementedError()
                override fun postEvent(event: TaskEvent) = throw NotImplementedError()
            }
        }

        on("when the task fails") {
            logger.onTaskFailed("some-task")

            it("prints a message to the output") {
                inOrder(redErrorConsole) {
                    verify(redErrorConsole).println()
                    verify(redErrorConsole).print("The task ")
                    verify(redErrorConsole).printBold("some-task")
                    verify(redErrorConsole).println(" failed. See above for details.")
                }
            }
        }
    }
})
