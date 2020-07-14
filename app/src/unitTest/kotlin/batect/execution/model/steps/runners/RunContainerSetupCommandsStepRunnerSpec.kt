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

import batect.config.Container
import batect.config.SetupCommand
import batect.docker.DockerContainer
import batect.docker.DockerContainerEnvironmentVariableProvider
import batect.docker.DockerException
import batect.docker.DockerExecResult
import batect.docker.UserAndGroup
import batect.docker.client.DockerExecClient
import batect.execution.CleanupOption
import batect.execution.ContainerRuntimeConfiguration
import batect.execution.RunAsCurrentUserConfigurationProvider
import batect.execution.RunOptions
import batect.execution.model.events.ContainerBecameReadyEvent
import batect.execution.model.events.RunningSetupCommandEvent
import batect.execution.model.events.SetupCommandExecutionErrorEvent
import batect.execution.model.events.SetupCommandFailedEvent
import batect.execution.model.events.SetupCommandsCompletedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.RunContainerSetupCommandsStep
import batect.os.Command
import batect.primitives.CancellationContext
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.ui.containerio.ContainerIOStreamingOptions
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import okio.Sink
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RunContainerSetupCommandsStepRunnerSpec : Spek({
    describe("running a 'run setup commands' step") {
        val execClient by createForEachTest { mock<DockerExecClient>() }
        val environmentVariableProvider by createForEachTest { mock<DockerContainerEnvironmentVariableProvider>() }
        val userAndGroup = UserAndGroup(456, 789)

        val runAsCurrentUserConfigurationProvider = mock<RunAsCurrentUserConfigurationProvider> {
            on { determineUserAndGroup(any()) } doReturn userAndGroup
        }

        val runOptions = RunOptions("some-task", emptyList(), CleanupOption.Cleanup, CleanupOption.Cleanup, true, emptyMap())
        val cancellationContext by createForEachTest { mock<CancellationContext>() }
        val ioStreamingOptions by createForEachTest { mock<ContainerIOStreamingOptions>() }
        val eventSink by createForEachTest { mock<TaskEventSink>() }

        val runner by createForEachTest { RunContainerSetupCommandsStepRunner(execClient, environmentVariableProvider, runAsCurrentUserConfigurationProvider, runOptions, cancellationContext, ioStreamingOptions) }

        given("the container has no setup commands") {
            val container = Container("the-container", imageSourceDoesNotMatter(), setupCommands = emptyList())
            val config = ContainerRuntimeConfiguration(null, null, null, emptyMap(), emptySet())
            val dockerContainer = DockerContainer("some-container-id")
            val step = RunContainerSetupCommandsStep(container, config, dockerContainer)

            beforeEachTest { runner.run(step, eventSink) }

            it("emits a 'container ready' event") {
                verify(eventSink).postEvent(ContainerBecameReadyEvent(container))
            }

            it("does not emit any other events") {
                verify(eventSink, times(1)).postEvent(any())
            }
        }

        given("the container has a single setup command with no specific working directory") {
            val command = Command.parse("./do the-thing")
            val setupCommand = SetupCommand(command)
            val container = Container("the-container", imageSourceDoesNotMatter(), setupCommands = listOf(setupCommand), workingDirectory = "/some/work/dir")
            val config = ContainerRuntimeConfiguration(null, null, null, emptyMap(), emptySet())
            val dockerContainer = DockerContainer("some-container-id")
            val step = RunContainerSetupCommandsStep(container, config, dockerContainer)

            val environmentVariablesToUse = mapOf("SOME_VAR" to "some value")
            val outputSink by createForEachTest { mock<Sink>() }

            beforeEachTest {
                whenever(environmentVariableProvider.environmentVariablesFor(container, config, runOptions.propagateProxyEnvironmentVariables, null)).doReturn(environmentVariablesToUse)
                whenever(ioStreamingOptions.stdoutForContainerSetupCommand(container, setupCommand, 0)).thenReturn(outputSink)
            }

            given("the command succeeds") {
                beforeEachTest {
                    whenever(execClient.run(any(), any(), any(), any(), any(), any(), any(), any())).doReturn(DockerExecResult(0, "Some output from the command"))
                }

                beforeEachTest { runner.run(step, eventSink) }

                it("emits a 'running setup command' event") {
                    verify(eventSink).postEvent(RunningSetupCommandEvent(container, setupCommand, 0))
                }

                it("runs the command with the working directory specified on the container") {
                    verify(execClient).run(
                        command,
                        dockerContainer,
                        environmentVariablesToUse,
                        container.privileged,
                        userAndGroup,
                        container.workingDirectory,
                        outputSink,
                        cancellationContext
                    )
                }

                it("emits a 'setup commands completed' event") {
                    verify(eventSink).postEvent(SetupCommandsCompletedEvent(container))
                }

                it("emits a 'container ready' event") {
                    verify(eventSink).postEvent(ContainerBecameReadyEvent(container))
                }

                it("emits the 'running setup command' event before running the command") {
                    inOrder(eventSink, execClient) {
                        verify(eventSink).postEvent(isA<RunningSetupCommandEvent>())
                        verify(execClient).run(any(), any(), any(), any(), any(), any(), any(), any())
                    }
                }

                it("emits the 'setup commands completed' event after running the command") {
                    inOrder(eventSink, execClient) {
                        verify(execClient).run(any(), any(), any(), any(), any(), any(), any(), any())
                        verify(eventSink).postEvent(isA<SetupCommandsCompletedEvent>())
                    }
                }
            }

            given("the command returns a non-zero exit code") {
                beforeEachTest {
                    whenever(execClient.run(any(), any(), any(), any(), any(), any(), any(), any())).doReturn(DockerExecResult(123, "Some output from the command"))
                }

                beforeEachTest { runner.run(step, eventSink) }

                it("emits a 'running setup command' event") {
                    verify(eventSink).postEvent(RunningSetupCommandEvent(container, setupCommand, 0))
                }

                it("emits a 'setup command failed' event") {
                    verify(eventSink).postEvent(SetupCommandFailedEvent(container, setupCommand, 123, "Some output from the command"))
                }

                it("does not emit a 'setup commands completed' event") {
                    verify(eventSink, never()).postEvent(isA<SetupCommandsCompletedEvent>())
                }

                it("does not emit a 'container ready' event") {
                    verify(eventSink, never()).postEvent(isA<ContainerBecameReadyEvent>())
                }
            }

            given("the command cannot be run") {
                beforeEachTest {
                    whenever(execClient.run(any(), any(), any(), any(), any(), any(), any(), any())).doThrow(DockerException("Something went wrong."))
                }

                beforeEachTest { runner.run(step, eventSink) }

                it("emits a 'running setup command' event") {
                    verify(eventSink).postEvent(RunningSetupCommandEvent(container, setupCommand, 0))
                }

                it("emits a 'setup command execution error' event") {
                    verify(eventSink).postEvent(SetupCommandExecutionErrorEvent(container, setupCommand, "Something went wrong."))
                }

                it("does not emit a 'setup commands completed' event") {
                    verify(eventSink, never()).postEvent(isA<SetupCommandsCompletedEvent>())
                }

                it("does not emit a 'container ready' event") {
                    verify(eventSink, never()).postEvent(isA<ContainerBecameReadyEvent>())
                }
            }
        }

        given("the container has a single setup command with a specific working directory") {
            val command = Command.parse("./do the-thing")
            val setupCommand = SetupCommand(command, "/some/other/command/work/dir")
            val container = Container("the-container", imageSourceDoesNotMatter(), setupCommands = listOf(setupCommand), workingDirectory = "/some/container/work/dir")
            val config = ContainerRuntimeConfiguration(null, null, null, emptyMap(), emptySet())
            val dockerContainer = DockerContainer("some-container-id")
            val step = RunContainerSetupCommandsStep(container, config, dockerContainer)

            val environmentVariablesToUse = mapOf("SOME_VAR" to "some value")
            val outputSink by createForEachTest { mock<Sink>() }

            beforeEachTest {
                whenever(environmentVariableProvider.environmentVariablesFor(container, config, runOptions.propagateProxyEnvironmentVariables, null)).doReturn(environmentVariablesToUse)
                whenever(ioStreamingOptions.stdoutForContainerSetupCommand(container, setupCommand, 0)).thenReturn(outputSink)
            }

            given("the command succeeds") {
                beforeEachTest {
                    whenever(execClient.run(any(), any(), any(), any(), any(), any(), any(), any())).doReturn(DockerExecResult(0, "Some output from the command"))
                }

                beforeEachTest { runner.run(step, eventSink) }

                it("runs the command with the working directory specified on the setup command") {
                    verify(execClient).run(
                        command,
                        dockerContainer,
                        environmentVariablesToUse,
                        container.privileged,
                        userAndGroup,
                        setupCommand.workingDirectory,
                        outputSink,
                        cancellationContext
                    )
                }
            }
        }

        given("the container has multiple setup commands") {
            val command1 = Command.parse("./do the-first-thing")
            val command2 = Command.parse("./do the-second-thing")
            val command3 = Command.parse("./do the-third-thing")
            val setupCommand1 = SetupCommand(command1)
            val setupCommand2 = SetupCommand(command2)
            val setupCommand3 = SetupCommand(command3)
            val container = Container("the-container", imageSourceDoesNotMatter(), setupCommands = listOf(setupCommand1, setupCommand2, setupCommand3), workingDirectory = "/some/work/dir")
            val config = ContainerRuntimeConfiguration(null, null, null, emptyMap(), emptySet())
            val dockerContainer = DockerContainer("some-container-id")
            val step = RunContainerSetupCommandsStep(container, config, dockerContainer)

            val environmentVariablesToUse = mapOf("SOME_VAR" to "some value")
            val outputSink1 by createForEachTest { mock<Sink>() }
            val outputSink2 by createForEachTest { mock<Sink>() }
            val outputSink3 by createForEachTest { mock<Sink>() }

            beforeEachTest {
                whenever(environmentVariableProvider.environmentVariablesFor(container, config, runOptions.propagateProxyEnvironmentVariables, null)).doReturn(environmentVariablesToUse)
                whenever(ioStreamingOptions.stdoutForContainerSetupCommand(container, setupCommand1, 0)).doReturn(outputSink1)
                whenever(ioStreamingOptions.stdoutForContainerSetupCommand(container, setupCommand2, 1)).doReturn(outputSink2)
                whenever(ioStreamingOptions.stdoutForContainerSetupCommand(container, setupCommand3, 2)).doReturn(outputSink3)
            }

            given("all of them succeed") {
                beforeEachTest {
                    whenever(execClient.run(any(), any(), any(), any(), any(), any(), any(), any())).doReturn(DockerExecResult(0, "Some output from the command"))
                }

                beforeEachTest { runner.run(step, eventSink) }

                it("emits a 'running setup command' event before running each command in the order provided") {
                    inOrder(eventSink, execClient) {
                        verify(eventSink).postEvent(RunningSetupCommandEvent(container, setupCommand1, 0))
                        verify(execClient).run(command1, dockerContainer, environmentVariablesToUse, container.privileged, userAndGroup, container.workingDirectory, outputSink1, cancellationContext)
                        verify(eventSink).postEvent(RunningSetupCommandEvent(container, setupCommand2, 1))
                        verify(execClient).run(command2, dockerContainer, environmentVariablesToUse, container.privileged, userAndGroup, container.workingDirectory, outputSink2, cancellationContext)
                        verify(eventSink).postEvent(RunningSetupCommandEvent(container, setupCommand3, 2))
                        verify(execClient).run(command3, dockerContainer, environmentVariablesToUse, container.privileged, userAndGroup, container.workingDirectory, outputSink3, cancellationContext)
                    }
                }

                it("emits 'setup commands completed' and 'container ready' events after running the last command") {
                    inOrder(eventSink, execClient) {
                        verify(execClient).run(command3, dockerContainer, environmentVariablesToUse, container.privileged, userAndGroup, container.workingDirectory, outputSink3, cancellationContext)
                        verify(eventSink).postEvent(SetupCommandsCompletedEvent(container))
                        verify(eventSink).postEvent(ContainerBecameReadyEvent(container))
                    }
                }
            }

            given("one of them fails") {
                beforeEachTest {
                    whenever(execClient.run(eq(command1), any(), any(), any(), any(), any(), any(), any())).doReturn(DockerExecResult(0, "Some output from the command"))
                    whenever(execClient.run(eq(command3), any(), any(), any(), any(), any(), any(), any())).doReturn(DockerExecResult(0, "Some output from the command"))
                }

                given("one of them fails with a non-zero exit code") {
                    beforeEachTest {
                        whenever(execClient.run(eq(command2), any(), any(), any(), any(), any(), any(), any())).doReturn(DockerExecResult(123, "Some output from the failing command"))
                    }

                    beforeEachTest { runner.run(step, eventSink) }

                    it("emits a 'running setup command' event for the commands before the failing command") {
                        verify(eventSink).postEvent(RunningSetupCommandEvent(container, setupCommand1, 0))
                    }

                    it("emits a 'running setup command' event for the failing command") {
                        verify(eventSink).postEvent(RunningSetupCommandEvent(container, setupCommand2, 1))
                    }

                    it("emits a 'setup command failed' event for the failing command") {
                        verify(eventSink).postEvent(SetupCommandFailedEvent(container, setupCommand2, 123, "Some output from the failing command"))
                    }

                    it("does not emit a 'setup commands completed' event") {
                        verify(eventSink, never()).postEvent(isA<SetupCommandsCompletedEvent>())
                    }

                    it("does not emit a 'container ready' event") {
                        verify(eventSink, never()).postEvent(isA<ContainerBecameReadyEvent>())
                    }

                    it("does not run subsequent commands") {
                        verify(execClient, never()).run(command3, dockerContainer, environmentVariablesToUse, container.privileged, userAndGroup, container.workingDirectory, outputSink3, cancellationContext)
                    }
                }

                given("one of them fails because the command cannot be run") {
                    beforeEachTest {
                        whenever(execClient.run(eq(command2), any(), any(), any(), any(), any(), any(), any())).doThrow(DockerException("Something went wrong."))
                    }

                    beforeEachTest { runner.run(step, eventSink) }

                    it("emits a 'running setup command' event for the commands before the failing command") {
                        verify(eventSink).postEvent(RunningSetupCommandEvent(container, setupCommand1, 0))
                    }

                    it("emits a 'running setup command' event for the failing command") {
                        verify(eventSink).postEvent(RunningSetupCommandEvent(container, setupCommand2, 1))
                    }

                    it("emits a 'setup command execution error' event") {
                        verify(eventSink).postEvent(SetupCommandExecutionErrorEvent(container, setupCommand2, "Something went wrong."))
                    }

                    it("does not emit a 'setup commands completed' event") {
                        verify(eventSink, never()).postEvent(isA<SetupCommandsCompletedEvent>())
                    }

                    it("does not emit a 'container ready' event") {
                        verify(eventSink, never()).postEvent(isA<ContainerBecameReadyEvent>())
                    }

                    it("does not run subsequent commands") {
                        verify(execClient, never()).run(command3, dockerContainer, environmentVariablesToUse, container.privileged, userAndGroup, container.workingDirectory, outputSink3, cancellationContext)
                    }
                }
            }
        }
    }
})
