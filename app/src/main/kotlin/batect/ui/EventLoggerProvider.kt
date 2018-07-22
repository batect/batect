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
import batect.utils.mapToSet

class EventLoggerProvider(
    private val failureErrorMessageFormatter: FailureErrorMessageFormatter,
    private val console: Console,
    private val errorConsole: Console,
    private val startupProgressDisplayProvider: StartupProgressDisplayProvider,
    private val consoleInfo: ConsoleInfo,
    private val requestedOutputStyle: OutputStyle?,
    private val disableColorOutput: Boolean
) {
    fun getEventLogger(graph: ContainerDependencyGraph, runOptions: RunOptions): EventLogger {
        when (requestedOutputStyle) {
            OutputStyle.Quiet -> return createQuietLogger(runOptions)
            OutputStyle.Fancy -> return createFancyLogger(graph, runOptions)
            OutputStyle.Simple -> return createSimpleLogger(graph, runOptions)
            null -> {
                if (!consoleInfo.supportsInteractivity || disableColorOutput) {
                    return createSimpleLogger(graph, runOptions)
                } else {
                    return createFancyLogger(graph, runOptions)
                }
            }
        }
    }

    private fun createQuietLogger(runOptions: RunOptions) =
        QuietEventLogger(failureErrorMessageFormatter, runOptions, errorConsole)

    private fun createFancyLogger(graph: ContainerDependencyGraph, runOptions: RunOptions): FancyEventLogger =
        FancyEventLogger(failureErrorMessageFormatter, runOptions, console, errorConsole, startupProgressDisplayProvider.createForDependencyGraph(graph), CleanupProgressDisplay())

    private fun createSimpleLogger(graph: ContainerDependencyGraph, runOptions: RunOptions): SimpleEventLogger {
        val containers = graph.allNodes.mapToSet { it.container }
        return SimpleEventLogger(containers, failureErrorMessageFormatter, runOptions, console, errorConsole)
    }
}
