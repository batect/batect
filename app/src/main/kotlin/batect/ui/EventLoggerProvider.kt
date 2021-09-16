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

package batect.ui

import batect.config.Task
import batect.execution.ContainerDependencyGraph
import batect.os.ConsoleDimensions
import batect.os.ConsoleInfo
import batect.ui.containerio.TaskContainerOnlyIOStreamingOptions
import batect.ui.fancy.CleanupProgressDisplay
import batect.ui.fancy.FancyEventLogger
import batect.ui.fancy.StartupProgressDisplayProvider
import batect.ui.interleaved.InterleavedEventLogger
import batect.ui.interleaved.InterleavedOutput
import batect.ui.quiet.QuietEventLogger
import batect.ui.simple.SimpleEventLogger
import com.hypirion.io.RevivableInputStream
import java.io.PrintStream

class EventLoggerProvider(
    private val failureErrorMessageFormatter: FailureErrorMessageFormatter,
    private val console: Console,
    private val errorConsole: Console,
    private val stdout: PrintStream,
    private val stdin: RevivableInputStream,
    private val startupProgressDisplayProvider: StartupProgressDisplayProvider,
    private val consoleInfo: ConsoleInfo,
    private val consoleDimensions: ConsoleDimensions,
    private val requestedOutputStyle: OutputStyle?,
    private val disableColorOutput: Boolean
) {
    fun getEventLogger(task: Task, graph: ContainerDependencyGraph): EventLogger {
        return when (requestedOutputStyle) {
            OutputStyle.Quiet -> createQuietLogger(graph)
            OutputStyle.Fancy -> createFancyLogger(graph)
            OutputStyle.Simple -> createSimpleLogger(graph)
            OutputStyle.All -> createInterleavedLogger(task, graph)
            null -> {
                if (!consoleInfo.supportsInteractivity || disableColorOutput || consoleDimensions.current == null) {
                    createSimpleLogger(graph)
                } else {
                    createFancyLogger(graph)
                }
            }
        }
    }

    private fun createQuietLogger(graph: ContainerDependencyGraph) =
        QuietEventLogger(failureErrorMessageFormatter, errorConsole, createTaskContainerOnlyIOStreamingOptions(graph))

    private fun createFancyLogger(graph: ContainerDependencyGraph): FancyEventLogger =
        FancyEventLogger(failureErrorMessageFormatter, console, errorConsole, startupProgressDisplayProvider.createForDependencyGraph(graph), CleanupProgressDisplay(), graph.taskContainerNode.container, createTaskContainerOnlyIOStreamingOptions(graph))

    private fun createSimpleLogger(graph: ContainerDependencyGraph): SimpleEventLogger {
        return SimpleEventLogger(graph.allContainers, graph.taskContainerNode.container, failureErrorMessageFormatter, console, errorConsole, createTaskContainerOnlyIOStreamingOptions(graph))
    }

    private fun createTaskContainerOnlyIOStreamingOptions(graph: ContainerDependencyGraph) = TaskContainerOnlyIOStreamingOptions(graph.taskContainerNode.container, stdout, stdin, consoleInfo)

    private fun createInterleavedLogger(task: Task, graph: ContainerDependencyGraph): InterleavedEventLogger {
        val containers = graph.allContainers
        val output = InterleavedOutput(task.name, containers, console)

        return InterleavedEventLogger(graph.taskContainerNode.container, containers, output, failureErrorMessageFormatter)
    }
}
