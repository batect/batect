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

package batect.ui

import batect.execution.ContainerDependencyGraph
import batect.execution.RunOptions
import batect.ui.containerio.TaskContainerOnlyIOStreamingOptions
import batect.ui.fancy.CleanupProgressDisplay
import batect.ui.fancy.FancyEventLogger
import batect.ui.fancy.StartupProgressDisplayProvider
import batect.ui.interleaved.InterleavedEventLogger
import batect.ui.interleaved.InterleavedOutput
import batect.ui.quiet.QuietEventLogger
import batect.ui.simple.SimpleEventLogger
import batect.utils.mapToSet
import java.io.InputStream
import java.io.PrintStream

class EventLoggerProvider(
    private val failureErrorMessageFormatter: FailureErrorMessageFormatter,
    private val console: Console,
    private val errorConsole: Console,
    private val stdout: PrintStream,
    private val stdin: InputStream,
    private val startupProgressDisplayProvider: StartupProgressDisplayProvider,
    private val consoleInfo: ConsoleInfo,
    private val requestedOutputStyle: OutputStyle?,
    private val disableColorOutput: Boolean
) {
    fun getEventLogger(graph: ContainerDependencyGraph, runOptions: RunOptions): EventLogger {
        return when (requestedOutputStyle) {
            OutputStyle.Quiet -> createQuietLogger(graph, runOptions)
            OutputStyle.Fancy -> createFancyLogger(graph, runOptions)
            OutputStyle.Simple -> createSimpleLogger(graph, runOptions)
            OutputStyle.All -> createInterleavedLogger(graph, runOptions)
            null -> {
                if (!consoleInfo.supportsInteractivity || disableColorOutput) {
                    createSimpleLogger(graph, runOptions)
                } else {
                    createFancyLogger(graph, runOptions)
                }
            }
        }
    }

    private fun createQuietLogger(graph: ContainerDependencyGraph, runOptions: RunOptions) =
        QuietEventLogger(failureErrorMessageFormatter, runOptions, errorConsole, createTaskContainerOnlyIOStreamingOptions(graph))

    private fun createFancyLogger(graph: ContainerDependencyGraph, runOptions: RunOptions): FancyEventLogger =
        FancyEventLogger(failureErrorMessageFormatter, runOptions, console, errorConsole, startupProgressDisplayProvider.createForDependencyGraph(graph), CleanupProgressDisplay(), graph.taskContainerNode.container, createTaskContainerOnlyIOStreamingOptions(graph))

    private fun createSimpleLogger(graph: ContainerDependencyGraph, runOptions: RunOptions): SimpleEventLogger {
        val containers = graph.allNodes.mapToSet { it.container }
        return SimpleEventLogger(containers, graph.taskContainerNode.container, failureErrorMessageFormatter, runOptions, console, errorConsole, createTaskContainerOnlyIOStreamingOptions(graph))
    }

    private fun createTaskContainerOnlyIOStreamingOptions(graph: ContainerDependencyGraph) = TaskContainerOnlyIOStreamingOptions(graph.taskContainerNode.container, stdout, stdin, consoleInfo)

    private fun createInterleavedLogger(graph: ContainerDependencyGraph, runOptions: RunOptions): InterleavedEventLogger {
        val containers = graph.allNodes.mapToSet { it.container }
        val output = InterleavedOutput(graph.task.name, containers, console)

        return InterleavedEventLogger(graph.taskContainerNode.container, containers, output, failureErrorMessageFormatter, runOptions)
    }
}
