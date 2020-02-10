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

package batect.execution

import batect.config.Container
import batect.config.RunAsCurrentUserConfig
import batect.config.VolumeMount
import batect.docker.UserAndGroup
import batect.execution.model.events.TaskEventSink
import batect.execution.model.events.TemporaryDirectoryCreatedEvent
import batect.execution.model.events.TemporaryFileCreatedEvent
import batect.os.NativeMethods
import batect.os.OperatingSystem
import batect.os.SystemInfo
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.runNullableForEachTest
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.anyElement
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.isEmpty
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributes

object RunAsCurrentUserConfigurationProviderSpec : Spek({
    describe("a 'run as current user' configuration provider") {
        val eventSink by createForEachTest { mock<TaskEventSink>() }

        given("the container has 'run as current user' disabled") {
            val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }

            val directoryThatExists = "/existing/directory"
            val fileThatExists = "/existing/file"
            val directoryThatDoesNotExist = "/new/directory"
            val containerMounts = setOf(
                VolumeMount(directoryThatExists, "/container/existing-directory", null),
                VolumeMount(fileThatExists, "/container/existing-file", null),
                VolumeMount(directoryThatDoesNotExist, "/container/new-directory", null)
            )

            val container = Container(
                "some-container",
                imageSourceDoesNotMatter(),
                volumeMounts = containerMounts,
                runAsCurrentUserConfig = RunAsCurrentUserConfig.RunAsDefaultContainerUser
            )

            val systemInfo by createForEachTest {
                mock<SystemInfo> {
                    on { tempDirectory } doReturn fileSystem.getPath("/tmp")
                }
            }

            val nativeMethods = mock<NativeMethods>()
            val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem) }

            beforeEachTest {
                Files.createDirectories(systemInfo.tempDirectory)
                Files.createDirectories(fileSystem.getPath(directoryThatExists))
                Files.createFile(fileSystem.getPath(fileThatExists))
            }

            on("generating the configuration") {
                val configuration by runForEachTest { provider.generateConfiguration(container, eventSink) }

                it("does not emit any events") {
                    verify(eventSink, never()).postEvent(any())
                }

                it("returns an empty set of volume mounts") {
                    assertThat(configuration.volumeMounts, isEmpty)
                }

                it("returns an empty user and group configuration") {
                    assertThat(configuration.userAndGroup, absent())
                }

                it("does not create any new directories") {
                    assertThat(Files.exists(fileSystem.getPath(directoryThatDoesNotExist)), equalTo(false))
                }
            }

            on("determining the user and group to use") {
                val userAndGroup by runNullableForEachTest { provider.determineUserAndGroup(container) }

                it("returns an empty user and group configuration") {
                    assertThat(userAndGroup, absent())
                }
            }
        }

        given("the container has 'run as current user' enabled") {
            val homeDirectory = "/home/some-user"
            val runAsCurrentUserConfig = RunAsCurrentUserConfig.RunAsCurrentUser(homeDirectory)

            given("the application is running on Windows") {
                val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.windows()) }

                val systemInfo by createForEachTest {
                    mock<SystemInfo> {
                        on { operatingSystem } doReturn OperatingSystem.Windows
                        on { tempDirectory } doReturn fileSystem.getPath("C:\\temp")
                    }
                }

                val directoryThatExists = "C:\\existing\\directory"
                val fileThatExists = "C:\\existing\\file"
                val directoryThatDoesNotExist = "C:\\new\\directory"
                val containerMounts = setOf(
                    VolumeMount(directoryThatExists, "/container/existing-directory", null),
                    VolumeMount(fileThatExists, "/container/existing-file", null),
                    VolumeMount(directoryThatDoesNotExist, "/container/new-directory", null)
                )

                beforeEachTest {
                    Files.createDirectories(systemInfo.tempDirectory)
                    Files.createDirectories(fileSystem.getPath(directoryThatExists))
                    Files.createFile(fileSystem.getPath(fileThatExists))
                }

                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    volumeMounts = containerMounts,
                    runAsCurrentUserConfig = runAsCurrentUserConfig
                )

                val nativeMethods = mock<NativeMethods> {
                    on { getUserName() } doThrow UnsupportedOperationException("This shouldn't be called on Windows")
                    on { getUserId() } doThrow UnsupportedOperationException("This shouldn't be called on Windows")
                    on { getGroupId() } doThrow UnsupportedOperationException("This shouldn't be called on Windows")
                    on { getGroupName() } doThrow UnsupportedOperationException("This shouldn't be called on Windows")
                }

                val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem) }

                on("generating the configuration") {
                    val configuration by runForEachTest { provider.generateConfiguration(container, eventSink) }

                    it("returns a set of volume mounts for the passwd and group file and home directory") {
                        assertThat(
                            configuration.volumeMounts.mapToSet { it.containerPath }, equalTo(
                                setOf(
                                    "/etc/passwd",
                                    "/etc/group",
                                    homeDirectory
                                )
                            )
                        )
                    }

                    it("returns a set of volume mounts with the passwd file mounted read-only") {
                        assertThat(configuration.volumeMounts, hasReadOnlyVolumeMount("/etc/passwd"))
                    }

                    it("returns a set of volume mounts with the group file mounted read-only") {
                        assertThat(configuration.volumeMounts, hasReadOnlyVolumeMount("/etc/group"))
                    }

                    it("returns a set of volume mounts with the home directory mounted in delegated mode") {
                        assertThat(configuration.volumeMounts, hasDelegatedVolumeMount(homeDirectory))
                    }

                    it("creates a passwd file with root's home directory set to the configured directory") {
                        val passwdFilePath = localPathToPasswdFile(configuration.volumeMounts, fileSystem)
                        val content = Files.readAllLines(passwdFilePath).joinToString("\n")
                        assertThat(
                            content, equalTo(
                                """
                                    |root:x:0:0:root:/home/some-user:/bin/sh
                                """.trimMargin()
                            )
                        )
                    }

                    it("creates a group file with group for root") {
                        val groupFilePath = localPathToGroupFile(configuration.volumeMounts, fileSystem)
                        val content = Files.readAllLines(groupFilePath).joinToString("\n")
                        assertThat(
                            content, equalTo(
                                """
                                    |root:x:0:root
                                """.trimMargin()
                            )
                        )
                    }

                    it("emits a 'temporary file created' event for the passwd file") {
                        val passwdFilePath = localPathToPasswdFile(configuration.volumeMounts, fileSystem)
                        verify(eventSink).postEvent(TemporaryFileCreatedEvent(container, passwdFilePath))
                    }

                    it("emits a 'temporary file created' event for the group file") {
                        val groupFilePath = localPathToGroupFile(configuration.volumeMounts, fileSystem)
                        verify(eventSink).postEvent(TemporaryFileCreatedEvent(container, groupFilePath))
                    }

                    it("emits a 'temporary directory created' event for the home directory") {
                        val homeDirectoryPath = localPathToHomeDirectory(configuration.volumeMounts, homeDirectory, fileSystem)
                        verify(eventSink).postEvent(TemporaryDirectoryCreatedEvent(container, homeDirectoryPath))
                    }

                    it("returns a user and group configuration with root's UID and GID") {
                        assertThat(configuration.userAndGroup, equalTo(UserAndGroup(0, 0)))
                    }

                    it("creates a directory for the volume mount path that does not exist") {
                        assertThat(Files.exists(fileSystem.getPath(directoryThatDoesNotExist)), equalTo(true))
                    }

                    it("creates a directory for the home directory") {
                        val homeDirectoryPath = localPathToHomeDirectory(configuration.volumeMounts, homeDirectory, fileSystem)
                        assertThat(Files.exists(homeDirectoryPath), equalTo(true))
                    }
                }

                on("determining the user and group to use") {
                    val userAndGroup by runNullableForEachTest { provider.determineUserAndGroup(container) }

                    it("returns a user and group configuration with root's UID and GID") {
                        assertThat(userAndGroup, equalTo(UserAndGroup(0, 0)))
                    }
                }
            }

            given("the application is not running on Windows") {
                val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix().toBuilder().setAttributeViews("posix").build()) }

                val systemInfo by createForEachTest {
                    mock<SystemInfo> {
                        on { operatingSystem } doReturn OperatingSystem.Other
                        on { tempDirectory } doReturn fileSystem.getPath("/tmp")
                    }
                }

                val directoryThatExists = "/existing/directory"
                val fileThatExists = "/existing/file"
                val directoryThatDoesNotExist = "/new/directory"
                val containerMounts = setOf(
                    VolumeMount(directoryThatExists, "/container/existing-directory", null),
                    VolumeMount(fileThatExists, "/container/existing-file", null),
                    VolumeMount(directoryThatDoesNotExist, "/container/new-directory", null)
                )

                beforeEachTest {
                    Files.createDirectories(systemInfo.tempDirectory)
                    Files.createDirectories(fileSystem.getPath(directoryThatExists))
                    Files.createFile(fileSystem.getPath(fileThatExists))
                }

                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    volumeMounts = containerMounts,
                    runAsCurrentUserConfig = runAsCurrentUserConfig
                )

                given("the current user is not root") {
                    val nativeMethods = mock<NativeMethods> {
                        on { getUserId() } doReturn 123
                        on { getUserName() } doReturn "the-user"
                        on { getGroupId() } doReturn 456
                        on { getGroupName() } doReturn "the-user's-group"
                    }

                    val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem) }

                    on("generating the configuration") {
                        val configuration by runForEachTest { provider.generateConfiguration(container, eventSink) }

                        it("returns a set of volume mounts for the passwd and group file and home directory") {
                            assertThat(
                                configuration.volumeMounts.mapToSet { it.containerPath }, equalTo(
                                    setOf(
                                        "/etc/passwd",
                                        "/etc/group",
                                        homeDirectory
                                    )
                                )
                            )
                        }

                        it("returns a set of volume mounts with the passwd file mounted read-only") {
                            assertThat(configuration.volumeMounts, hasReadOnlyVolumeMount("/etc/passwd"))
                        }

                        it("returns a set of volume mounts with the group file mounted read-only") {
                            assertThat(configuration.volumeMounts, hasReadOnlyVolumeMount("/etc/group"))
                        }

                        it("returns a set of volume mounts with the home directory mounted in delegated mode") {
                            assertThat(configuration.volumeMounts, hasDelegatedVolumeMount(homeDirectory))
                        }

                        it("creates a passwd file with both root and the current user") {
                            val passwdFilePath = localPathToPasswdFile(configuration.volumeMounts, fileSystem)
                            val content = Files.readAllLines(passwdFilePath).joinToString("\n")
                            assertThat(
                                content, equalTo(
                                    """
                                        |root:x:0:0:root:/root:/bin/sh
                                        |the-user:x:123:456:the-user:/home/some-user:/bin/sh
                                    """.trimMargin()
                                )
                            )
                        }

                        it("creates a group file with group for both root and the current user") {
                            val groupFilePath = localPathToGroupFile(configuration.volumeMounts, fileSystem)
                            val content = Files.readAllLines(groupFilePath).joinToString("\n")
                            assertThat(
                                content, equalTo(
                                    """
                                        |root:x:0:root
                                        |the-user's-group:x:456:the-user
                                    """.trimMargin()
                                )
                            )
                        }

                        it("emits a 'temporary file created' event for the passwd file") {
                            val passwdFilePath = localPathToPasswdFile(configuration.volumeMounts, fileSystem)
                            verify(eventSink).postEvent(TemporaryFileCreatedEvent(container, passwdFilePath))
                        }

                        it("emits a 'temporary file created' event for the group file") {
                            val groupFilePath = localPathToGroupFile(configuration.volumeMounts, fileSystem)
                            verify(eventSink).postEvent(TemporaryFileCreatedEvent(container, groupFilePath))
                        }

                        it("emits a 'temporary directory created' event for the home directory") {
                            val homeDirectoryPath = localPathToHomeDirectory(configuration.volumeMounts, homeDirectory, fileSystem)
                            verify(eventSink).postEvent(TemporaryDirectoryCreatedEvent(container, homeDirectoryPath))
                        }

                        it("returns a user and group configuration with the current user's user and group IDs") {
                            assertThat(configuration.userAndGroup, equalTo(UserAndGroup(123, 456)))
                        }

                        it("creates a directory for the volume mount path that does not exist") {
                            assertThat(Files.exists(fileSystem.getPath(directoryThatDoesNotExist)), equalTo(true))
                        }

                        it("creates a directory for the home directory") {
                            val homeDirectoryPath = localPathToHomeDirectory(configuration.volumeMounts, homeDirectory, fileSystem)
                            assertThat(Files.exists(homeDirectoryPath), equalTo(true))
                        }

                        it("sets the user for the created home directory to the current user") {
                            val homeDirectoryPath = localPathToHomeDirectory(configuration.volumeMounts, homeDirectory, fileSystem)
                            val owner = Files.readAttributes(homeDirectoryPath, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).owner()
                            assertThat(owner.name, equalTo("the-user"))
                        }

                        it("sets the group for the created home directory to the current user's primary group") {
                            val homeDirectoryPath = localPathToHomeDirectory(configuration.volumeMounts, homeDirectory, fileSystem)
                            val group = Files.readAttributes(homeDirectoryPath, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).group()
                            assertThat(group.name, equalTo("the-user's-group"))
                        }
                    }

                    on("determining the user and group to use") {
                        val userAndGroup by runNullableForEachTest { provider.determineUserAndGroup(container) }

                        it("returns a user and group configuration with the current user's user and group IDs") {
                            assertThat(userAndGroup, equalTo(UserAndGroup(123, 456)))
                        }
                    }
                }

                given("the current user is root") {
                    val nativeMethods = mock<NativeMethods> {
                        on { getUserId() } doReturn 0
                        on { getUserName() } doReturn "root"
                        on { getGroupId() } doReturn 0
                        on { getGroupName() } doReturn "root"
                    }

                    val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem) }

                    on("generating the configuration") {
                        val configuration by runForEachTest { provider.generateConfiguration(container, eventSink) }

                        it("returns a set of volume mounts for the passwd and group file") {
                            assertThat(
                                configuration.volumeMounts.mapToSet { it.containerPath }, equalTo(
                                    setOf(
                                        "/etc/passwd",
                                        "/etc/group",
                                        homeDirectory
                                    )
                                )
                            )
                        }

                        it("returns a set of volume mounts with the passwd file mounted read-only") {
                            assertThat(configuration.volumeMounts, hasReadOnlyVolumeMount("/etc/passwd"))
                        }

                        it("returns a set of volume mounts with the group file mounted read-only") {
                            assertThat(configuration.volumeMounts, hasReadOnlyVolumeMount("/etc/group"))
                        }

                        it("returns a set of volume mounts with the home directory mounted in delegated mode") {
                            assertThat(configuration.volumeMounts, hasDelegatedVolumeMount(homeDirectory))
                        }

                        it("creates a passwd file with just root listed") {
                            val passwdFilePath = localPathToPasswdFile(configuration.volumeMounts, fileSystem)
                            val content = Files.readAllLines(passwdFilePath).joinToString("\n")
                            assertThat(
                                content, equalTo(
                                    """
                                        |root:x:0:0:root:/home/some-user:/bin/sh
                                    """.trimMargin()
                                )
                            )
                        }

                        it("creates a group file with a group for root") {
                            val groupFilePath = localPathToGroupFile(configuration.volumeMounts, fileSystem)
                            val content = Files.readAllLines(groupFilePath).joinToString("\n")
                            assertThat(
                                content, equalTo(
                                    """
                                        |root:x:0:root
                                    """.trimMargin()
                                )
                            )
                        }

                        it("emits a 'temporary file created' event for the passwd file") {
                            val passwdFilePath = localPathToPasswdFile(configuration.volumeMounts, fileSystem)
                            verify(eventSink).postEvent(TemporaryFileCreatedEvent(container, passwdFilePath))
                        }

                        it("emits a 'temporary file created' event for the group file") {
                            val groupFilePath = localPathToGroupFile(configuration.volumeMounts, fileSystem)
                            verify(eventSink).postEvent(TemporaryFileCreatedEvent(container, groupFilePath))
                        }

                        it("emits a 'temporary directory created' event for the home directory") {
                            val homeDirectoryPath = localPathToHomeDirectory(configuration.volumeMounts, homeDirectory, fileSystem)
                            verify(eventSink).postEvent(TemporaryDirectoryCreatedEvent(container, homeDirectoryPath))
                        }

                        it("returns a user and group configuration with the current user's user and group IDs") {
                            assertThat(configuration.userAndGroup, equalTo(UserAndGroup(0, 0)))
                        }

                        it("creates a directory for the volume mount path that does not exist") {
                            assertThat(Files.exists(fileSystem.getPath(directoryThatDoesNotExist)), equalTo(true))
                        }

                        it("creates a directory for the home directory") {
                            val homeDirectoryPath = localPathToHomeDirectory(configuration.volumeMounts, homeDirectory, fileSystem)
                            assertThat(Files.exists(homeDirectoryPath), equalTo(true))
                        }

                        it("sets the user for the created home directory to the current user") {
                            val homeDirectoryPath = localPathToHomeDirectory(configuration.volumeMounts, homeDirectory, fileSystem)
                            val owner = Files.readAttributes(homeDirectoryPath, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).owner()
                            assertThat(owner.name, equalTo("root"))
                        }

                        it("sets the group for the created home directory to the current user's primary group") {
                            val homeDirectoryPath = localPathToHomeDirectory(configuration.volumeMounts, homeDirectory, fileSystem)
                            val group = Files.readAttributes(homeDirectoryPath, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).group()
                            assertThat(group.name, equalTo("root"))
                        }
                    }

                    on("determining the user and group to use") {
                        val userAndGroup by runNullableForEachTest { provider.determineUserAndGroup(container) }

                        it("returns a user and group configuration with root's UID and GID") {
                            assertThat(userAndGroup, equalTo(UserAndGroup(0, 0)))
                        }
                    }
                }
            }
        }
    }
})

private fun hasReadOnlyVolumeMount(path: String) =
    anyElement(has(VolumeMount::containerPath, equalTo(path)) and has(VolumeMount::options, equalTo("ro")))

private fun hasDelegatedVolumeMount(path: String) =
    anyElement(has(VolumeMount::containerPath, equalTo(path)) and has(VolumeMount::options, equalTo("delegated")))

private fun localPathToPasswdFile(mounts: Set<VolumeMount>, fileSystem: FileSystem): Path = fileSystem.getPath(mounts.single { it.containerPath == "/etc/passwd" }.localPath)
private fun localPathToGroupFile(mounts: Set<VolumeMount>, fileSystem: FileSystem): Path = fileSystem.getPath(mounts.single { it.containerPath == "/etc/group" }.localPath)
private fun localPathToHomeDirectory(mounts: Set<VolumeMount>, homeDirectory: String, fileSystem: FileSystem): Path = fileSystem.getPath(mounts.single { it.containerPath == homeDirectory }.localPath)
