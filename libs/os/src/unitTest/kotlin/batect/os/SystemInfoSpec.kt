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

package batect.os

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.on
import batect.testutils.runForEachTest
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.jsoftbiz.utils.OS
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.Properties

object SystemInfoSpec : Spek({
    describe("a system info provider") {
        val nativeMethods = mock<NativeMethods> {
            on { getUserName() } doReturn "awesome-user"
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
            properties.setProperty("java.io.tmpdir", "/some/temp/dir")
            properties.setProperty("line.separator", "some-long-line-separator")

            properties
        }

        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }

        val osInfo by createForEachTest {
            mock<OS> {
                on { platformName } doReturn "Bubuntu Linux 1.2.3"
            }
        }

        on("getting the JVM version") {
            val jvmVersion by runForEachTest { SystemInfo(nativeMethods, fileSystem, osInfo, systemProperties).jvmVersion }

            it("returns a formatted string containing the details of the JVM") {
                assertThat(jvmVersion, equalTo("Awesome JVMs, Inc. Best JVM Ever 1.2.3"))
            }
        }

        on("getting operating system information") {
            val systemInfo by runForEachTest { SystemInfo(nativeMethods, fileSystem, osInfo, systemProperties) }

            it("returns the operating system name") {
                assertThat(systemInfo.osName, equalTo("Best OS Ever"))
            }

            it("returns the operating system version") {
                assertThat(systemInfo.osVersion, equalTo("4.5.6"))
            }

            it("returns the operating system architecture") {
                assertThat(systemInfo.osArchitecture, equalTo("x86"))
            }

            it("returns the operating system details") {
                assertThat(systemInfo.osDetails, equalTo("Bubuntu Linux 1.2.3"))
            }
        }

        describe("getting the operating system and whether that OS is supported") {
            on("when running on macOS") {
                beforeEachTest {
                    systemProperties.setProperty("os.name", "Mac OS X")
                    systemProperties.setProperty("java.io.tmpdir", "/var/folders/tf/abc123/T/")
                }

                val systemInfo by runForEachTest { SystemInfo(nativeMethods, fileSystem, osInfo, systemProperties) }

                it("returns that the operating system is Mac") {
                    assertThat(systemInfo.operatingSystem, equalTo(OperatingSystem.Mac))
                }

                it("returns that the operating system is supported") {
                    assertThat(systemInfo.isSupportedOperatingSystem, equalTo(true))
                }

                it("returns that the temporary directory is '/tmp'") {
                    assertThat(systemInfo.tempDirectory, equalTo(fileSystem.getPath("/tmp")))
                }

                it("returns a summary of the operating system without details") {
                    assertThat(systemInfo.osSummary, equalTo("Mac OS X 4.5.6 x86"))
                }
            }

            on("when running on Linux") {
                beforeEachTest {
                    systemProperties.setProperty("os.name", "Linux")
                    systemProperties.setProperty("java.io.tmpdir", "/tmp")
                }

                val systemInfo by runForEachTest { SystemInfo(nativeMethods, fileSystem, osInfo, systemProperties) }

                it("returns that the operating system is Linux") {
                    assertThat(systemInfo.operatingSystem, equalTo(OperatingSystem.Linux))
                }

                it("returns that the operating system is supported") {
                    assertThat(systemInfo.isSupportedOperatingSystem, equalTo(true))
                }

                it("returns that the temporary directory is '/tmp'") {
                    assertThat(systemInfo.tempDirectory, equalTo(fileSystem.getPath("/tmp")))
                }

                it("returns a summary of the operating system with details") {
                    assertThat(systemInfo.osSummary, equalTo("Linux 4.5.6 x86 (Bubuntu Linux 1.2.3)"))
                }
            }

            on("when running on Windows") {
                beforeEachTest {
                    systemProperties.setProperty("os.name", "Windows 10")
                    systemProperties.setProperty("java.io.tmpdir", "C:\\some-temp-dir")
                }

                val systemInfo by runForEachTest { SystemInfo(nativeMethods, fileSystem, osInfo, systemProperties) }

                it("returns that the operating system is Windows") {
                    assertThat(systemInfo.operatingSystem, equalTo(OperatingSystem.Windows))
                }

                it("returns that the operating system is supported") {
                    assertThat(systemInfo.isSupportedOperatingSystem, equalTo(true))
                }

                it("returns that the temporary directory is the value of the 'java.io.tmpdir' system property") {
                    assertThat(systemInfo.tempDirectory, equalTo(fileSystem.getPath("C:\\some-temp-dir")))
                }

                it("returns a summary of the operating system without details") {
                    assertThat(systemInfo.osSummary, equalTo("Windows 10 4.5.6 x86"))
                }
            }

            on("when running on another operating system") {
                beforeEachTest { systemProperties.setProperty("os.name", "Something else") }

                val systemInfo by runForEachTest { SystemInfo(nativeMethods, fileSystem, osInfo, systemProperties) }

                it("returns that the operating system is unknown") {
                    assertThat(systemInfo.operatingSystem, equalTo(OperatingSystem.Other))
                }

                it("returns that the operating system is not supported") {
                    assertThat(systemInfo.isSupportedOperatingSystem, equalTo(false))
                }

                it("returns a summary of the operating system without details") {
                    assertThat(systemInfo.osSummary, equalTo("Something else 4.5.6 x86"))
                }
            }
        }

        on("getting the home directory") {
            val homeDir by runForEachTest { SystemInfo(nativeMethods, fileSystem, osInfo, systemProperties).homeDirectory }

            it("returns the user's home directory") {
                assertThat(homeDir, equalTo(fileSystem.getPath("/some/home/dir")))
            }
        }

        on("getting the current user name") {
            val userName by runForEachTest { SystemInfo(nativeMethods, fileSystem, osInfo, systemProperties).userName }

            it("returns the ID given by the `id -un` command") {
                assertThat(userName, equalTo("awesome-user"))
            }
        }

        on("getting the line separator") {
            val lineSeparator by runForEachTest { SystemInfo(nativeMethods, fileSystem, osInfo, systemProperties).lineSeparator }

            it("returns the system's line separator") {
                assertThat(lineSeparator, equalTo("some-long-line-separator"))
            }
        }

        on("serializing system info") {
            val json by runForEachTest { Json.Default.encodeToJsonElement(SystemInfo.serializer(), SystemInfo(nativeMethods, fileSystem, osInfo, systemProperties)).jsonObject }

            it("only includes the expected fields") {
                assertThat(json.keys, equalTo(setOf("operatingSystem", "jvmVersion", "osName", "osVersion", "osArchitecture", "osDetails", "homeDirectory", "lineSeparator", "tempDirectory", "userName")))
            }

            it("includes the operating system") {
                assertThat(json["operatingSystem"], equalTo(JsonPrimitive("Other")))
            }

            it("includes the JVM version") {
                assertThat(json["jvmVersion"], equalTo(JsonPrimitive("Awesome JVMs, Inc. Best JVM Ever 1.2.3")))
            }

            it("includes the operating system name") {
                assertThat(json["osName"], equalTo(JsonPrimitive("Best OS Ever")))
            }

            it("includes the operating system version") {
                assertThat(json["osVersion"], equalTo(JsonPrimitive("4.5.6")))
            }

            it("includes the operating system architecture") {
                assertThat(json["osArchitecture"], equalTo(JsonPrimitive("x86")))
            }

            it("includes the operating system details") {
                assertThat(json["osDetails"], equalTo(JsonPrimitive("Bubuntu Linux 1.2.3")))
            }

            it("includes the home directory") {
                assertThat(json["homeDirectory"], equalTo(JsonPrimitive("/some/home/dir")))
            }

            it("includes the line separator") {
                assertThat(json["lineSeparator"], equalTo(JsonPrimitive("some-long-line-separator")))
            }

            it("includes the temp directory") {
                assertThat(json["tempDirectory"], equalTo(JsonPrimitive("/some/temp/dir")))
            }

            it("includes the user's user name") {
                assertThat(json["userName"], equalTo(JsonPrimitive("awesome-user")))
            }
        }
    }
})
