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

import batect.config.BuildImage
import batect.config.Container
import batect.config.PullImage
import batect.docker.DockerContainer
import batect.execution.BehaviourAfterFailure
import batect.execution.RunOptions
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerCreationFailedEvent
import batect.execution.model.events.ContainerDidNotBecomeHealthyEvent
import batect.execution.model.events.ContainerRemovalFailedEvent
import batect.execution.model.events.ContainerRunFailedEvent
import batect.execution.model.events.ContainerStartFailedEvent
import batect.execution.model.events.ContainerStopFailedEvent
import batect.execution.model.events.ExecutionFailedEvent
import batect.execution.model.events.ImageBuildFailedEvent
import batect.execution.model.events.ImagePullFailedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.events.TaskNetworkCreationFailedEvent
import batect.execution.model.events.TaskNetworkDeletedEvent
import batect.execution.model.events.TaskNetworkDeletionFailedEvent
import batect.execution.model.events.TemporaryDirectoryDeletionFailedEvent
import batect.execution.model.events.TemporaryFileDeletionFailedEvent
import batect.execution.model.events.UserInterruptedExecutionEvent
import batect.os.SystemInfo
import batect.testutils.equivalentTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.testutils.osIndependentPath
import batect.testutils.withMessage
import batect.ui.text.Text
import batect.ui.text.TextRun
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object FailureErrorMessageFormatterSpec : Spek({
    describe("a failure error message formatter") {
        mapOf(
            "Windows" to "\r\n",
            "non-Windows platforms" to "\n"
        ).forEach { platformDescription, lineEnding ->
            describe("when running on $platformDescription") {
                val systemInfo = mock<SystemInfo> {
                    on { lineSeparator } doReturn lineEnding
                }

                val formatter = FailureErrorMessageFormatter(systemInfo)

                fun Text.withPlatformSpecificLineSeparator() = this.copy(content.replace("\n", lineEnding))
                fun TextRun.withPlatformSpecificLineSeparator() = this.map { it.withPlatformSpecificLineSeparator() }

                describe("formatting a message to display after a failure event occurs") {
                    val container = Container("the-container", imageSourceDoesNotMatter())

                    data class Scenario(val description: String, val event: TaskFailedEvent, val expectedMessage: TextRun)

                    setOf(
                        Scenario(
                            "task network creation failed",
                            TaskNetworkCreationFailedEvent("Something went wrong."),
                            Text.red(Text.bold("Error: ") + Text("Could not create network for task.\n")) + Text("Something went wrong.")
                        ),
                        Scenario(
                            "image build failed",
                            ImageBuildFailedEvent(BuildImage(osIndependentPath("/some-build-dir")), "Something went wrong."),
                            Text.red(Text.bold("Error: ") + Text("Could not build image from directory '/some-build-dir'.\n")) + Text("Something went wrong.")
                        ),
                        Scenario(
                            "image pull failed",
                            ImagePullFailedEvent(PullImage("the-image"), "Something went wrong."),
                            Text.red(Text.bold("Error: ") + Text("Could not pull image ") + Text.bold("the-image") + Text(".\n")) + Text("Something went wrong.")
                        ),
                        Scenario(
                            "container creation failed",
                            ContainerCreationFailedEvent(container, "Something went wrong."),
                            Text.red(Text.bold("Error: ") + Text("Could not create container ") + Text.bold("the-container") + Text(".\n")) + Text("Something went wrong.")
                        ),
                        Scenario(
                            "task network deletion failed",
                            TaskNetworkDeletionFailedEvent("Something went wrong."),
                            Text.red(Text.bold("Error: ") + Text("Could not delete the task network.\n")) + Text("Something went wrong.")
                        ),
                        Scenario(
                            "temporary file deletion failed",
                            TemporaryFileDeletionFailedEvent(osIndependentPath("/tmp/some-file"), "Something went wrong."),
                            Text.red(Text.bold("Error: ") + Text("Could not delete temporary file '/tmp/some-file'.\n")) + Text("Something went wrong.")
                        ),
                        Scenario(
                            "temporary directory deletion failed",
                            TemporaryDirectoryDeletionFailedEvent(osIndependentPath("/tmp/some-directory"), "Something went wrong."),
                            Text.red(Text.bold("Error: ") + Text("Could not delete temporary directory '/tmp/some-directory'.\n")) + Text("Something went wrong.")
                        ),
                        Scenario(
                            "container run failed",
                            ContainerRunFailedEvent(container, "Something went wrong."),
                            Text.red(Text.bold("Error: ") + Text("Could not run container ") + Text.bold("the-container") + Text(".\n")) + Text("Something went wrong.")
                        ),
                        Scenario(
                            "container stop failed",
                            ContainerStopFailedEvent(container, "Something went wrong."),
                            Text.red(Text.bold("Error: ") + Text("Could not stop container ") + Text.bold("the-container") + Text(".\n")) + Text("Something went wrong.")
                        ),
                        Scenario(
                            "container removal failed",
                            ContainerRemovalFailedEvent(container, "Something went wrong."),
                            Text.red(Text.bold("Error: ") + Text("Could not remove container ") + Text.bold("the-container") + Text(".\n")) + Text("Something went wrong.")
                        ),
                        Scenario(
                            "execution failed",
                            ExecutionFailedEvent("Something went wrong."),
                            Text.red(Text.bold("Error: ") + Text("An unexpected exception occurred during execution.\n")) + Text("Something went wrong.")
                        ),
                        Scenario(
                            "user interrupted execution",
                            UserInterruptedExecutionEvent,
                            Text.red(Text.bold("Error: ") + Text("Interrupt received during execution.\n")) + Text("User interrupted execution.")
                        )
                    ).forEach { (description, event, expectedMessage) ->
                        given("a '$description' event") {
                            on("getting the message for that event") {
                                val message = formatter.formatErrorMessage(event, mock())

                                it("returns an appropriate error message") {
                                    assertThat(message, equivalentTo(expectedMessage.withPlatformSpecificLineSeparator()))
                                }
                            }
                        }
                    }

                    setOf(
                        Scenario(
                            "container start failed",
                            ContainerStartFailedEvent(container, "Something went wrong."),
                            Text.red(Text.bold("Error: ") + Text("Could not start container ") + Text.bold("the-container") + Text(".\n")) + Text("Something went wrong.")
                        ),
                        Scenario(
                            "container did not become healthy",
                            ContainerDidNotBecomeHealthyEvent(container, "Something went wrong."),
                            Text.red(Text.bold("Error: ") + Text("Container ") + Text.bold("the-container") + Text(" did not become healthy.\n")) + Text("Something went wrong.")
                        )
                    ).forEach { (description, event, expectedMessage) ->
                        given("a '$description' event") {
                            given("cleanup after failure is disabled") {
                                val runOptions = mock<RunOptions>() {
                                    on { behaviourAfterFailure } doReturn BehaviourAfterFailure.DontCleanup
                                }

                                on("getting the message for that event") {
                                    val message = formatter.formatErrorMessage(event, runOptions)

                                    it("returns an appropriate error message") {
                                        assertThat(message, equivalentTo(expectedMessage.withPlatformSpecificLineSeparator()))
                                    }
                                }
                            }

                            given("cleanup after failure is enabled") {
                                val runOptions = mock<RunOptions>() {
                                    on { behaviourAfterFailure } doReturn BehaviourAfterFailure.Cleanup
                                }

                                on("getting the message for that event") {
                                    val message = formatter.formatErrorMessage(event, runOptions)
                                    val expectedMessageWithCleanupInfo = expectedMessage + Text("\n\nYou can re-run the task with ") + Text.bold("--no-cleanup-after-failure") + Text(" to leave the created containers running to diagnose the issue.")

                                    it("returns an appropriate error message with a message mentioning that the task can be re-run with cleanup disabled") {
                                        assertThat(
                                            message,
                                            equivalentTo(expectedMessageWithCleanupInfo.withPlatformSpecificLineSeparator())
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                describe("formatting a message to display after the task has failed with cleanup disabled") {
                    given("no events were posted") {
                        val events = emptySet<TaskEvent>()

                        it("throws an appropriate exception") {
                            assertThat(
                                { formatter.formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events, emptyList()) },
                                throws<IllegalArgumentException>(withMessage("No containers were created and so this method should not be called."))
                            )
                        }
                    }

                    given("no containers were created") {
                        val events = setOf(
                            TaskNetworkDeletedEvent
                        )

                        it("throws an appropriate exception") {
                            assertThat(
                                { formatter.formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events, emptyList()) },
                                throws<IllegalArgumentException>(withMessage("No containers were created and so this method should not be called."))
                            )
                        }
                    }

                    given("a container was created") {
                        val events = setOf(
                            ContainerCreatedEvent(Container("http-server", imageSourceDoesNotMatter()), DockerContainer("http-server-container-id"))
                        )

                        given("there are no cleanup commands") {
                            val cleanupCommands = emptyList<String>()

                            on("formatting the message") {
                                it("throws an appropriate exception") {
                                    assertThat(
                                        { formatter.formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events, cleanupCommands) },
                                        throws<IllegalArgumentException>(withMessage("No cleanup commands were provided."))
                                    )
                                }
                            }
                        }

                        given("there is one cleanup command") {
                            val cleanupCommands = listOf(
                                "docker network rm some-network"
                            )

                            on("formatting the message") {
                                val message = formatter.formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events, cleanupCommands)
                                val expectedMessage = Text.red(Text("As the task was run with ") + Text.bold("--no-cleanup-after-failure") + Text(", the created containers will not be cleaned up.\n")) +
                                    Text("For container ") + Text.bold("http-server") + Text(", view its output by running '") + Text.bold("docker logs http-server-container-id") +
                                    Text("', or run a command in the container with '") + Text.bold("docker exec -it http-server-container-id <command>") + Text("'.\n") +
                                    Text("\n") +
                                    Text("Once you have finished investigating the issue, clean up all temporary resources created by batect by running:\n") +
                                    Text.bold("docker network rm some-network")

                                it("returns an appropriate message") {
                                    assertThat(message, equivalentTo(expectedMessage.withPlatformSpecificLineSeparator()))
                                }
                            }
                        }

                        given("there are multiple cleanup commands") {
                            val cleanupCommands = listOf(
                                "docker rm some-container",
                                "docker network rm some-network"
                            )

                            on("formatting the message") {
                                val message = formatter.formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events, cleanupCommands)
                                val expectedMessage =
                                    Text.red(Text("As the task was run with ") + Text.bold("--no-cleanup-after-failure") + Text(", the created containers will not be cleaned up.\n")) +
                                        Text("For container ") + Text.bold("http-server") + Text(", view its output by running '") + Text.bold("docker logs http-server-container-id") +
                                        Text("', or run a command in the container with '") + Text.bold("docker exec -it http-server-container-id <command>") + Text("'.\n") +
                                        Text("\n") +
                                        Text("Once you have finished investigating the issue, clean up all temporary resources created by batect by running:\n") +
                                        Text.bold("docker rm some-container\n") +
                                        Text.bold("docker network rm some-network")

                                it("returns an appropriate message") {
                                    assertThat(message, equivalentTo(expectedMessage.withPlatformSpecificLineSeparator()))
                                }
                            }
                        }
                    }

                    given("some containers were created") {
                        val events = setOf(
                            ContainerCreatedEvent(Container("http-server", imageSourceDoesNotMatter()), DockerContainer("http-server-container-id")),
                            ContainerCreatedEvent(Container("database", imageSourceDoesNotMatter()), DockerContainer("database-container-id"))
                        )

                        given("there are no cleanup commands") {
                            val cleanupCommands = emptyList<String>()

                            on("formatting the message") {
                                it("throws an appropriate exception") {
                                    assertThat(
                                        { formatter.formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events, cleanupCommands) },
                                        throws<IllegalArgumentException>(withMessage("No cleanup commands were provided."))
                                    )
                                }
                            }
                        }

                        given("there is one cleanup command") {
                            val cleanupCommands = listOf(
                                "docker network rm some-network"
                            )

                            on("formatting the message") {
                                val message = formatter.formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events, cleanupCommands)
                                val expectedMessage =
                                    Text.red(Text("As the task was run with ") + Text.bold("--no-cleanup-after-failure") + Text(", the created containers will not be cleaned up.\n")) +
                                        Text("For container ") + Text.bold("database") + Text(", view its output by running '") + Text.bold("docker logs database-container-id") +
                                        Text("', or run a command in the container with '") + Text.bold("docker exec -it database-container-id <command>") + Text("'.\n") +
                                        Text("For container ") + Text.bold("http-server") + Text(", view its output by running '") + Text.bold("docker logs http-server-container-id") +
                                        Text("', or run a command in the container with '") + Text.bold("docker exec -it http-server-container-id <command>") + Text("'.\n") +
                                        Text("\n") +
                                        Text("Once you have finished investigating the issue, clean up all temporary resources created by batect by running:\n") +
                                        Text.bold("docker network rm some-network")

                                it("returns an appropriate message") {
                                    assertThat(message, equivalentTo(expectedMessage.withPlatformSpecificLineSeparator()))
                                }
                            }
                        }

                        given("there are multiple cleanup commands") {
                            val cleanupCommands = listOf(
                                "docker rm some-container",
                                "docker network rm some-network"
                            )

                            on("formatting the message") {
                                val message = formatter.formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events, cleanupCommands)
                                val expectedMessage =
                                    Text.red(Text("As the task was run with ") + Text.bold("--no-cleanup-after-failure") + Text(", the created containers will not be cleaned up.\n")) +
                                        Text("For container ") + Text.bold("database") + Text(", view its output by running '") + Text.bold("docker logs database-container-id") +
                                        Text("', or run a command in the container with '") + Text.bold("docker exec -it database-container-id <command>") + Text("'.\n") +
                                        Text("For container ") + Text.bold("http-server") + Text(", view its output by running '") + Text.bold("docker logs http-server-container-id") +
                                        Text("', or run a command in the container with '") + Text.bold("docker exec -it http-server-container-id <command>") + Text("'.\n") +
                                        Text("\n") +
                                        Text("Once you have finished investigating the issue, clean up all temporary resources created by batect by running:\n") +
                                        Text.bold("docker rm some-container\n") +
                                        Text.bold("docker network rm some-network")

                                it("returns an appropriate message") {
                                    assertThat(message, equivalentTo(expectedMessage.withPlatformSpecificLineSeparator()))
                                }
                            }
                        }
                    }
                }

                describe("formatting a message to display after cleanup failed") {
                    given("there are no cleanup commands") {
                        val cleanupCommands = emptyList<String>()

                        on("formatting the message") {
                            val message = formatter.formatManualCleanupMessageAfterCleanupFailure(cleanupCommands)

                            it("returns an empty set of text") {
                                assertThat(message, equivalentTo(TextRun()))
                            }
                        }
                    }

                    given("there is one cleanup command") {
                        val cleanupCommands = listOf(
                            "docker network rm some-network"
                        )

                        on("formatting the message") {
                            val message = formatter.formatManualCleanupMessageAfterCleanupFailure(cleanupCommands)
                            val expectedMessage = Text.red("Clean up has failed, and batect cannot guarantee that all temporary resources created have been completely cleaned up.\n") +
                                Text("You may need to run the following command to clean up any remaining resources:\n") +
                                Text.bold("docker network rm some-network")

                            it("returns an appropriate message") {
                                assertThat(message, equivalentTo(expectedMessage.withPlatformSpecificLineSeparator()))
                            }
                        }
                    }

                    given("there are multiple cleanup commands") {
                        val cleanupCommands = listOf(
                            "rm -rf /tmp/the-thing",
                            "docker rm some-container",
                            "docker network rm some-network"
                        )

                        on("formatting the message") {
                            val message = formatter.formatManualCleanupMessageAfterCleanupFailure(cleanupCommands)
                            val expectedMessage = Text.red("Clean up has failed, and batect cannot guarantee that all temporary resources created have been completely cleaned up.\n") +
                                Text("You may need to run some or all of the following commands to clean up any remaining resources:\n") +
                                Text.bold("rm -rf /tmp/the-thing\n") +
                                Text.bold("docker rm some-container\n") +
                                Text.bold("docker network rm some-network")

                            it("returns an appropriate message") {
                                assertThat(message, equivalentTo(expectedMessage.withPlatformSpecificLineSeparator()))
                            }
                        }
                    }
                }
            }
        }
    }
})
