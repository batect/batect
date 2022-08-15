/*
    Copyright 2017-2022 Charles Korn.

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

import batect.config.CacheMount
import batect.config.Container
import batect.config.RunAsCurrentUserConfig
import batect.docker.DockerContainer
import batect.docker.DockerContainerType
import batect.dockerclient.ContainerReference
import batect.dockerclient.DockerClient
import batect.dockerclient.DockerClientException
import batect.dockerclient.HostMount
import batect.dockerclient.UploadDirectory
import batect.dockerclient.UploadFile
import batect.dockerclient.UserAndGroup
import batect.dockerclient.VolumeMount
import batect.dockerclient.VolumeReference
import batect.os.NativeMethods
import batect.os.OperatingSystem
import batect.os.SystemInfo
import batect.testutils.beforeEachTestSuspend
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.itSuspend
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.runNullableForEachTest
import batect.testutils.withCause
import batect.testutils.withMessage
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.throws
import okio.Path.Companion.toPath
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files

object RunAsCurrentUserConfigurationProviderSpec : Spek({
    describe("a 'run as current user' configuration provider") {
        val dockerClient by createForEachTest { mock<DockerClient>() }
        val dockerContainer = DockerContainer(ContainerReference("abc-123"), "abc-123-name")

        given("the container has 'run as current user' disabled") {
            val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }

            val container = Container(
                "some-container",
                imageSourceDoesNotMatter(),
                runAsCurrentUserConfig = RunAsCurrentUserConfig.RunAsDefaultContainerUser,
                volumeMounts = setOf(CacheMount("some-cache", "/my-cache"))
            )

            val systemInfo by createForEachTest { mock<SystemInfo>() }
            val nativeMethods = mock<NativeMethods>()

            DockerContainerType.values().forEach { containerType ->
                given("$containerType containers are in use") {
                    val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem, containerType, dockerClient) }

                    on("applying the configuration to the container") {
                        runForEachTest { provider.applyConfigurationToContainer(container, dockerContainer) }

                        it("does not upload any files or directories to the container") {
                            verifyNoInteractions(dockerClient)
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
                    HostMount(directoryThatExists.toPath(), "/container/existing-directory", null),
                    HostMount(fileThatExists.toPath(), "/container/existing-file", null),
                    HostMount(directoryThatDoesNotExist.toPath(), "/container/new-directory", null),
                    VolumeMount(VolumeReference("my-volume"), "/container/volume", null)
                )

                beforeEachTest {
                    Files.createDirectories(fileSystem.getPath(directoryThatExists))
                    Files.createFile(fileSystem.getPath(fileThatExists))
                }

                given("Linux containers are in use") {
                    val containerType = DockerContainerType.Linux
                    val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem, containerType, dockerClient) }

                    beforeEachTest { provider.createMissingVolumeMountDirectories(volumeMounts, container) }

                    it("does not create a directory for the volume mount path that does not exist") {
                        assertThat(Files.exists(fileSystem.getPath(directoryThatDoesNotExist)), equalTo(false))
                    }
                }

                given("Windows containers are in use") {
                    val containerType = DockerContainerType.Windows
                    val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem, containerType, dockerClient) }

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
                runAsCurrentUserConfig = runAsCurrentUserConfig,
                volumeMounts = setOf(CacheMount("some-cache", "/caches/first-cache"))
            )

            given("the application is running on Windows") {
                val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.windows()) }

                val systemInfo by createForEachTest {
                    mock<SystemInfo> {
                        on { operatingSystem } doReturn OperatingSystem.Windows
                    }
                }

                val nativeMethods = mock<NativeMethods> {
                    on { getUserName() } doThrow UnsupportedOperationException("This shouldn't be called on Windows")
                    on { getUserId() } doThrow UnsupportedOperationException("This shouldn't be called on Windows")
                    on { getGroupId() } doThrow UnsupportedOperationException("This shouldn't be called on Windows")
                    on { getGroupName() } doThrow UnsupportedOperationException("This shouldn't be called on Windows")
                }

                given("Linux containers are being used") {
                    val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem, DockerContainerType.Linux, dockerClient) }

                    on("applying configuration to the container") {
                        runForEachTest { provider.applyConfigurationToContainer(container, dockerContainer) }

                        itSuspend("uploads /etc/passwd and /etc/group files for the root user and group to the container") {
                            val passwdContent = """
                                |root:x:0:0:root:/home/some-user:/bin/sh
                            """.trimMargin()

                            val groupContent = """
                                |root:x:0:root
                            """.trimMargin()

                            verify(dockerClient).uploadToContainer(
                                dockerContainer.reference,
                                setOf(
                                    UploadFile("passwd", 0, 0, "644".toInt(8), passwdContent.toByteArray(Charsets.UTF_8)),
                                    UploadFile("group", 0, 0, "644".toInt(8), groupContent.toByteArray(Charsets.UTF_8))
                                ),
                                "/etc"
                            )
                        }

                        itSuspend("uploads the configured home directory to the container, with the owner and group set to root") {
                            verify(dockerClient).uploadToContainer(
                                dockerContainer.reference,
                                setOf(
                                    UploadDirectory("some-user", 0, 0, "755".toInt(8))
                                ),
                                "/home"
                            )
                        }

                        itSuspend("uploads the configured cache directory to the container, with the owner and group set to root") {
                            verify(dockerClient).uploadToContainer(
                                dockerContainer.reference,
                                setOf(
                                    UploadDirectory("first-cache", 0, 0, "755".toInt(8))
                                ),
                                "/caches"
                            )
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
                    val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem, DockerContainerType.Windows, dockerClient) }

                    on("applying configuration to the container") {
                        it("throws an appropriate exception") {
                            assertThat({ provider.applyConfigurationToContainer(container, dockerContainer) }, throws<RunAsCurrentUserConfigurationException>(withMessage("Container 'some-container' has run as current user enabled, but this is not supported for Windows containers.")))
                        }
                    }
                }
            }

            given("the application is not running on Windows") {
                val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix().toBuilder().setAttributeViews("posix").build()) }

                val systemInfo by createForEachTest {
                    mock<SystemInfo> {
                        on { operatingSystem } doReturn OperatingSystem.Other
                    }
                }

                given("the current user is not root") {
                    val nativeMethods = mock<NativeMethods> {
                        on { getUserId() } doReturn 123
                        on { getUserName() } doReturn "the-user"
                        on { getGroupId() } doReturn 456
                        on { getGroupName() } doReturn "the-user's-group"
                    }

                    val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem, DockerContainerType.Linux, dockerClient) }

                    given("the configured home directory is in a subdirectory of the filesystem") {
                        on("applying configuration to the container") {
                            runForEachTest { provider.applyConfigurationToContainer(container, dockerContainer) }

                            itSuspend("uploads /etc/passwd and /etc/group files for the root user and group and the current user's user and group to the container") {
                                val passwdContent = """
                                    |root:x:0:0:root:/root:/bin/sh
                                    |the-user:x:123:456:the-user:/home/some-user:/bin/sh
                                """.trimMargin()

                                val groupContent = """
                                    |root:x:0:root
                                    |the-user's-group:x:456:the-user
                                """.trimMargin()

                                verify(dockerClient).uploadToContainer(
                                    dockerContainer.reference,
                                    setOf(
                                        UploadFile("passwd", 0, 0, "644".toInt(8), passwdContent.toByteArray(Charsets.UTF_8)),
                                        UploadFile("group", 0, 0, "644".toInt(8), groupContent.toByteArray(Charsets.UTF_8))
                                    ),
                                    "/etc"
                                )
                            }

                            itSuspend("uploads the configured home directory to the container, with the owner and group set to the current user's user and group") {
                                verify(dockerClient).uploadToContainer(
                                    dockerContainer.reference,
                                    setOf(
                                        UploadDirectory("some-user", 123, 456, "755".toInt(8))
                                    ),
                                    "/home"
                                )
                            }

                            itSuspend("uploads the configured cache directory to the container, with the owner and group set to the current user's user and group") {
                                verify(dockerClient).uploadToContainer(
                                    dockerContainer.reference,
                                    setOf(
                                        UploadDirectory("first-cache", 123, 456, "755".toInt(8))
                                    ),
                                    "/caches"
                                )
                            }
                        }

                        on("determining the user and group to use") {
                            val userAndGroup by runNullableForEachTest { provider.determineUserAndGroup(container) }

                            it("returns a user and group configuration with the current user's user and group IDs") {
                                assertThat(userAndGroup, equalTo(UserAndGroup(123, 456)))
                            }
                        }
                    }

                    given("the configured home directory is in the root of the filesystem") {
                        val containerWithHomeDirectoryInRoot = container.copy(runAsCurrentUserConfig = RunAsCurrentUserConfig.RunAsCurrentUser("/my-home"))

                        on("applying configuration to the container") {
                            runForEachTest { provider.applyConfigurationToContainer(containerWithHomeDirectoryInRoot, dockerContainer) }

                            itSuspend("uploads the configured home directory to the container, with the owner and group set to the current user's user and group") {
                                verify(dockerClient).uploadToContainer(
                                    dockerContainer.reference,
                                    setOf(
                                        UploadDirectory("my-home", 123, 456, "755".toInt(8))
                                    ),
                                    "/"
                                )
                            }
                        }
                    }

                    given("the configured home directory is not an absolute path") {
                        val containerWithNonAbsoluteHomeDirectory = container.copy(runAsCurrentUserConfig = RunAsCurrentUserConfig.RunAsCurrentUser("my-home"))

                        on("applying configuration to the container") {
                            it("throws an appropriate exception") {
                                assertThat({ provider.applyConfigurationToContainer(containerWithNonAbsoluteHomeDirectory, dockerContainer) }, throws<RunAsCurrentUserConfigurationException>(withMessage("Container 'some-container' has an invalid home directory configured: 'my-home' is not an absolute path.")))
                            }
                        }
                    }

                    given("the configured home directory and cache directories are provided with trailing slashes") {
                        val containerWithTrailingSlashes = Container(
                            "some-container",
                            imageSourceDoesNotMatter(),
                            runAsCurrentUserConfig = RunAsCurrentUserConfig.RunAsCurrentUser("/home/some-user/"),
                            volumeMounts = setOf(CacheMount("some-cache", "/caches/first-cache/"))
                        )

                        on("applying configuration to the container") {
                            runForEachTest { provider.applyConfigurationToContainer(containerWithTrailingSlashes, dockerContainer) }

                            itSuspend("uploads the configured home directory to the container, with the owner and group set to the current user's user and group") {
                                verify(dockerClient).uploadToContainer(
                                    dockerContainer.reference,
                                    setOf(
                                        UploadDirectory("some-user", 123, 456, "755".toInt(8))
                                    ),
                                    "/home"
                                )
                            }

                            itSuspend("uploads the configured cache directory to the container, with the owner and group set to the current user's user and group") {
                                verify(dockerClient).uploadToContainer(
                                    dockerContainer.reference,
                                    setOf(
                                        UploadDirectory("first-cache", 123, 456, "755".toInt(8))
                                    ),
                                    "/caches"
                                )
                            }
                        }
                    }

                    given("a configured cache directory is in the root of the filesystem") {
                        val containerWithCacheDirectoryInRoot = container.copy(volumeMounts = setOf(CacheMount("some-cache", "/first-cache")))

                        on("applying configuration to the container") {
                            runForEachTest { provider.applyConfigurationToContainer(containerWithCacheDirectoryInRoot, dockerContainer) }

                            itSuspend("uploads the configured cache directory to the container, with the owner and group set to the current user's user and group") {
                                verify(dockerClient).uploadToContainer(
                                    dockerContainer.reference,
                                    setOf(
                                        UploadDirectory("first-cache", 123, 456, "755".toInt(8))
                                    ),
                                    "/"
                                )
                            }
                        }
                    }

                    given("a configured cache directory is not an absolute path") {
                        val containerWithNonAbsoluteCacheDirectory = container.copy(volumeMounts = setOf(CacheMount("some-cache", "first-cache")))

                        on("applying configuration to the container") {
                            it("throws an appropriate exception") {
                                assertThat({ provider.applyConfigurationToContainer(containerWithNonAbsoluteCacheDirectory, dockerContainer) }, throws<RunAsCurrentUserConfigurationException>(withMessage("Container 'some-container' has an invalid cache mount configured: 'first-cache' is not an absolute path.")))
                            }
                        }
                    }

                    given("uploading to the container fails") {
                        val exception = DockerClientException("Something went wrong.")

                        beforeEachTestSuspend {
                            whenever(dockerClient.uploadToContainer(any(), any(), any())).thenThrow(exception)
                        }

                        on("applying configuration to the container") {
                            it("throws an appropriate exception") {
                                assertThat(
                                    { provider.applyConfigurationToContainer(container, dockerContainer) },
                                    throws<RunAsCurrentUserConfigurationException>(
                                        withMessage("Could not apply 'run as current user' configuration to container 'some-container': Something went wrong.") and
                                            withCause(exception)
                                    )
                                )
                            }
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

                    val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo, nativeMethods, fileSystem, DockerContainerType.Linux, dockerClient) }

                    on("applying configuration to the container") {
                        runForEachTest { provider.applyConfigurationToContainer(container, dockerContainer) }

                        itSuspend("uploads /etc/passwd and /etc/group files for the root user and group to the container") {
                            val passwdContent = """
                                    |root:x:0:0:root:/home/some-user:/bin/sh
                            """.trimMargin()

                            val groupContent = """
                                    |root:x:0:root
                            """.trimMargin()

                            verify(dockerClient).uploadToContainer(
                                dockerContainer.reference,
                                setOf(
                                    UploadFile("passwd", 0, 0, "644".toInt(8), passwdContent.toByteArray(Charsets.UTF_8)),
                                    UploadFile("group", 0, 0, "644".toInt(8), groupContent.toByteArray(Charsets.UTF_8))
                                ),
                                "/etc"
                            )
                        }

                        itSuspend("uploads the configured home directory to the container, with the owner and group set to root") {
                            verify(dockerClient).uploadToContainer(
                                dockerContainer.reference,
                                setOf(
                                    UploadDirectory("some-user", 0, 0, "755".toInt(8))
                                ),
                                "/home"
                            )
                        }

                        itSuspend("uploads the configured cache directory to the container, with the owner and group set to root") {
                            verify(dockerClient).uploadToContainer(
                                dockerContainer.reference,
                                setOf(
                                    UploadDirectory("first-cache", 0, 0, "755".toInt(8))
                                ),
                                "/caches"
                            )
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
                val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(mock(), mock(), fileSystem, mock(), mock()) }

                describe("creating missing volume mount directories") {
                    given("mounts for existing local directories and files and a non-existent local path") {
                        val directoryThatExists = "/existing/directory"
                        val fileThatExists = "/existing/file"
                        val directoryThatDoesNotExist = "/new/directory"
                        val volumeMounts = setOf(
                            HostMount(directoryThatExists.toPath(), "/container/existing-directory", null),
                            HostMount(fileThatExists.toPath(), "/container/existing-file", null),
                            HostMount(directoryThatDoesNotExist.toPath(), "/container/new-directory", null),
                            VolumeMount(VolumeReference("my-volume"), "/container/volume", null)
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
                            HostMount(outerLocalPath.toPath(), "/container/local-mount", null),
                            HostMount(innerLocalPath.toPath(), "/container/local-mount/path", null)
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
                            HostMount(localPath.toPath(), "/container/local-mount", null),
                            VolumeMount(VolumeReference("my-volume"), "/container/volume", null)
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
                            HostMount(localPath.toPath(), "/container/volume/local-mount", null),
                            VolumeMount(VolumeReference("my-volume"), "/container/volume", null)
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
                            HostMount(localPath.toPath(), "/container/local-mount", null),
                            VolumeMount(VolumeReference("my-volume"), "/container/local-mount/volume", null)
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
                            HostMount(localPath.toPath(), "/container/local-mount", null),
                            VolumeMount(VolumeReference("my-volume"), "/container/local-mount/path/to/volume", null)
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
                            HostMount(outerLocalPath.toPath(), "/container/local-mount", null),
                            HostMount(innerLocalPath.toPath(), "/container/local-mount/path", null),
                            VolumeMount(VolumeReference("my-volume"), "/container/local-mount/path/to/volume", null)
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
                            HostMount(localPath.toPath(), "/container/local-mount", null),
                            VolumeMount(VolumeReference("volume-1"), "/container/local-mount/volume-1", null),
                            VolumeMount(VolumeReference("volume-2"), "/container/local-mount/volume-1/volume-2", null)
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
                            VolumeMount(VolumeReference("volume-1"), "/container/volume-1", null),
                            HostMount(localPath.toPath(), "/container/volume-1/local-mount", null),
                            VolumeMount(VolumeReference("volume-2"), "/container/volume-1/local-mount/volume-2", null)
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
