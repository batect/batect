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

package batect.execution

import batect.config.Container
import batect.config.RunAsCurrentUserConfig
import batect.docker.DockerVolumeMount
import batect.docker.DockerVolumeMountSource
import batect.docker.UserAndGroup
import batect.docker.client.DockerContainerType
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
import batect.testutils.withMessage
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.anyElement
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.throws
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

            val container = Container(
                "some-container",
                imageSourceDoesNotMatter(),
                runAsCurrentUserConfig = RunAsCurrentUserConfig.RunAsDefaultContainerUser
            )

            val systemInfo by createForEachTest {
                mock<SystemInfo> {
                    on { tempDirectory } doReturn fileSystem.getPath("/tmp")
                }
            }

            val nativeMethods = mock<NativeMethods>()

            DockerContainerType.values().forEach { containerType ->
                given("$containerType containers are in use") {
                    val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem, containerType) }

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
                    }

                    on("determining the user and group to use") {
                        val userAndGroup by runNullableForEachTest { provider.determineUserAndGroup(container) }

                        it("returns an empty user and group configuration") {
                            assertThat(userAndGroup, absent())
                        }
                    }
                }
            }

            describe("creating missing volume mount directories") {
                val directoryThatExists = "/existing/directory"
                val fileThatExists = "/existing/file"
                val directoryThatDoesNotExist = "/new/directory"
                val volumeMounts = setOf(
                    DockerVolumeMount(DockerVolumeMountSource.LocalPath(directoryThatExists), "/container/existing-directory", null),
                    DockerVolumeMount(DockerVolumeMountSource.LocalPath(fileThatExists), "/container/existing-file", null),
                    DockerVolumeMount(DockerVolumeMountSource.LocalPath(directoryThatDoesNotExist), "/container/new-directory", null),
                    DockerVolumeMount(DockerVolumeMountSource.Volume("my-volume"), "/container/volume", null)
                )

                beforeEachTest {
                    Files.createDirectories(fileSystem.getPath(directoryThatExists))
                    Files.createFile(fileSystem.getPath(fileThatExists))
                }

                given("Linux containers are in use") {
                    val containerType = DockerContainerType.Linux
                    val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem, containerType) }

                    beforeEachTest { provider.createMissingVolumeMountDirectories(volumeMounts, container) }

                    it("does not create a directory for the volume mount path that does not exist") {
                        assertThat(Files.exists(fileSystem.getPath(directoryThatDoesNotExist)), equalTo(false))
                    }
                }

                given("Windows containers are in use") {
                    val containerType = DockerContainerType.Windows
                    val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem, containerType) }

                    beforeEachTest { provider.createMissingVolumeMountDirectories(volumeMounts, container) }

                    it("creates a directory for the volume mount path that does not exist") {
                        assertThat(Files.exists(fileSystem.getPath(directoryThatDoesNotExist)), equalTo(true))
                    }
                }
            }
        }

        given("the container has 'run as current user' enabled") {
            val homeDirectory = "/home/some-user"
            val runAsCurrentUserConfig = RunAsCurrentUserConfig.RunAsCurrentUser(homeDirectory)

            val container = Container(
                "some-container",
                imageSourceDoesNotMatter(),
                runAsCurrentUserConfig = runAsCurrentUserConfig
            )

            given("the application is running on Windows") {
                val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.windows()) }

                val systemInfo by createForEachTest {
                    mock<SystemInfo> {
                        on { operatingSystem } doReturn OperatingSystem.Windows
                        on { tempDirectory } doReturn fileSystem.getPath("C:\\temp")
                    }
                }

                beforeEachTest {
                    Files.createDirectories(systemInfo.tempDirectory)
                }

                val nativeMethods = mock<NativeMethods> {
                    on { getUserName() } doThrow UnsupportedOperationException("This shouldn't be called on Windows")
                    on { getUserId() } doThrow UnsupportedOperationException("This shouldn't be called on Windows")
                    on { getGroupId() } doThrow UnsupportedOperationException("This shouldn't be called on Windows")
                    on { getGroupName() } doThrow UnsupportedOperationException("This shouldn't be called on Windows")
                }

                given("Linux containers are being used") {
                    val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem, DockerContainerType.Linux) }

                    on("generating the configuration") {
                        val configuration by runForEachTest { provider.generateConfiguration(container, eventSink) }

                        it("returns a set of volume mounts for the passwd and group file and home directory") {
                            assertThat(
                                configuration.volumeMounts.mapToSet { it.containerPath },
                                equalTo(
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
                                content,
                                equalTo(
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
                                content,
                                equalTo(
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

                given("Windows containers are being used") {
                    val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem, DockerContainerType.Windows) }

                    on("generating the configuration") {
                        it("throws an appropriate exception") {
                            assertThat({ provider.generateConfiguration(container, eventSink) }, throws<RunAsCurrentUserConfigurationException>(withMessage("Container 'some-container' has run as current user enabled, but this is not supported for Windows containers.")))
                        }
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

                beforeEachTest {
                    Files.createDirectories(systemInfo.tempDirectory)
                }

                given("the current user is not root") {
                    val nativeMethods = mock<NativeMethods> {
                        on { getUserId() } doReturn 123
                        on { getUserName() } doReturn "the-user"
                        on { getGroupId() } doReturn 456
                        on { getGroupName() } doReturn "the-user's-group"
                    }

                    val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem, DockerContainerType.Linux) }

                    on("generating the configuration") {
                        val configuration by runForEachTest { provider.generateConfiguration(container, eventSink) }

                        it("returns a set of volume mounts for the passwd and group file and home directory") {
                            assertThat(
                                configuration.volumeMounts.mapToSet { it.containerPath },
                                equalTo(
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
                                content,
                                equalTo(
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
                                content,
                                equalTo(
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

                    val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem, DockerContainerType.Linux) }

                    on("generating the configuration") {
                        val configuration by runForEachTest { provider.generateConfiguration(container, eventSink) }

                        it("returns a set of volume mounts for the passwd and group file") {
                            assertThat(
                                configuration.volumeMounts.mapToSet { it.containerPath },
                                equalTo(
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
                                content,
                                equalTo(
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
                                content,
                                equalTo(
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

            describe("regardless of the current operating system") {
                val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
                val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(mock(), mock(), fileSystem, mock()) }

                describe("creating missing volume mount directories") {
                    given("mounts for existing local directories and files and a non-existent local path") {
                        val directoryThatExists = "/existing/directory"
                        val fileThatExists = "/existing/file"
                        val directoryThatDoesNotExist = "/new/directory"
                        val volumeMounts = setOf(
                            DockerVolumeMount(DockerVolumeMountSource.LocalPath(directoryThatExists), "/container/existing-directory", null),
                            DockerVolumeMount(DockerVolumeMountSource.LocalPath(fileThatExists), "/container/existing-file", null),
                            DockerVolumeMount(DockerVolumeMountSource.LocalPath(directoryThatDoesNotExist), "/container/new-directory", null),
                            DockerVolumeMount(DockerVolumeMountSource.Volume("my-volume"), "/container/volume", null)
                        )

                        beforeEachTest {
                            Files.createDirectories(fileSystem.getPath(directoryThatExists))
                            Files.createFile(fileSystem.getPath(fileThatExists))
                        }

                        beforeEachTest { provider.createMissingVolumeMountDirectories(volumeMounts, container) }

                        it("creates a directory for the volume mount path that does not exist") {
                            assertThat(Files.exists(fileSystem.getPath(directoryThatDoesNotExist)), equalTo(true))
                        }
                    }

                    given("a local path is mounted in another local mount's container path") {
                        val outerLocalPath = "/local/source/outer"
                        val innerLocalPath = "/local/source/inner"

                        val volumeMounts = setOf(
                            DockerVolumeMount(DockerVolumeMountSource.LocalPath(outerLocalPath), "/container/local-mount", null),
                            DockerVolumeMount(DockerVolumeMountSource.LocalPath(innerLocalPath), "/container/local-mount/path", null)
                        )

                        beforeEachTest { provider.createMissingVolumeMountDirectories(volumeMounts, container) }

                        it("creates a directory for the local paths that does not exist") {
                            assertThat(Files.exists(fileSystem.getPath(outerLocalPath)), equalTo(true))
                            assertThat(Files.exists(fileSystem.getPath(innerLocalPath)), equalTo(true))
                        }

                        it("creates directories within the outer local path for the inner mount's mount point") {
                            assertThat(Files.exists(fileSystem.getPath(outerLocalPath, "path")), equalTo(true))
                        }
                    }

                    given("a volume mount and a local mount that do not have overlapping container paths") {
                        val localPath = "/local/source"
                        val volumeMounts = setOf(
                            DockerVolumeMount(DockerVolumeMountSource.LocalPath(localPath), "/container/local-mount", null),
                            DockerVolumeMount(DockerVolumeMountSource.Volume("my-volume"), "/container/volume", null)
                        )

                        beforeEachTest { provider.createMissingVolumeMountDirectories(volumeMounts, container) }

                        it("creates a directory for the local path that does not exist") {
                            assertThat(Files.exists(fileSystem.getPath(localPath)), equalTo(true))
                        }

                        it("does not create anything inside the local directory") {
                            assertThat(Files.list(fileSystem.getPath(localPath)).toArray().toList(), isEmpty)
                        }
                    }

                    given("a volume mount and a local mount where the local path is mounted inside the volume mount's container path") {
                        val localPath = "/local/source"
                        val volumeMounts = setOf(
                            DockerVolumeMount(DockerVolumeMountSource.LocalPath(localPath), "/container/volume/local-mount", null),
                            DockerVolumeMount(DockerVolumeMountSource.Volume("my-volume"), "/container/volume", null)
                        )

                        beforeEachTest { provider.createMissingVolumeMountDirectories(volumeMounts, container) }

                        it("creates a directory for the local path that does not exist") {
                            assertThat(Files.exists(fileSystem.getPath(localPath)), equalTo(true))
                        }

                        it("does not create anything inside the local directory") {
                            assertThat(Files.list(fileSystem.getPath(localPath)).toArray().toList(), isEmpty)
                        }
                    }

                    given("a volume is mounted within a local mount's container path") {
                        val localPath = "/local/source"
                        val volumeMounts = setOf(
                            DockerVolumeMount(DockerVolumeMountSource.LocalPath(localPath), "/container/local-mount", null),
                            DockerVolumeMount(DockerVolumeMountSource.Volume("my-volume"), "/container/local-mount/volume", null)
                        )

                        beforeEachTest { provider.createMissingVolumeMountDirectories(volumeMounts, container) }

                        it("creates a directory for the local path that does not exist") {
                            assertThat(Files.exists(fileSystem.getPath(localPath)), equalTo(true))
                        }

                        it("creates directories within the local path for the volume's mount point") {
                            assertThat(Files.exists(fileSystem.getPath(localPath, "volume")), equalTo(true))
                        }
                    }

                    given("a volume is mounted nested within a local mount's container path") {
                        val localPath = "/local/source"
                        val volumeMounts = setOf(
                            DockerVolumeMount(DockerVolumeMountSource.LocalPath(localPath), "/container/local-mount", null),
                            DockerVolumeMount(DockerVolumeMountSource.Volume("my-volume"), "/container/local-mount/path/to/volume", null)
                        )

                        beforeEachTest { provider.createMissingVolumeMountDirectories(volumeMounts, container) }

                        it("creates a directory for the local path that does not exist") {
                            assertThat(Files.exists(fileSystem.getPath(localPath)), equalTo(true))
                        }

                        it("creates directories within the local path for the volume's mount point") {
                            assertThat(Files.exists(fileSystem.getPath(localPath, "path", "to", "volume")), equalTo(true))
                        }
                    }

                    given("a volume is mounted with a local mount's container path which is within another local mount's container path") {
                        val outerLocalPath = "/local/source/outer"
                        val innerLocalPath = "/local/source/inner"

                        val volumeMounts = setOf(
                            DockerVolumeMount(DockerVolumeMountSource.LocalPath(outerLocalPath), "/container/local-mount", null),
                            DockerVolumeMount(DockerVolumeMountSource.LocalPath(innerLocalPath), "/container/local-mount/path", null),
                            DockerVolumeMount(DockerVolumeMountSource.Volume("my-volume"), "/container/local-mount/path/to/volume", null)
                        )

                        beforeEachTest { provider.createMissingVolumeMountDirectories(volumeMounts, container) }

                        it("creates a directory for the local paths that does not exist") {
                            assertThat(Files.exists(fileSystem.getPath(outerLocalPath)), equalTo(true))
                            assertThat(Files.exists(fileSystem.getPath(innerLocalPath)), equalTo(true))
                        }

                        it("creates directories within the outer local path for the inner mount's mount point") {
                            assertThat(Files.exists(fileSystem.getPath(outerLocalPath, "path")), equalTo(true))
                        }

                        it("creates directories within the closest enclosing local path for the volume's mount point") {
                            assertThat(Files.exists(fileSystem.getPath(innerLocalPath, "to", "volume")), equalTo(true))
                        }
                    }

                    given("a volume is mounted within a second volume's container path, and that second volume is mounted inside a local mount's container path") {
                        val localPath = "/local/source"
                        val volumeMounts = setOf(
                            DockerVolumeMount(DockerVolumeMountSource.LocalPath(localPath), "/container/local-mount", null),
                            DockerVolumeMount(DockerVolumeMountSource.Volume("volume-1"), "/container/local-mount/volume-1", null),
                            DockerVolumeMount(DockerVolumeMountSource.Volume("volume-2"), "/container/local-mount/volume-1/volume-2", null)
                        )

                        beforeEachTest { provider.createMissingVolumeMountDirectories(volumeMounts, container) }

                        it("creates a directory for the local path that does not exist") {
                            assertThat(Files.exists(fileSystem.getPath(localPath)), equalTo(true))
                        }

                        it("creates directories within the local path for the second volume's mount point") {
                            assertThat(Files.exists(fileSystem.getPath(localPath, "volume-1")), equalTo(true))
                        }

                        it("does not create a directory within the local path for the first volume's mount point") {
                            assertThat(Files.exists(fileSystem.getPath(localPath, "volume-1", "volume-2")), equalTo(false))
                        }
                    }

                    given("a volume is mounted within a local mount's container path, and that local mount is mounted within a second volume's container path") {
                        val localPath = "/local/source"
                        val volumeMounts = setOf(
                            DockerVolumeMount(DockerVolumeMountSource.Volume("volume-1"), "/container/volume-1", null),
                            DockerVolumeMount(DockerVolumeMountSource.LocalPath(localPath), "/container/volume-1/local-mount", null),
                            DockerVolumeMount(DockerVolumeMountSource.Volume("volume-2"), "/container/volume-1/local-mount/volume-2", null)
                        )

                        beforeEachTest { provider.createMissingVolumeMountDirectories(volumeMounts, container) }

                        it("creates a directory for the local path that does not exist") {
                            assertThat(Files.exists(fileSystem.getPath(localPath)), equalTo(true))
                        }

                        it("creates directories within the local path for the first volume's mount point") {
                            assertThat(Files.exists(fileSystem.getPath(localPath, "volume-2")), equalTo(true))
                        }
                    }
                }
            }
        }
    }
})

private fun hasReadOnlyVolumeMount(path: String) =
    anyElement(has(DockerVolumeMount::containerPath, equalTo(path)) and has(DockerVolumeMount::options, equalTo("ro")))

private fun hasDelegatedVolumeMount(path: String) =
    anyElement(has(DockerVolumeMount::containerPath, equalTo(path)) and has(DockerVolumeMount::options, equalTo("delegated")))

private fun localPathToPasswdFile(mounts: Set<DockerVolumeMount>, fileSystem: FileSystem): Path = fileSystem.getPath(pathToLocalMount(mounts) { it.containerPath == "/etc/passwd" })
private fun localPathToGroupFile(mounts: Set<DockerVolumeMount>, fileSystem: FileSystem): Path = fileSystem.getPath(pathToLocalMount(mounts) { it.containerPath == "/etc/group" })
private fun localPathToHomeDirectory(mounts: Set<DockerVolumeMount>, homeDirectory: String, fileSystem: FileSystem): Path = fileSystem.getPath(pathToLocalMount(mounts) { it.containerPath == homeDirectory })

private fun pathToLocalMount(mounts: Set<DockerVolumeMount>, predicate: (DockerVolumeMount) -> Boolean): String =
    (mounts.single(predicate).source as DockerVolumeMountSource.LocalPath).path
