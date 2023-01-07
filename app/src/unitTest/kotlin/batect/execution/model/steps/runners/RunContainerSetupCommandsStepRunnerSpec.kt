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
import batect.docker.DockerContainer
import batect.docker.DockerContainerEnvironmentVariableProvider
import batect.dockerclient.ContainerExecInspectionResult
import batect.dockerclient.ContainerExecReference
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
import batect.os.Command
import batect.primitives.CancellationContext
import batect.testutils.beforeEachTestSuspend
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.itSuspend
import batect.ui.containerio.ContainerIOStreamingOptions
import com.natpryce.hamkrest.assertion.assertThat
import okio.Buffer
import okio.Sink
import okio.buffer
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isA
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.notNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RunContainerSetupCommandsStepRunnerSpec : Spek({
    describe("running a 'run setup commands' step") {
        val dockerClient by createForEachTest { mock<DockerClient>() }
        val environmentVariableProvider by createForEachTest { mock<DockerContainerEnvironmentVariableProvider>() }
        val userAndGroup = UserAndGroup(456, 789)

        val runAsCurrentUserConfigurationProvider = mock<RunAsCurrentUserConfigurationProvider> {
            on { determineUserAndGroup(any()) } doReturn userAndGroup
        }

        val cancellationContext by createForEachTest { mock<CancellationContext>() }
        val ioStreamingOptions by createForEachTest { mock<ContainerIOStreamingOptions>() }
        val eventSink by createForEachTest { mock<TaskEventSink>() }
        val logger by createLoggerForEachTest()

        val runner by createForEachTest {
            RunContainerSetupCommandsStepRunner(
                dockerClient,
                environmentVariableProvider,
                runAsCurrentUserConfigurationProvider,
                cancellationContext,
                ioStreamingOptions,
                logger,
            )
        }

        given("the container has no setup commands") {
            val container = Container("the-container", imageSourceDoesNotMatter(), setupCommands = emptyList())
            val dockerContainer = DockerContainer(ContainerReference("some-container-id"), "some-container-name")
            val step = RunContainerSetupCommandsStep(container, dockerContainer)

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
            val dockerContainer = DockerContainer(ContainerReference("some-container-id"), "some-container-name")
            val step = RunContainerSetupCommandsStep(container, dockerContainer)

            val environmentVariablesToUse = mapOf("SOME_VAR" to "some value")
            val outputSink by createForEachTest { Buffer() }
            val execInstance by createForEachTest { ContainerExecReference("the-exec-instance") }

            beforeEachTestSuspend {
                whenever(environmentVariableProvider.environmentVariablesFor(container, null)).doReturn(environmentVariablesToUse)
                whenever(ioStreamingOptions.stdoutForContainerSetupCommand(container, setupCommand, 0)).thenReturn(outputSink)
                whenever(dockerClient.createExec(any())).doReturn(execInstance)

                whenever(dockerClient.startAndAttachToExec(any(), any(), any(), any(), anyOrNull())).doAnswer { invocation ->
                    val stdout = invocation.getArgument<SinkTextOutput>(2)

                    stdout.sink.buffer().use { buffer ->
                        buffer.writeUtf8("Some output from the command")
                    }

                    Unit
                }
            }

            given("the command succeeds") {
                beforeEachTestSuspend {
                    whenever(dockerClient.inspectExec(execInstance)).doReturn(ContainerExecInspectionResult(0, false))
                }

                beforeEachTest { runner.run(step, eventSink) }

                it("emits a 'running setup command' event") {
                    verify(eventSink).postEvent(RunningSetupCommandEvent(container, setupCommand, 0))
                }

                itSuspend("creates the exec instance with the expected configuration") {
                    val captor = argumentCaptor<ContainerExecSpec>()
                    verify(dockerClient).createExec(captor.capture())

                    val spec = captor.firstValue
                    assertThat(spec.command, equalTo(command.parsedCommand))
                    assertThat(spec.container, equalTo(ContainerReference("some-container-id")))
                    assertThat(spec.environmentVariables, equalTo(environmentVariablesToUse))
                    assertThat(spec.privileged, equalTo(container.privileged))
                    assertThat(spec.userAndGroup, equalTo(userAndGroup))
                    assertThat(spec.workingDirectory, equalTo(container.workingDirectory))
                }

                itSuspend("runs the created exec instance") {
                    verify(dockerClient).startAndAttachToExec(eq(execInstance), eq(true), notNull(), notNull(), isNull())
                }

                it("streams I/O from the exec instance to the provided output stream") {
                    assertThat(outputSink.readUtf8(), equalTo("Some output from the command"))
                }

                it("emits a 'setup commands completed' event") {
                    verify(eventSink).postEvent(SetupCommandsCompletedEvent(container))
                }

                it("emits a 'container ready' event") {
                    verify(eventSink).postEvent(ContainerBecameReadyEvent(container))
                }

                itSuspend("emits the 'running setup command' event before running the command") {
                    inOrder(eventSink, dockerClient) {
                        verify(eventSink).postEvent(isA<RunningSetupCommandEvent>())
                        verify(dockerClient).startAndAttachToExec(any(), any(), any(), any(), anyOrNull())
                    }
                }

                itSuspend("emits the 'setup commands completed' event after running the command") {
                    inOrder(eventSink, dockerClient) {
                        verify(dockerClient).startAndAttachToExec(any(), any(), any(), any(), anyOrNull())
                        verify(eventSink).postEvent(isA<SetupCommandsCompletedEvent>())
                    }
                }
            }

            given("the command returns a non-zero exit code") {
                beforeEachTestSuspend {
                    whenever(dockerClient.inspectExec(execInstance)).doReturn(ContainerExecInspectionResult(123, false))
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
                beforeEachTestSuspend {
                    whenever(dockerClient.startAndAttachToExec(any(), any(), any(), any(), anyOrNull())).doThrow(DockerClientException("Something went wrong."))
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
            val dockerContainer = DockerContainer(ContainerReference("some-container-id"), "some-container-name")
            val step = RunContainerSetupCommandsStep(container, dockerContainer)

            val environmentVariablesToUse = mapOf("SOME_VAR" to "some value")
            val outputSink by createForEachTest { mock<Sink>() }

            beforeEachTest {
                whenever(environmentVariableProvider.environmentVariablesFor(container, null)).doReturn(environmentVariablesToUse)
                whenever(ioStreamingOptions.stdoutForContainerSetupCommand(container, setupCommand, 0)).thenReturn(outputSink)
            }

            given("the command succeeds") {
                beforeEachTestSuspend {
                    whenever(dockerClient.createExec(any())).doReturn(ContainerExecReference("some-exec-instance"))
                    whenever(dockerClient.inspectExec(any())).doReturn(ContainerExecInspectionResult(0, false))
                }

                beforeEachTest { runner.run(step, eventSink) }

                itSuspend("runs the command with the working directory specified on the setup command") {
                    verify(dockerClient).createExec(argThat { workingDirectory == setupCommand.workingDirectory })
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
            val dockerContainer = DockerContainer(ContainerReference("some-container-id"), "some-container-name")
            val step = RunContainerSetupCommandsStep(container, dockerContainer)

            val environmentVariablesToUse = mapOf("SOME_VAR" to "some value")
            val execInstance1 = ContainerExecReference("command-1-exec")
            val execInstance2 = ContainerExecReference("command-2-exec")
            val execInstance3 = ContainerExecReference("command-3-exec")

            beforeEachTestSuspend {
                whenever(environmentVariableProvider.environmentVariablesFor(container, null)).doReturn(environmentVariablesToUse)

                whenever(dockerClient.createExec(argThat { command == command1.parsedCommand })).doReturn(execInstance1)
                whenever(dockerClient.createExec(argThat { command == command2.parsedCommand })).doReturn(execInstance2)
                whenever(dockerClient.createExec(argThat { command == command3.parsedCommand })).doReturn(execInstance3)
            }

            given("all of them succeed") {
                beforeEachTestSuspend {
                    whenever(dockerClient.inspectExec(any())).doReturn(ContainerExecInspectionResult(0, false))
                }

                beforeEachTest { runner.run(step, eventSink) }

                itSuspend("emits a 'running setup command' event before running each command in the order provided") {
                    inOrder(eventSink, dockerClient) {
                        verify(eventSink).postEvent(RunningSetupCommandEvent(container, setupCommand1, 0))
                        verify(dockerClient).startAndAttachToExec(eq(execInstance1), any(), any(), any(), anyOrNull())
                        verify(eventSink).postEvent(RunningSetupCommandEvent(container, setupCommand2, 1))
                        verify(dockerClient).startAndAttachToExec(eq(execInstance2), any(), any(), any(), anyOrNull())
                        verify(eventSink).postEvent(RunningSetupCommandEvent(container, setupCommand3, 2))
                        verify(dockerClient).startAndAttachToExec(eq(execInstance3), any(), any(), any(), anyOrNull())
                    }
                }

                itSuspend("emits 'setup commands completed' and 'container ready' events after running the last command") {
                    inOrder(eventSink, dockerClient) {
                        verify(dockerClient).startAndAttachToExec(eq(execInstance3), any(), any(), any(), anyOrNull())
                        verify(eventSink).postEvent(SetupCommandsCompletedEvent(container))
                        verify(eventSink).postEvent(ContainerBecameReadyEvent(container))
                    }
                }
            }

            given("one of them fails") {
                beforeEachTestSuspend {
                    whenever(dockerClient.inspectExec(execInstance1)).doReturn(ContainerExecInspectionResult(0, false))
                    whenever(dockerClient.inspectExec(execInstance3)).doReturn(ContainerExecInspectionResult(0, false))
                }

                given("one of them fails with a non-zero exit code") {
                    beforeEachTestSuspend {
                        whenever(dockerClient.startAndAttachToExec(eq(execInstance2), any(), any(), any(), anyOrNull())).doAnswer { invocation ->
                            val stdout = invocation.getArgument<SinkTextOutput>(2)

                            stdout.sink.buffer().use { buffer ->
                                buffer.writeUtf8("Some output from the failing command")
                            }

                            Unit
                        }

                        whenever(dockerClient.inspectExec(execInstance2)).doReturn(ContainerExecInspectionResult(123, false))
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

                    itSuspend("does not run subsequent commands") {
                        verify(dockerClient, never()).startAndAttachToExec(eq(execInstance3), any(), any(), any(), anyOrNull())
                    }
                }

                given("one of them fails because the command cannot be run") {
                    beforeEachTestSuspend {
                        whenever(dockerClient.startAndAttachToExec(eq(execInstance2), any(), any(), any(), anyOrNull())).doThrow(DockerClientException("Something went wrong."))
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

                    itSuspend("does not run subsequent commands") {
                        verify(dockerClient, never()).startAndAttachToExec(eq(execInstance3), any(), any(), any(), anyOrNull())
                    }
                }
            }
        }
    }
})
