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

package batect.ui.fancy

import batect.config.Container
import batect.docker.DockerContainer
import batect.docker.DockerNetwork
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerRemovedEvent
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.events.TaskNetworkDeletedEvent
import batect.execution.model.events.TemporaryDirectoryCreatedEvent
import batect.execution.model.events.TemporaryDirectoryDeletedEvent
import batect.execution.model.events.TemporaryFileCreatedEvent
import batect.execution.model.events.TemporaryFileDeletedEvent
import batect.testutils.createForEachTest
import batect.testutils.equivalentTo
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.ui.text.Text
import com.natpryce.hamkrest.assertion.assertThat
import java.nio.file.Paths
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CleanupProgressDisplayLineSpec : Spek({
    describe("a cleanup progress display line") {
        val cleanupDisplay by createForEachTest { CleanupProgressDisplayLine() }

        describe("printing cleanup progress to the console") {
            on("when there is nothing to clean up") {
                val output by runForEachTest { cleanupDisplay.print() }

                it("prints that clean up is complete") {
                    assertThat(output, equivalentTo(Text.white("Clean up: done")))
                }
            }

            describe("when there is only the network to clean up") {
                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                }

                on("and the network hasn't been removed yet") {
                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the network still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing task network...")))
                    }
                }

                on("and the network has been removed") {
                    beforeEachTest { cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent) }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that clean up is complete") {
                        assertThat(output, equivalentTo(Text.white("Clean up: done")))
                    }
                }
            }

            describe("when there is a container and the network to clean up") {
                val container = Container("some-container", imageSourceDoesNotMatter())

                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container, DockerContainer("some-container-id")))
                }

                on("and the container hasn't been removed yet") {
                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the container still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white(Text("Cleaning up: 1 container (") + Text.bold("some-container") + Text(") left to remove..."))))
                    }
                }

                on("and the container has been removed") {
                    beforeEachTest { cleanupDisplay.onEventPosted(ContainerRemovedEvent(container)) }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the network still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing task network...")))
                    }
                }

                on("and the network has been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that clean up is complete") {
                        assertThat(output, equivalentTo(Text.white("Clean up: done")))
                    }
                }
            }

            describe("when there is a container, a temporary file and the network to clean up") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val filePath = Paths.get("some-file")

                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container, DockerContainer("some-container-id")))
                    cleanupDisplay.onEventPosted(TemporaryFileCreatedEvent(container, filePath))
                }

                on("and the container hasn't been removed yet") {
                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the container still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white(Text("Cleaning up: 1 container (") + Text.bold("some-container") + Text(") left to remove..."))))
                    }
                }

                on("and the container has been removed") {
                    beforeEachTest { cleanupDisplay.onEventPosted(ContainerRemovedEvent(container)) }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the network and file still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing task network and 1 temporary file...")))
                    }
                }

                on("and the network has been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the file still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing 1 temporary file...")))
                    }
                }

                on("and the temporary file has been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TemporaryFileDeletedEvent(filePath))
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the network still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing task network...")))
                    }
                }

                on("and both the network and the temporary file has been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TemporaryFileDeletedEvent(filePath))
                        cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that clean up is complete") {
                        assertThat(output, equivalentTo(Text.white("Clean up: done")))
                    }
                }
            }

            describe("when there is a container, a temporary directory and the network to clean up") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val directoryPath = Paths.get("some-directory")

                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container, DockerContainer("some-container-id")))
                    cleanupDisplay.onEventPosted(TemporaryDirectoryCreatedEvent(container, directoryPath))
                }

                on("and the container hasn't been removed yet") {
                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the container still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white(Text("Cleaning up: 1 container (") + Text.bold("some-container") + Text(") left to remove..."))))
                    }
                }

                on("and the container has been removed") {
                    beforeEachTest { cleanupDisplay.onEventPosted(ContainerRemovedEvent(container)) }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the network and directory still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing task network and 1 temporary directory...")))
                    }
                }

                on("and the network has been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the directory still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing 1 temporary directory...")))
                    }
                }

                on("and the temporary directory has been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TemporaryDirectoryDeletedEvent(directoryPath))
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the network still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing task network...")))
                    }
                }

                on("and both the network and the temporary directory has been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TemporaryDirectoryDeletedEvent(directoryPath))
                        cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that clean up is complete") {
                        assertThat(output, equivalentTo(Text.white("Clean up: done")))
                    }
                }
            }

            describe("when there is a container, two temporary files and the network to clean up") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val file1Path = Paths.get("file-1")
                val file2Path = Paths.get("file-2")

                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container, DockerContainer("some-container-id")))
                    cleanupDisplay.onEventPosted(TemporaryFileCreatedEvent(container, file1Path))
                    cleanupDisplay.onEventPosted(TemporaryFileCreatedEvent(container, file2Path))
                }

                on("and the container hasn't been removed yet") {
                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the container still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white(Text("Cleaning up: 1 container (") + Text.bold("some-container") + Text(") left to remove..."))))
                    }
                }

                on("and the container has been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the network and both files still need to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing task network and 2 temporary files...")))
                    }
                }

                on("and the network has been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the files still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing 2 temporary files...")))
                    }
                }

                on("and the network and one of the temporary files have been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TemporaryFileDeletedEvent(file1Path))
                        cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the files still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing 1 temporary file...")))
                    }
                }

                on("and the both temporary files have been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TemporaryFileDeletedEvent(file1Path))
                        cleanupDisplay.onEventPosted(TemporaryFileDeletedEvent(file2Path))
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the network still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing task network...")))
                    }
                }

                on("and both the network and the temporary files has been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TemporaryFileDeletedEvent(file1Path))
                        cleanupDisplay.onEventPosted(TemporaryFileDeletedEvent(file2Path))
                        cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that clean up is complete") {
                        assertThat(output, equivalentTo(Text.white("Clean up: done")))
                    }
                }
            }

            describe("when there is a container, two temporary directories and the network to clean up") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val directory1Path = Paths.get("directory-1")
                val directory2Path = Paths.get("directory-2")

                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container, DockerContainer("some-container-id")))
                    cleanupDisplay.onEventPosted(TemporaryDirectoryCreatedEvent(container, directory1Path))
                    cleanupDisplay.onEventPosted(TemporaryDirectoryCreatedEvent(container, directory2Path))
                }

                on("and the container hasn't been removed yet") {
                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the container still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white(Text("Cleaning up: 1 container (") + Text.bold("some-container") + Text(") left to remove..."))))
                    }
                }

                on("and the container has been removed") {
                    beforeEachTest { cleanupDisplay.onEventPosted(ContainerRemovedEvent(container)) }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the network and both directories still need to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing task network and 2 temporary directories...")))
                    }
                }

                on("and the network has been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the directories still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing 2 temporary directories...")))
                    }
                }

                on("and the network and one of the temporary directories have been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TemporaryDirectoryDeletedEvent(directory1Path))
                        cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the directories still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing 1 temporary directory...")))
                    }
                }

                on("and both temporary directories have been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TemporaryDirectoryDeletedEvent(directory1Path))
                        cleanupDisplay.onEventPosted(TemporaryDirectoryDeletedEvent(directory2Path))
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the network still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing task network...")))
                    }
                }

                on("and both the network and the temporary directories have been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TemporaryDirectoryDeletedEvent(directory1Path))
                        cleanupDisplay.onEventPosted(TemporaryDirectoryDeletedEvent(directory2Path))
                        cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that clean up is complete") {
                        assertThat(output, equivalentTo(Text.white("Clean up: done")))
                    }
                }
            }

            describe("when there is a container, a temporary file, a temporary directory and the network to clean up") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val filePath = Paths.get("some-file")
                val directoryPath = Paths.get("some-directory")

                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container, DockerContainer("some-container-id")))
                    cleanupDisplay.onEventPosted(TemporaryFileCreatedEvent(container, filePath))
                    cleanupDisplay.onEventPosted(TemporaryDirectoryCreatedEvent(container, directoryPath))
                }

                on("and the container hasn't been removed yet") {
                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the container still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white(Text("Cleaning up: 1 container (") + Text.bold("some-container") + Text(") left to remove..."))))
                    }
                }

                on("and the container has been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the network and both directories still need to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing task network, 1 temporary file and 1 temporary directory...")))
                    }
                }

                on("and the network has been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the directories still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing 1 temporary file and 1 temporary directory...")))
                    }
                }

                on("and the network and the temporary file have been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TemporaryFileDeletedEvent(filePath))
                        cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the directory still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing 1 temporary directory...")))
                    }
                }

                on("and the network and the temporary directory have been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TemporaryDirectoryDeletedEvent(directoryPath))
                        cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the file still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing 1 temporary file...")))
                    }
                }

                on("and the temporary file and temporary directory have been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TemporaryFileDeletedEvent(filePath))
                        cleanupDisplay.onEventPosted(TemporaryDirectoryDeletedEvent(directoryPath))
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the network still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing task network...")))
                    }
                }

                on("and both the network, the temporary file and the temporary directory have been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                        cleanupDisplay.onEventPosted(TemporaryFileDeletedEvent(filePath))
                        cleanupDisplay.onEventPosted(TemporaryDirectoryDeletedEvent(directoryPath))
                        cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that clean up is complete") {
                        assertThat(output, equivalentTo(Text.white("Clean up: done")))
                    }
                }
            }

            describe("when there are two containers and the network to clean up") {
                val container1 = Container("container-1", imageSourceDoesNotMatter())
                val container2 = Container("container-2", imageSourceDoesNotMatter())

                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container1, DockerContainer("container-1-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container2, DockerContainer("container-2-id")))
                }

                on("and neither container has been removed yet") {
                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that both of the containers still need to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white(Text("Cleaning up: 2 containers (") + Text.bold("container-1") + Text(" and ") + Text.bold("container-2") + Text(") left to remove..."))))
                    }
                }

                on("and one container has been removed") {
                    beforeEachTest { cleanupDisplay.onEventPosted(ContainerRemovedEvent(container1)) }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that the other container still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white(Text("Cleaning up: 1 container (") + Text.bold("container-2") + Text(") left to remove..."))))
                    }
                }

                on("and the network has been removed") {
                    beforeEachTest {
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container1))
                        cleanupDisplay.onEventPosted(ContainerRemovedEvent(container2))
                        cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)
                    }

                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that clean up is complete") {
                        assertThat(output, equivalentTo(Text.white("Clean up: done")))
                    }
                }
            }

            describe("when there are three containers and the network to clean up") {
                val container1 = Container("container-1", imageSourceDoesNotMatter())
                val container2 = Container("container-2", imageSourceDoesNotMatter())
                val container3 = Container("container-3", imageSourceDoesNotMatter())

                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container1, DockerContainer("container-1-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container2, DockerContainer("container-2-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container3, DockerContainer("container-3-id")))
                }

                on("and none of the containers have been removed yet") {
                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that all of the containers still need to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white(Text("Cleaning up: 3 containers (") + Text.bold("container-1") + Text(", ") + Text.bold("container-2") + Text(" and ") + Text.bold("container-3") + Text(") left to remove..."))))
                    }
                }
            }

            describe("when there are four containers and the network to clean up") {
                val container1 = Container("container-1", imageSourceDoesNotMatter())
                val container2 = Container("container-2", imageSourceDoesNotMatter())
                val container3 = Container("container-3", imageSourceDoesNotMatter())
                val container4 = Container("container-4", imageSourceDoesNotMatter())

                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container1, DockerContainer("container-1-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container2, DockerContainer("container-2-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container3, DockerContainer("container-3-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container4, DockerContainer("container-4-id")))
                }

                on("and none of the containers have been removed yet") {
                    val output by runForEachTest { cleanupDisplay.print() }

                    it("prints that all of the containers still need to be cleaned up") {
                        assertThat(
                            output,
                            equivalentTo(Text.white(Text("Cleaning up: 4 containers (") + Text.bold("container-1") + Text(", ") + Text.bold("container-2") + Text(", ") + Text.bold("container-3") + Text(" and ") + Text.bold("container-4") + Text(") left to remove...")))
                        )
                    }
                }
            }
        }
    }
})
