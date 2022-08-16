/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.execution.model.steps.runners

import batect.config.Container
import batect.config.SetupCommand
import batect.docker.DockerContainerEnvironmentVariableProvider
import batect.dockerclient.ContainerExecSpec
import batect.dockerclient.ContainerReference
import batect.dockerclient.DockerClient
import batect.dockerclient.DockerClientException
import batect.dockerclient.UserAndGroup
import batect.dockerclient.io.SinkTextOutput
import batect.execution.RunAsCurrentUserConfigurationProvider
import batect.execution.model.events.ContainerBecameReadyEvent
import batect.execution.model.events.RunningSetupCommandEvent
import batect.execution.model.events.SetupCommandExecutionErrorEvent
import batect.execution.model.events.SetupCommandFailedEvent
import batect.execution.model.events.SetupCommandsCompletedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.RunContainerSetupCommandsStep
import batect.io.Tee
import batect.logging.Logger
import batect.primitives.CancellationContext
import batect.primitives.runBlocking
import batect.ui.containerio.ContainerIOStreamingOptions
import kotlinx.coroutines.runBlocking
import okio.Buffer

class RunContainerSetupCommandsStepRunner(
    private val dockerClient: DockerClient,
    private val environmentVariableProvider: DockerContainerEnvironmentVariableProvider,
    private val runAsCurrentUserConfigurationProvider: RunAsCurrentUserConfigurationProvider,
    private val cancellationContext: CancellationContext,
    private val ioStreamingOptions: ContainerIOStreamingOptions,
    private val logger: Logger
) {
    fun run(step: RunContainerSetupCommandsStep, eventSink: TaskEventSink) {
        if (step.container.setupCommands.isEmpty()) {
            eventSink.postEvent(ContainerBecameReadyEvent(step.container))
            return
        }

        val environmentVariables = environmentVariableProvider.environmentVariablesFor(step.container, null)
        val userAndGroup = runAsCurrentUserConfigurationProvider.determineUserAndGroup(step.container)

        step.container.setupCommands.forEachIndexed { index, command ->
            try {
                eventSink.postEvent(RunningSetupCommandEvent(step.container, command, index))

                val result = runSetupCommand(command, index, environmentVariables, userAndGroup, step.container, step.dockerContainer.reference)

                if (result.exitCode != 0L) {
                    eventSink.postEvent(SetupCommandFailedEvent(step.container, command, result.exitCode, result.output.readUtf8()))
                    return
                }
            } catch (e: DockerClientException) {
                logger.error {
                    message("Running setup command failed.")
                    exception(e)
                    data("containerId", step.dockerContainer.reference.id)
                    data("commandIndex", index)
                }

                eventSink.postEvent(SetupCommandExecutionErrorEvent(step.container, command, e.message ?: ""))
                return
            }
        }

        eventSink.postEvent(SetupCommandsCompletedEvent(step.container))
        eventSink.postEvent(ContainerBecameReadyEvent(step.container))
    }

    private fun runSetupCommand(
        setupCommand: SetupCommand,
        setupCommandIndex: Int,
        environmentVariables: Map<String, String>,
        userAndGroup: UserAndGroup?,
        container: Container,
        dockerContainer: ContainerReference
    ): ExecutionResult {
        val builder = ContainerExecSpec.Builder(dockerContainer)
            .withCommand(setupCommand.command.parsedCommand)
            .withEnvironmentVariables(environmentVariables)
            .withStdoutAttached()
            .withStderrAttached()
            .withTTYAttached()

        if (setupCommand.workingDirectory != null) {
            builder.withWorkingDirectory(setupCommand.workingDirectory)
        } else if (container.workingDirectory != null) {
            builder.withWorkingDirectory(container.workingDirectory)
        }

        if (userAndGroup != null) {
            builder.withUserAndGroup(userAndGroup)
        }

        if (container.privileged) {
            builder.withPrivileged()
        }

        val exec = runBlocking { dockerClient.createExec(builder.build()) }
        val uiStdout = ioStreamingOptions.stdoutForContainerSetupCommand(container, setupCommand, setupCommandIndex)
        val stdoutBuffer = Buffer()
        val combinedStdout = if (uiStdout == null) stdoutBuffer else Tee(uiStdout, stdoutBuffer)

        val exitCode = cancellationContext.runBlocking {
            dockerClient.startAndAttachToExec(
                exec,
                true,
                SinkTextOutput(combinedStdout),
                SinkTextOutput(combinedStdout),
                null
            )

            dockerClient.inspectExec(exec).exitCode!!
        }

        return ExecutionResult(exitCode, stdoutBuffer)
    }

    private data class ExecutionResult(val exitCode: Long, val output: Buffer)
}
