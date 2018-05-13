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

package batect.execution

import batect.config.Container
import batect.config.RunAsCurrentUserConfig
import batect.config.VolumeMount
import batect.docker.UserAndGroup
import batect.execution.model.events.TaskEventSink
import batect.execution.model.events.TemporaryDirectoryCreatedEvent
import batect.execution.model.events.TemporaryFileCreatedEvent
import batect.os.SystemInfo
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.isEmptyMap
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributes

object RunAsCurrentUserConfigurationProviderSpec : Spek({
    describe("a 'run as current user' configuration provider") {
        val eventSink by createForEachTest { mock<TaskEventSink>() }
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix().toBuilder().setAttributeViews("posix").build()) }

        val directoryThatExists = "/existing/directory"
        val fileThatExists = "/existing/file"
        val directoryThatDoesNotExist = "/new/directory"
        val containerMounts = setOf(
            VolumeMount(directoryThatExists, "/container/existing-directory", null),
            VolumeMount(fileThatExists, "/container/existing-file", null),
            VolumeMount(directoryThatDoesNotExist, "/container/new-directory", null)
        )

        beforeEachTest {
            Files.createDirectories(fileSystem.getPath("/tmp"))
            Files.createDirectories(fileSystem.getPath(directoryThatExists))
            Files.createFile(fileSystem.getPath(fileThatExists))
        }

        given("the container has 'run as current user' disabled") {
            val container = Container(
                "some-container",
                imageSourceDoesNotMatter(),
                volumeMounts = containerMounts,
                runAsCurrentUserConfig = RunAsCurrentUserConfig(enabled = false)
            )

            val systemInfo = mock<SystemInfo>()
            val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, fileSystem) }

            on("generating the configuration") {
                val configuration = provider.generateConfiguration(container, eventSink)

                it("does not emit any events") {
                    verify(eventSink, never()).postEvent(any())
                }

                it("returns an empty user and group configuration") {
                    assertThat(configuration.userAndGroup, absent())
                }

                it("returns an empty set of paths to copy to the container") {
                    assertThat(configuration.pathsToCopyToContainer, isEmptyMap())
                }

                it("does not create any new directories") {
                    assertThat(Files.exists(fileSystem.getPath(directoryThatDoesNotExist)), equalTo(false))
                }
            }
        }

        given("the container has 'run as current user' enabled") {
            val container = Container(
                "some-container",
                imageSourceDoesNotMatter(),
                volumeMounts = containerMounts,
                runAsCurrentUserConfig = RunAsCurrentUserConfig(enabled = true, homeDirectory = "/home/some-user")
            )

            given("the current user is not root") {
                val systemInfo = mock<SystemInfo> {
                    on { userId } doReturn 123
                    on { userName } doReturn "the-user"
                    on { groupId } doReturn 456
                    on { groupName } doReturn "the-user's-group"
                }

                val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, fileSystem) }

                on("generating the configuration") {
                    val configuration = provider.generateConfiguration(container, eventSink)

                    it("returns a set of paths to copy for just the passwd and group files and home directory") {
                        assertThat(configuration.pathsToCopyToContainer.values.toSet(), equalTo(setOf(
                            "/etc/passwd",
                            "/etc/group",
                            container.runAsCurrentUserConfig.homeDirectory!!
                        )))
                    }

                    it("creates a passwd file with both root and the current user") {
                        val passwdFilePath = localPathToPasswdFile(configuration.pathsToCopyToContainer)
                        val content = Files.readAllLines(passwdFilePath).joinToString("\n")
                        assertThat(content, equalTo("""
                            |root:x:0:0:root:/root:/bin/sh
                            |the-user:x:123:456:the-user:/home/some-user:/bin/sh
                        """.trimMargin()))
                    }

                    it("creates a group file with group for both root and the current user") {
                        val groupFilePath = localPathToGroupFile(configuration.pathsToCopyToContainer)
                        val content = Files.readAllLines(groupFilePath).joinToString("\n")
                        assertThat(content, equalTo("""
                            |root:x:0:root
                            |the-user's-group:x:456:the-user
                        """.trimMargin()))
                    }

                    it("emits a 'temporary file created' event for the passwd file") {
                        val passwdFilePath = localPathToPasswdFile(configuration.pathsToCopyToContainer)
                        verify(eventSink).postEvent(TemporaryFileCreatedEvent(container, passwdFilePath))
                    }

                    it("emits a 'temporary file created' event for the group file") {
                        val groupFilePath = localPathToGroupFile(configuration.pathsToCopyToContainer)
                        verify(eventSink).postEvent(TemporaryFileCreatedEvent(container, groupFilePath))
                    }

                    it("emits a 'temporary directory created' event for the home directory") {
                        val homeDirectoryPath = localPathToHomeDirectory(configuration.pathsToCopyToContainer, container.runAsCurrentUserConfig.homeDirectory!!)
                        verify(eventSink).postEvent(TemporaryDirectoryCreatedEvent(container, homeDirectoryPath))
                    }

                    it("returns a user and group configuration with the current user's user and group IDs") {
                        assertThat(configuration.userAndGroup, equalTo(UserAndGroup(123, 456)))
                    }

                    it("creates a directory for the volume mount path that does not exist") {
                        assertThat(Files.exists(fileSystem.getPath(directoryThatDoesNotExist)), equalTo(true))
                    }

                    it("creates a directory for the home directory") {
                        val homeDirectoryPath = localPathToHomeDirectory(configuration.pathsToCopyToContainer, container.runAsCurrentUserConfig.homeDirectory!!)
                        assertThat(Files.exists(homeDirectoryPath), equalTo(true))
                    }

                    it("sets the user for the created home directory to the current user") {
                        val homeDirectoryPath = localPathToHomeDirectory(configuration.pathsToCopyToContainer, container.runAsCurrentUserConfig.homeDirectory!!)
                        val owner = Files.readAttributes(homeDirectoryPath, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).owner()
                        assertThat(owner.name, equalTo("the-user"))
                    }

                    it("sets the group for the created home directory to the current user's primary group") {
                        val homeDirectoryPath = localPathToHomeDirectory(configuration.pathsToCopyToContainer, container.runAsCurrentUserConfig.homeDirectory!!)
                        val group = Files.readAttributes(homeDirectoryPath, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).group()
                        assertThat(group.name, equalTo("the-user's-group"))
                    }
                }
            }

            given("the current user is root") {
                val systemInfo = mock<SystemInfo> {
                    on { userId } doReturn 0
                    on { userName } doReturn "root"
                    on { groupId } doReturn 0
                    on { groupName } doReturn "root"
                }

                val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, fileSystem) }

                on("generating the configuration") {
                    val configuration = provider.generateConfiguration(container, eventSink)

                    it("returns a set of paths to copy for just the passwd and group files and home directory") {
                        assertThat(configuration.pathsToCopyToContainer.values.toSet(), equalTo(setOf(
                            "/etc/passwd",
                            "/etc/group",
                            container.runAsCurrentUserConfig.homeDirectory!!
                        )))
                    }

                    it("creates a passwd file with just root listed") {
                        val passwdFilePath = localPathToPasswdFile(configuration.pathsToCopyToContainer)
                        val content = Files.readAllLines(passwdFilePath).joinToString("\n")
                        assertThat(content, equalTo("""
                            |root:x:0:0:root:/home/some-user:/bin/sh
                        """.trimMargin()))
                    }

                    it("creates a group file with a group for root") {
                        val groupFilePath = localPathToGroupFile(configuration.pathsToCopyToContainer)
                        val content = Files.readAllLines(groupFilePath).joinToString("\n")
                        assertThat(content, equalTo("""
                            |root:x:0:root
                        """.trimMargin()))
                    }

                    it("emits a 'temporary file created' event for the passwd file") {
                        val passwdFilePath = localPathToPasswdFile(configuration.pathsToCopyToContainer)
                        verify(eventSink).postEvent(TemporaryFileCreatedEvent(container, passwdFilePath))
                    }

                    it("emits a 'temporary file created' event for the group file") {
                        val groupFilePath = localPathToGroupFile(configuration.pathsToCopyToContainer)
                        verify(eventSink).postEvent(TemporaryFileCreatedEvent(container, groupFilePath))
                    }

                    it("emits a 'temporary directory created' event for the home directory") {
                        val homeDirectoryPath = localPathToHomeDirectory(configuration.pathsToCopyToContainer, container.runAsCurrentUserConfig.homeDirectory!!)
                        verify(eventSink).postEvent(TemporaryDirectoryCreatedEvent(container, homeDirectoryPath))
                    }

                    it("returns a user and group configuration with the current user's user and group IDs") {
                        assertThat(configuration.userAndGroup, equalTo(UserAndGroup(0, 0)))
                    }

                    it("creates a directory for the volume mount path that does not exist") {
                        assertThat(Files.exists(fileSystem.getPath(directoryThatDoesNotExist)), equalTo(true))
                    }

                    it("creates a directory for the home directory") {
                        val homeDirectoryPath = localPathToHomeDirectory(configuration.pathsToCopyToContainer, container.runAsCurrentUserConfig.homeDirectory!!)
                        assertThat(Files.exists(homeDirectoryPath), equalTo(true))
                    }

                    it("sets the user for the created home directory to the current user") {
                        val homeDirectoryPath = localPathToHomeDirectory(configuration.pathsToCopyToContainer, container.runAsCurrentUserConfig.homeDirectory!!)
                        val owner = Files.readAttributes(homeDirectoryPath, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).owner()
                        assertThat(owner.name, equalTo("root"))
                    }

                    it("sets the group for the created home directory to the current user's primary group") {
                        val homeDirectoryPath = localPathToHomeDirectory(configuration.pathsToCopyToContainer, container.runAsCurrentUserConfig.homeDirectory!!)
                        val group = Files.readAttributes(homeDirectoryPath, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).group()
                        assertThat(group.name, equalTo("root"))
                    }
                }
            }
        }
    }
})

private fun localPathToPasswdFile(pathsToCopy: Map<Path, String>): Path = pathsToCopy.asIterable().single { it.value == "/etc/passwd" }.key
private fun localPathToGroupFile(pathsToCopy: Map<Path, String>): Path = pathsToCopy.asIterable().single { it.value == "/etc/group" }.key
private fun localPathToHomeDirectory(pathsToCopy: Map<Path, String>, homeDirectory: String): Path = pathsToCopy.asIterable().single { it.value == homeDirectory }.key
