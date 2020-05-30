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

import batect.docker.DockerContainerEnvironmentVariableProvider
import batect.docker.DockerException
import batect.docker.client.DockerExecClient
import batect.execution.CancellationContext
import batect.execution.RunAsCurrentUserConfigurationProvider
import batect.execution.RunOptions
import batect.execution.model.events.ContainerBecameReadyEvent
import batect.execution.model.events.RunningSetupCommandEvent
import batect.execution.model.events.SetupCommandExecutionErrorEvent
import batect.execution.model.events.SetupCommandFailedEvent
import batect.execution.model.events.SetupCommandsCompletedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.RunContainerSetupCommandsStep
import batect.ui.containerio.ContainerIOStreamingOptions

class RunContainerSetupCommandsStepRunner(
    private val execClient: DockerExecClient,
    private val environmentVariableProvider: DockerContainerEnvironmentVariableProvider,
    private val runAsCurrentUserConfigurationProvider: RunAsCurrentUserConfigurationProvider,
    private val runOptions: RunOptions,
    private val cancellationContext: CancellationContext,
    private val ioStreamingOptions: ContainerIOStreamingOptions
) {
    fun run(step: RunContainerSetupCommandsStep, eventSink: TaskEventSink) {
        if (step.container.setupCommands.isEmpty()) {
            eventSink.postEvent(ContainerBecameReadyEvent(step.container))
            return
        }

        val environmentVariables = environmentVariableProvider.environmentVariablesFor(
            step.container,
            step.config,
            runOptions.propagateProxyEnvironmentVariables,
            null
        )

        val userAndGroup = runAsCurrentUserConfigurationProvider.determineUserAndGroup(step.container)

        step.container.setupCommands.forEachIndexed { index, command ->
            try {
                eventSink.postEvent(RunningSetupCommandEvent(step.container, command, index))

                val result = execClient.run(
                    command.command,
                    step.dockerContainer,
                    environmentVariables,
                    step.container.privileged,
                    userAndGroup,
                    command.workingDirectory ?: step.container.workingDirectory,
                    ioStreamingOptions.stdoutForContainerSetupCommand(step.container, command, index),
                    cancellationContext
                )

                if (result.exitCode != 0) {
                    eventSink.postEvent(SetupCommandFailedEvent(step.container, command, result.exitCode, result.output))
                    return
                }
            } catch (e: DockerException) {
                eventSink.postEvent(SetupCommandExecutionErrorEvent(step.container, command, e.message ?: ""))
                return
            }
        }

        eventSink.postEvent(SetupCommandsCompletedEvent(step.container))
        eventSink.postEvent(ContainerBecameReadyEvent(step.container))
    }
}
