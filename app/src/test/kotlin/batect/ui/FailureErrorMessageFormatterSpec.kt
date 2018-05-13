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

import batect.config.Container
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
import batect.execution.model.events.ImageBuildFailedEvent
import batect.execution.model.events.ImagePullFailedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.events.TaskNetworkCreationFailedEvent
import batect.execution.model.events.TaskNetworkDeletedEvent
import batect.execution.model.events.TaskNetworkDeletionFailedEvent
import batect.execution.model.events.TemporaryDirectoryDeletionFailedEvent
import batect.execution.model.events.TemporaryFileDeletionFailedEvent
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.nio.file.Paths

object FailureErrorMessageFormatterSpec : Spek({
    describe("a failure error message formatter") {
        val formatter = FailureErrorMessageFormatter()

        describe("formatting a message to display after a failure event occurs") {
            val container = Container("the-container", imageSourceDoesNotMatter())

            data class Scenario(val description: String, val event: TaskFailedEvent, val expectedMessage: String)

            setOf(
                Scenario("task network creation failed", TaskNetworkCreationFailedEvent("Something went wrong."), "Could not create network for task: Something went wrong."),
                Scenario("image build failed", ImageBuildFailedEvent(container, "Something went wrong."), "Could not build image for container 'the-container': Something went wrong."),
                Scenario("image pull failed", ImagePullFailedEvent("the-image", "Something went wrong."), "Could not pull image 'the-image': Something went wrong."),
                Scenario("container creation failed", ContainerCreationFailedEvent(container, "Something went wrong."), "Could not create container 'the-container': Something went wrong."),
                Scenario("task network deletion failed", TaskNetworkDeletionFailedEvent("Something went wrong."), "Could not delete the task network: Something went wrong."),
                Scenario("temporary file deletion failed", TemporaryFileDeletionFailedEvent(Paths.get("/tmp/some-file"), "Something went wrong."), "Could not delete temporary file '/tmp/some-file': Something went wrong."),
                Scenario("temporary directory deletion failed", TemporaryDirectoryDeletionFailedEvent(Paths.get("/tmp/some-directory"), "Something went wrong."), "Could not delete temporary directory '/tmp/some-directory': Something went wrong."),
                Scenario("container stop failed", ContainerStopFailedEvent(container, "Something went wrong."), "Could not stop container 'the-container': Something went wrong."),
                Scenario("container removal failed", ContainerRemovalFailedEvent(container, "Something went wrong."), "Could not remove container 'the-container': Something went wrong.")
            ).forEach { (description, event, expectedMessage) ->
                given("a '$description' event") {
                    on("getting the message for that event") {
                        val message = formatter.formatErrorMessage(event, mock())

                        it("returns an appropriate error message") {
                            assertThat(message, equalTo(expectedMessage))
                        }
                    }
                }
            }

            setOf(
                Scenario("container start failed", ContainerStartFailedEvent(container, "Something went wrong."), "Could not start container 'the-container': Something went wrong."),
                Scenario("container did not become healthy", ContainerDidNotBecomeHealthyEvent(container, "Something went wrong."), "Container 'the-container' did not become healthy: Something went wrong."),
                Scenario("container run failed", ContainerRunFailedEvent(container, "Something went wrong."), "Could not run container 'the-container': Something went wrong.")
            ).forEach { (description, event, expectedMessage) ->
                given("a '$description' event") {
                    given("cleanup after failure is disabled") {
                        val runOptions = mock<RunOptions>() {
                            on { behaviourAfterFailure } doReturn BehaviourAfterFailure.DontCleanup
                        }

                        on("getting the message for that event") {
                            val message = formatter.formatErrorMessage(event, runOptions)

                            it("returns an appropriate error message") {
                                assertThat(message, equalTo(expectedMessage))
                            }
                        }
                    }

                    given("cleanup after failure is enabled") {
                        val runOptions = mock<RunOptions>() {
                            on { behaviourAfterFailure } doReturn BehaviourAfterFailure.Cleanup
                        }

                        on("getting the message for that event") {
                            val message = formatter.formatErrorMessage(event, runOptions)

                            it("returns an appropriate error message with a message mentioning that the task can be re-run with cleanup disabled") {
                                assertThat(message, equalTo(expectedMessage + "\n\nYou can re-run the task with --no-cleanup-after-failure to leave the created containers running to diagnose the issue."))
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
                    assertThat({ formatter.formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events, emptyList()) },
                        throws<IllegalArgumentException>(withMessage("No containers were created and so this method should not be called.")))
                }
            }

            given("no containers were created") {
                val events = setOf(
                    TaskNetworkDeletedEvent
                )

                it("throws an appropriate exception") {
                    assertThat({ formatter.formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events, emptyList()) },
                        throws<IllegalArgumentException>(withMessage("No containers were created and so this method should not be called.")))
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
                            assertThat({ formatter.formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events, cleanupCommands) },
                                throws<IllegalArgumentException>(withMessage("No cleanup commands were provided.")))
                        }
                    }
                }

                given("there is one cleanup command") {
                    val cleanupCommands = listOf(
                        "docker network rm some-network"
                    )

                    on("formatting the message") {
                        val message = formatter.formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events, cleanupCommands)

                        it("returns an appropriate message") {
                            assertThat(message, equalTo("""
                                |As the task was run with --no-cleanup-after-failure, the created containers will not be cleaned up.
                                |For container 'http-server': view its output by running 'docker logs http-server-container-id', or run a command in the container with 'docker exec -it http-server-container-id <command>'.
                                |
                                |Once you have finished investigating the issue, you can clean up all temporary resources created by batect by running:
                                |docker network rm some-network
                            """.trimMargin()))
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

                        it("returns an appropriate message") {
                            assertThat(message, equalTo("""
                                |As the task was run with --no-cleanup-after-failure, the created containers will not be cleaned up.
                                |For container 'http-server': view its output by running 'docker logs http-server-container-id', or run a command in the container with 'docker exec -it http-server-container-id <command>'.
                                |
                                |Once you have finished investigating the issue, you can clean up all temporary resources created by batect by running:
                                |docker rm some-container
                                |docker network rm some-network
                            """.trimMargin()))
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
                            assertThat({ formatter.formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events, cleanupCommands) },
                                throws<IllegalArgumentException>(withMessage("No cleanup commands were provided.")))
                        }
                    }
                }

                given("there is one cleanup command") {
                    val cleanupCommands = listOf(
                        "docker network rm some-network"
                    )

                    on("formatting the message") {
                        val message = formatter.formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events, cleanupCommands)

                        it("returns an appropriate message") {
                            assertThat(message, equalTo("""
                                |As the task was run with --no-cleanup-after-failure, the created containers will not be cleaned up.
                                |For container 'database': view its output by running 'docker logs database-container-id', or run a command in the container with 'docker exec -it database-container-id <command>'.
                                |For container 'http-server': view its output by running 'docker logs http-server-container-id', or run a command in the container with 'docker exec -it http-server-container-id <command>'.
                                |
                                |Once you have finished investigating the issue, you can clean up all temporary resources created by batect by running:
                                |docker network rm some-network
                            """.trimMargin()))
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

                        it("returns an appropriate message") {
                            assertThat(message, equalTo("""
                                |As the task was run with --no-cleanup-after-failure, the created containers will not be cleaned up.
                                |For container 'database': view its output by running 'docker logs database-container-id', or run a command in the container with 'docker exec -it database-container-id <command>'.
                                |For container 'http-server': view its output by running 'docker logs http-server-container-id', or run a command in the container with 'docker exec -it http-server-container-id <command>'.
                                |
                                |Once you have finished investigating the issue, you can clean up all temporary resources created by batect by running:
                                |docker rm some-container
                                |docker network rm some-network
                            """.trimMargin()))
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

                    it("returns an empty string") {
                        assertThat(message, equalTo(""))
                    }
                }
            }

            given("there is one cleanup command") {
                val cleanupCommands = listOf(
                    "docker network rm some-network"
                )

                on("formatting the message") {
                    val message = formatter.formatManualCleanupMessageAfterCleanupFailure(cleanupCommands)

                    it("returns an appropriate message") {
                        assertThat(message, equalTo("""
                            |Clean up has failed, and batect cannot guarantee that all temporary resources created have been completely cleaned up.
                            |You may need to run the following command to clean up any remaining resources:
                            |
                            |docker network rm some-network
                        """.trimMargin()))
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

                    it("returns an appropriate message") {
                        assertThat(message, equalTo("""
                            |Clean up has failed, and batect cannot guarantee that all temporary resources created have been completely cleaned up.
                            |You may need to run some or all of the following commands to clean up any remaining resources:
                            |
                            |rm -rf /tmp/the-thing
                            |docker rm some-container
                            |docker network rm some-network
                        """.trimMargin()))
                    }
                }
            }
        }
    }
})
