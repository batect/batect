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

package batect.os

import batect.testutils.createForEachTest
import batect.testutils.on
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import jnr.posix.Group
import jnr.posix.POSIX
import jnr.posix.Passwd
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.Properties

object SystemInfoSpec : Spek({
    describe("a system info provider") {
        val userInfo = mock<Passwd> {
            on { loginName } doReturn "awesome-user"
        }

        val groupInfo = mock<Group> {
            on { name } doReturn "awesome-group"
        }

        val posix = mock<POSIX> {
            on { geteuid() } doReturn 123
            on { getpwuid(123) } doReturn userInfo
            on { getegid() } doReturn 777
            on { getgrgid(777) } doReturn groupInfo
        }

        val systemProperties by createForEachTest {
            val properties = Properties()

            properties.setProperty("java.vm.vendor", "Awesome JVMs, Inc.")
            properties.setProperty("java.vm.name", "Best JVM Ever")
            properties.setProperty("java.version", "1.2.3")
            properties.setProperty("os.name", "Best OS Ever")
            properties.setProperty("os.arch", "x86")
            properties.setProperty("os.version", "4.5.6")
            properties.setProperty("user.home", "/some/home/dir")

            properties
        }

        on("getting the JVM version") {
            val jvmVersion by runForEachTest { SystemInfo(posix, systemProperties).jvmVersion }

            it("returns a formatted string containing the details of the JVM") {
                assertThat(jvmVersion, equalTo("Awesome JVMs, Inc. Best JVM Ever 1.2.3"))
            }
        }

        on("getting the OS version") {
            val osVersion by runForEachTest { SystemInfo(posix, systemProperties).osVersion }

            it("returns a formatted string containing the details of the OS") {
                assertThat(osVersion, equalTo("Best OS Ever 4.5.6 (x86)"))
            }
        }

        describe("getting the operating system and whether that OS is supported") {
            on("when running on OS X") {
                beforeEachTest { systemProperties.setProperty("os.name", "Mac OS X") }

                val systemInfo by runForEachTest { SystemInfo(posix, systemProperties) }

                it("returns that the operating system is Mac OS X") {
                    assertThat(systemInfo.operatingSystem, equalTo(OperatingSystem.Mac))
                }

                it("returns that the operating system is supported") {
                    assertThat(systemInfo.isSupportedOperatingSystem, equalTo(true))
                }
            }

            on("when running on Linux") {
                beforeEachTest { systemProperties.setProperty("os.name", "Linux") }

                val systemInfo by runForEachTest { SystemInfo(posix, systemProperties) }

                it("returns that the operating system is Linux") {
                    assertThat(systemInfo.operatingSystem, equalTo(OperatingSystem.Linux))
                }

                it("returns that the operating system is supported") {
                    assertThat(systemInfo.isSupportedOperatingSystem, equalTo(true))
                }
            }

            on("when running on Windows") {
                beforeEachTest { systemProperties.setProperty("os.name", "Windows 10") }

                val systemInfo by runForEachTest { SystemInfo(posix, systemProperties) }

                it("returns that the operating system is Windows") {
                    assertThat(systemInfo.operatingSystem, equalTo(OperatingSystem.Windows))
                }

                it("returns that the operating system is not supported") {
                    assertThat(systemInfo.isSupportedOperatingSystem, equalTo(false))
                }
            }

            on("when running on another operating system") {
                beforeEachTest { systemProperties.setProperty("os.name", "Something else") }

                val systemInfo by runForEachTest { SystemInfo(posix, systemProperties) }

                it("returns that the operating system is unknown") {
                    assertThat(systemInfo.operatingSystem, equalTo(OperatingSystem.Other))
                }

                it("returns that the operating system is not supported") {
                    assertThat(systemInfo.isSupportedOperatingSystem, equalTo(false))
                }
            }
        }

        on("getting the home directory") {
            val homeDir by runForEachTest { SystemInfo(posix, systemProperties).homeDirectory }

            it("returns the user's home directory") {
                assertThat(homeDir, equalTo("/some/home/dir"))
            }
        }

        on("getting the current user ID") {
            val userID by runForEachTest { SystemInfo(posix, systemProperties).userId }

            it("returns the ID given by the `id -u` command") {
                assertThat(userID, equalTo(123))
            }
        }

        on("getting the current user name") {
            val userName by runForEachTest { SystemInfo(posix, systemProperties).userName }

            it("returns the ID given by the `id -un` command") {
                assertThat(userName, equalTo("awesome-user"))
            }
        }

        on("getting the current group ID") {
            val groupID by runForEachTest { SystemInfo(posix, systemProperties).groupId }

            it("returns the ID given by the `id -g` command") {
                assertThat(groupID, equalTo(777))
            }
        }

        on("getting the current group name") {
            val groupName by runForEachTest { SystemInfo(posix, systemProperties).groupName }

            it("returns the ID given by the `id -gn` command") {
                assertThat(groupName, equalTo("awesome-group"))
            }
        }
    }
})
