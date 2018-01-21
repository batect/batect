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

package batect.model

import batect.config.Container
import batect.config.RunAsCurrentUserConfig
import batect.config.VolumeMount
import batect.docker.UserAndGroup
import batect.model.events.TaskEventSink
import batect.model.events.TemporaryFileCreatedEvent
import batect.os.SystemInfo
import batect.testutils.createForEachTest
import batect.testutils.imageSourceDoesNotMatter
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.anyElement
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.isEmpty
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
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object RunAsCurrentUserConfigurationProviderSpec : Spek({
    describe("a 'run as current user' configuration provider") {
        val eventSink by createForEachTest { mock<TaskEventSink>() }

        given("the container has 'run as current user' disabled") {
            val container = Container(
                "some-container",
                imageSourceDoesNotMatter(),
                runAsCurrentUserConfig = RunAsCurrentUserConfig(enabled = false)
            )

            val systemInfo = mock<SystemInfo>()
            val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo) }

            on("generating the configuration") {
                val configuration = provider.generateConfiguration(container, eventSink)

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
        }

        given("the container has 'run as current user' enabled") {
            val container = Container(
                "some-container",
                imageSourceDoesNotMatter(),
                runAsCurrentUserConfig = RunAsCurrentUserConfig(enabled = true, homeDirectory = "/home/some-user")
            )

            given("the current user is not root") {
                val systemInfo = mock<SystemInfo> {
                    on { userId } doReturn 123
                    on { userName } doReturn "the-user"
                    on { groupId } doReturn 456
                    on { groupName } doReturn "the-user's-group"
                }

                val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo) }

                on("generating the configuration") {
                    val configuration = provider.generateConfiguration(container, eventSink)

                    configuration.volumeMounts.forEach {
                        File(it.localPath).deleteOnExit()
                    }

                    it("returns a set of volume mounts for the passwd and group file") {
                        assertThat(configuration.volumeMounts.mapToSet { it.containerPath }, equalTo(setOf(
                            "/etc/passwd",
                            "/etc/group"
                        )))
                    }

                    it("returns a set of volume mounts with the passwd file mounted read-only") {
                        assertThat(configuration.volumeMounts, hasReadOnlyVolumeMount("/etc/passwd"))
                    }

                    it("returns a set of volume mounts with the group file mounted read-only") {
                        assertThat(configuration.volumeMounts, hasReadOnlyVolumeMount("/etc/group"))
                    }

                    it("creates a passwd file with both root and the current user") {
                        val passwdFilePath = localPathToPasswdFile(configuration.volumeMounts)
                        val content = Files.readAllLines(passwdFilePath).joinToString("\n")
                        assertThat(content, equalTo("""
                            |root:x:0:0:root:/root:/bin/sh
                            |the-user:x:123:456:the-user:/home/some-user:/bin/sh
                        """.trimMargin()))
                    }

                    it("creates a group file with group for both root and the current user") {
                        val groupFilePath = localPathToGroupFile(configuration.volumeMounts)
                        val content = Files.readAllLines(groupFilePath).joinToString("\n")
                        assertThat(content, equalTo("""
                            |root:x:0:root
                            |the-user's-group:x:456:the-user
                        """.trimMargin()))
                    }

                    it("emits a 'temporary file created' event for the passwd file") {
                        val passwdFilePath = localPathToPasswdFile(configuration.volumeMounts)
                        verify(eventSink).postEvent(TemporaryFileCreatedEvent(container, passwdFilePath))
                    }

                    it("emits a 'temporary file created' event for the group file") {
                        val groupFilePath = localPathToGroupFile(configuration.volumeMounts)
                        verify(eventSink).postEvent(TemporaryFileCreatedEvent(container, groupFilePath))
                    }

                    it("returns a user and group configuration with the current user's user and group IDs") {
                        assertThat(configuration.userAndGroup, equalTo(UserAndGroup(123, 456)))
                    }
                }
            }

            given("the current user is root") {
                val systemInfo = mock<SystemInfo> {
                    on { userId } doReturn 0
                    on { groupId } doReturn 0
                }

                val provider by createForEachTest { RunAsCurrentUserConfigurationProvider(systemInfo) }

                on("generating the configuration") {
                    val configuration = provider.generateConfiguration(container, eventSink)

                    configuration.volumeMounts.forEach {
                        File(it.localPath).deleteOnExit()
                    }

                    it("returns a set of volume mounts for the passwd and group file") {
                        assertThat(configuration.volumeMounts.mapToSet { it.containerPath }, equalTo(setOf(
                            "/etc/passwd",
                            "/etc/group"
                        )))
                    }

                    it("returns a set of volume mounts with the passwd file mounted read-only") {
                        assertThat(configuration.volumeMounts, hasReadOnlyVolumeMount("/etc/passwd"))
                    }

                    it("returns a set of volume mounts with the group file mounted read-only") {
                        assertThat(configuration.volumeMounts, hasReadOnlyVolumeMount("/etc/group"))
                    }

                    it("creates a passwd file with just root listed") {
                        val passwdFilePath = localPathToPasswdFile(configuration.volumeMounts)
                        val content = Files.readAllLines(passwdFilePath).joinToString("\n")
                        assertThat(content, equalTo("""
                            |root:x:0:0:root:/home/some-user:/bin/sh
                        """.trimMargin()))
                    }

                    it("creates a group file with a group for root") {
                        val groupFilePath = localPathToGroupFile(configuration.volumeMounts)
                        val content = Files.readAllLines(groupFilePath).joinToString("\n")
                        assertThat(content, equalTo("""
                            |root:x:0:root
                        """.trimMargin()))
                    }

                    it("emits a 'temporary file created' event for the passwd file") {
                        val passwdFilePath = localPathToPasswdFile(configuration.volumeMounts)
                        verify(eventSink).postEvent(TemporaryFileCreatedEvent(container, passwdFilePath))
                    }

                    it("emits a 'temporary file created' event for the group file") {
                        val groupFilePath = localPathToGroupFile(configuration.volumeMounts)
                        verify(eventSink).postEvent(TemporaryFileCreatedEvent(container, groupFilePath))
                    }

                    it("returns a user and group configuration with the current user's user and group IDs") {
                        assertThat(configuration.userAndGroup, equalTo(UserAndGroup(0, 0)))
                    }
                }
            }
        }
    }
})

private fun hasReadOnlyVolumeMount(path: String) =
    anyElement(has(VolumeMount::containerPath, equalTo(path)) and has(VolumeMount::options, equalTo("ro")))

private fun localPathToPasswdFile(mounts: Set<VolumeMount>): Path = Paths.get(mounts.single { it.containerPath == "/etc/passwd" }.localPath)
private fun localPathToGroupFile(mounts: Set<VolumeMount>): Path = Paths.get(mounts.single { it.containerPath == "/etc/group" }.localPath)
