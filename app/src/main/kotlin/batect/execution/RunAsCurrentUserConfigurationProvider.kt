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
import batect.config.VolumeMount
import batect.docker.UserAndGroup
import batect.execution.model.events.TaskEventSink
import batect.execution.model.events.TemporaryDirectoryCreatedEvent
import batect.execution.model.events.TemporaryFileCreatedEvent
import batect.os.SystemInfo
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView

class RunAsCurrentUserConfigurationProvider(
    private val systemInfo: SystemInfo,
    private val fileSystem: FileSystem
) {
    private val temporaryDirectory = fileSystem.getPath("/tmp")

    fun generateConfiguration(container: Container, eventSink: TaskEventSink): RunAsCurrentUserConfiguration {
        if (container.runAsCurrentUserConfig.enabled) {
            val volumeMounts = createMounts(container, eventSink)
            createMissingVolumeMountDirectories(container)

            val homeDirectory = createHomeDirectory()
            eventSink.postEvent(TemporaryDirectoryCreatedEvent(container, homeDirectory))

            val pathsToCopyToContainer = mapOf(
                homeDirectory to container.runAsCurrentUserConfig.homeDirectory!!
            )

            return RunAsCurrentUserConfiguration(volumeMounts, UserAndGroup(systemInfo.userId, systemInfo.groupId), pathsToCopyToContainer)
        } else {
            return RunAsCurrentUserConfiguration(emptySet(), null, emptyMap())
        }
    }

    private fun createMounts(container: Container, eventSink: TaskEventSink): Set<VolumeMount> {
        val passwdFile = createPasswdFile(container)
        eventSink.postEvent(TemporaryFileCreatedEvent(container, passwdFile))

        val groupFile = createGroupFile()
        eventSink.postEvent(TemporaryFileCreatedEvent(container, groupFile))

        return setOf(
            VolumeMount(passwdFile.toString(), "/etc/passwd", "ro"),
            VolumeMount(groupFile.toString(), "/etc/group", "ro")
        )
    }

    private fun createPasswdFile(container: Container): Path {
        val path = createTempFile("passwd")
        val homeDirectory = container.runAsCurrentUserConfig.homeDirectory!!

        val lines = if (systemInfo.userId == 0) {
            listOf("root:x:0:0:root:$homeDirectory:/bin/sh")
        } else {
            listOf(
                "root:x:0:0:root:/root:/bin/sh",
                "${systemInfo.userName}:x:${systemInfo.userId}:${systemInfo.groupId}:${systemInfo.userName}:$homeDirectory:/bin/sh"
            )
        }

        Files.write(path, lines)

        return path
    }

    private fun createGroupFile(): Path {
        val path = createTempFile("group")
        val rootGroup = "root:x:0:root"

        val lines = if (systemInfo.groupId == 0) {
            listOf(rootGroup)
        } else {
            listOf(
                rootGroup,
                "${systemInfo.groupName}:x:${systemInfo.groupId}:${systemInfo.userName}"
            )
        }

        Files.write(path, lines)

        return path
    }

    private fun createHomeDirectory(): Path {
        val path = Files.createTempDirectory(temporaryDirectory, "batect-home-")

        val attributeView = Files.getFileAttributeView(path, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
        val lookupService = fileSystem.userPrincipalLookupService

        attributeView.setOwner(lookupService.lookupPrincipalByName(systemInfo.userName))
        attributeView.setGroup(lookupService.lookupPrincipalByGroupName(systemInfo.groupName))

        return path
    }

    private fun createTempFile(name: String): Path = Files.createTempFile(temporaryDirectory, "batect-$name-", "")

    private fun createMissingVolumeMountDirectories(container: Container) {
        container.volumeMounts.forEach { volumeMount ->
            val path = fileSystem.getPath(volumeMount.localPath)

            if (!Files.exists(path)) {
                Files.createDirectories(path)
            }
        }
    }
}
