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
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object EventLoggerProviderSpec : Spek({
    describe("an event logger provider") {
        val console = mock<Console>()
        val errorConsole = mock<Console>()
        val graph = mock<DependencyGraph>()

        on("when the console supports interactivity") {
            val startupProgressDisplay = mock<StartupProgressDisplay>()
            val startupProgressDisplayProvider = mock<StartupProgressDisplayProvider> {
                on { createForDependencyGraph(graph) } doReturn startupProgressDisplay
            }

            val consoleInfo = mock<ConsoleInfo> {
                on { supportsInteractivity } doReturn true
            }

            val provider = EventLoggerProvider(console, errorConsole, startupProgressDisplayProvider, consoleInfo)
            val logger = provider.getEventLogger(graph)

            it("returns a fancy event logger") {
                assertThat(logger, isA<FancyEventLogger>())
            }

            it("passes the console to the event logger") {
                assertThat((logger as FancyEventLogger).console, equalTo(console))
            }

            it("passes the error console to the event logger") {
                assertThat((logger as FancyEventLogger).errorConsole, equalTo(errorConsole))
            }

            it("passes the startup progress display to the event logger") {
                assertThat((logger as FancyEventLogger).startupProgressDisplay, equalTo(startupProgressDisplay))
            }
        }

        on("when the console does not support interactivity") {
            val startupProgressDisplayProvider = mock<StartupProgressDisplayProvider>()

            val consoleInfo = mock<ConsoleInfo> {
                on { supportsInteractivity } doReturn false
            }

            val provider = EventLoggerProvider(console, errorConsole, startupProgressDisplayProvider, consoleInfo)
            val logger = provider.getEventLogger(graph)

            it("returns a simple event logger") {
                assertThat(logger, isA<SimpleEventLogger>())
            }

            it("passes the console to the event logger") {
                assertThat((logger as SimpleEventLogger).console, equalTo(console))
            }

            it("passes the error console to the event logger") {
                assertThat((logger as SimpleEventLogger).errorConsole, equalTo(errorConsole))
            }
        }
    }
})
