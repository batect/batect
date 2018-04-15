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

package batect.ui

import batect.execution.ContainerDependencyGraph
import batect.execution.RunOptions
import batect.ui.fancy.CleanupProgressDisplay
import batect.ui.fancy.FancyEventLogger
import batect.ui.fancy.StartupProgressDisplayProvider
import batect.ui.quiet.QuietEventLogger
import batect.ui.simple.SimpleEventLogger

class EventLoggerProvider(
    private val failureErrorMessageFormatter: FailureErrorMessageFormatter,
    private val console: Console,
    private val errorConsole: Console,
    private val startupProgressDisplayProvider: StartupProgressDisplayProvider,
    private val consoleInfo: ConsoleInfo,
    private val forceSimpleOutputMode: Boolean,
    private val forceQuietOutputMode: Boolean
) {
    fun getEventLogger(graph: ContainerDependencyGraph, runOptions: RunOptions): EventLogger {
        if (forceQuietOutputMode) {
            return QuietEventLogger(failureErrorMessageFormatter, runOptions, errorConsole)
        }

        if (consoleInfo.supportsInteractivity && !forceSimpleOutputMode) {
            return FancyEventLogger(failureErrorMessageFormatter, runOptions, console, errorConsole, startupProgressDisplayProvider.createForDependencyGraph(graph), CleanupProgressDisplay())
        }

        return SimpleEventLogger(failureErrorMessageFormatter, runOptions, console, errorConsole)
    }
}
