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
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView

class RunAsCurrentUserConfigurationProvider(
    private val systemInfo: SystemInfo,
    private val nativeMethods: NativeMethods,
    private val fileSystem: FileSystem
) {
    fun determineUserAndGroup(container: Container): UserAndGroup? = when (container.runAsCurrentUserConfig) {
        is RunAsCurrentUserConfig.RunAsDefaultContainerUser -> null
        is RunAsCurrentUserConfig.RunAsCurrentUser -> UserAndGroup(determineUserId(), determineGroupId())
    }

    fun generateConfiguration(container: Container, eventSink: TaskEventSink): RunAsCurrentUserConfiguration = when (container.runAsCurrentUserConfig) {
        is RunAsCurrentUserConfig.RunAsCurrentUser -> {
            val userId = determineUserId()
            val userName = determineUserName()
            val groupId = determineGroupId()
            val groupName = determineGroupName()

            val volumeMounts = createMounts(container, container.runAsCurrentUserConfig, userId, userName, groupId, groupName, eventSink)
            createMissingVolumeMountDirectories(container)

            RunAsCurrentUserConfiguration(volumeMounts, UserAndGroup(userId, groupId))
        }
        is RunAsCurrentUserConfig.RunAsDefaultContainerUser -> RunAsCurrentUserConfiguration(emptySet(), null)
    }

    private fun determineUserId(): Int = when (systemInfo.operatingSystem) {
        OperatingSystem.Windows -> 0
        else -> nativeMethods.getUserId()
    }

    private fun determineGroupId(): Int = when (systemInfo.operatingSystem) {
        OperatingSystem.Windows -> 0
        else -> nativeMethods.getGroupId()
    }

    private fun determineUserName(): String = when (systemInfo.operatingSystem) {
        OperatingSystem.Windows -> "root"
        else -> nativeMethods.getUserName()
    }

    private fun determineGroupName(): String = when (systemInfo.operatingSystem) {
        OperatingSystem.Windows -> "root"
        else -> nativeMethods.getGroupName()
    }

    private fun createMounts(
        container: Container,
        runAsCurrentUserConfig: RunAsCurrentUserConfig.RunAsCurrentUser,
        userId: Int,
        userName: String,
        groupId: Int,
        groupName: String,
        eventSink: TaskEventSink
    ): Set<VolumeMount> {
        val passwdFile = createPasswdFile(runAsCurrentUserConfig, userId, userName, groupId)
        eventSink.postEvent(TemporaryFileCreatedEvent(container, passwdFile))

        val groupFile = createGroupFile(userName, groupId, groupName)
        eventSink.postEvent(TemporaryFileCreatedEvent(container, groupFile))

        val homeDirectory = createHomeDirectory()
        eventSink.postEvent(TemporaryDirectoryCreatedEvent(container, homeDirectory))

        return setOf(
            VolumeMount(passwdFile.toString(), "/etc/passwd", "ro"),
            VolumeMount(groupFile.toString(), "/etc/group", "ro"),
            VolumeMount(homeDirectory.toString(), runAsCurrentUserConfig.homeDirectory, "delegated")
        )
    }

    private fun createPasswdFile(runAsCurrentUserConfig: RunAsCurrentUserConfig.RunAsCurrentUser, userId: Int, userName: String, groupId: Int): Path {
        val path = createTempFile("passwd")
        val homeDirectory = runAsCurrentUserConfig.homeDirectory

        val lines = if (userId == 0) {
            listOf("root:x:0:0:root:$homeDirectory:/bin/sh")
        } else {
            listOf(
                "root:x:0:0:root:/root:/bin/sh",
                "$userName:x:$userId:$groupId:$userName:$homeDirectory:/bin/sh"
            )
        }

        Files.write(path, lines)

        return path
    }

    private fun createGroupFile(userName: String, groupId: Int, groupName: String): Path {
        val path = createTempFile("group")
        val rootGroup = "root:x:0:root"

        val lines = if (groupId == 0) {
            listOf(rootGroup)
        } else {
            listOf(
                rootGroup,
                "$groupName:x:$groupId:$userName"
            )
        }

        Files.write(path, lines)

        return path
    }

    private fun createHomeDirectory(): Path {
        val path = Files.createTempDirectory(systemInfo.tempDirectory, "batect-home-")

        if (systemInfo.operatingSystem != OperatingSystem.Windows) {
            val attributeView = Files.getFileAttributeView(path, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
            val lookupService = fileSystem.userPrincipalLookupService

            attributeView.setOwner(lookupService.lookupPrincipalByName(nativeMethods.getUserName()))
            attributeView.setGroup(lookupService.lookupPrincipalByGroupName(nativeMethods.getGroupName()))
        }

        return path
    }

    private fun createTempFile(name: String): Path = Files.createTempFile(systemInfo.tempDirectory, "batect-$name-", "")

    private fun createMissingVolumeMountDirectories(container: Container) {
        container.volumeMounts.forEach { volumeMount ->
            val path = fileSystem.getPath(volumeMount.localPath)

            if (!Files.exists(path)) {
                Files.createDirectories(path)
            }
        }
    }
}
