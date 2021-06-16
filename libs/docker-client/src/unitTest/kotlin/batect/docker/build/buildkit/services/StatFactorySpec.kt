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

package batect.docker.build.buildkit.services

import batect.os.OperatingSystem
import batect.os.SystemInfo
import batect.testutils.createForGroup
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.onlyOn
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isEmptyString
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import jnr.ffi.Platform
import jnr.posix.POSIXFactory
import okio.ByteString.Companion.encodeUtf8
import okio.utf8Size
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit

object StatFactorySpec : Spek({
    describe("a Stat factory") {
        val posix = POSIXFactory.getNativePOSIX()

        onlyOn(Platform.OS.WINDOWS) {
            given("the application is running on Windows") {
                val systemInfo by createForGroup {
                    mock<SystemInfo> {
                        on { operatingSystem } doReturn OperatingSystem.Windows
                    }
                }

                val factory by createForGroup { StatFactory(systemInfo, posix) }

                // TODO
            }
        }

        onlyOn(setOf(Platform.OS.DARWIN, Platform.OS.LINUX)) {
            given("the application is not running on Windows") {
                val systemInfo by createForGroup {
                    mock<SystemInfo> {
                        on { operatingSystem } doReturn when (Platform.getNativePlatform().os) {
                            Platform.OS.LINUX -> OperatingSystem.Linux
                            Platform.OS.DARWIN -> OperatingSystem.Mac
                            else -> throw UnsupportedOperationException("Unknown operating system ${Platform.getNativePlatform().os}")
                        }
                    }
                }

                val factory by createForGroup { StatFactory(systemInfo, posix) }

                val currentUserId = posix.geteuid()
                val currentGroupId = posix.getegid()

                given("an ordinary file") {
                    val filePath by createForGroup { Files.createTempFile("batect-${StatFactorySpec::class.simpleName!!}", "-test-file") }
                    val lastModifiedTime = 1620798740000123000L
                    val fileContent = "Some file content"

                    beforeGroup {
                        filePath.toFile().deleteOnExit()

                        Files.write(filePath, fileContent.toByteArray(Charsets.UTF_8))
                        runCommand("chmod", "0751", filePath.toString())
                        runCommand("xattr", "-w", "attribute-1", "attribute-1-value", filePath.toString())
                        runCommand("xattr", "-w", "attribute-2", "attribute-2-value", filePath.toString())
                        Files.setLastModifiedTime(filePath, FileTime.from(lastModifiedTime, TimeUnit.NANOSECONDS))
                    }

                    val stat by createForGroup { factory.createStat(filePath, "the-test-file") }

                    it("returns the relative path provided") {
                        assertThat(stat.path, equalTo("the-test-file"))
                    }

                    it("returns the mode of the file") {
                        assertThat(stat.mode, equalTo(octal("100751")))
                    }

                    it("returns the owner of the file") {
                        assertThat(stat.uid, equalTo(currentUserId))
                    }

                    it("returns the group of the file") {
                        assertThat(stat.gid, equalTo(currentGroupId))
                    }

                    it("returns the size of the file") {
                        assertThat(stat.size, equalTo(fileContent.utf8Size()))
                    }

                    it("returns the last modified time of the file") {
                        assertThat(stat.modTime, equalTo(lastModifiedTime))
                    }

                    it("returns an empty link name") {
                        assertThat(stat.linkname, isEmptyString)
                    }

                    it("returns empty major and minor device numbers") {
                        assertThat(stat.devmajor, equalTo(0))
                        assertThat(stat.devminor, equalTo(0))
                    }

                    it("returns the extended attributes of the file") {
                        assertThat(
                            stat.xattrs,
                            equalTo(
                                mapOf(
                                    "attribute-1" to "attribute-1-value".encodeUtf8(),
                                    "attribute-2" to "attribute-2-value".encodeUtf8()
                                )
                            )
                        )
                    }
                }

                given("a symlink to an ordinary file") {
                    val filePath by createForGroup { Files.createTempFile("batect-${StatFactorySpec::class.simpleName!!}", "-test-link-target") }
                    val linkPath by createForGroup { Files.createTempFile("batect-${StatFactorySpec::class.simpleName!!}", "-test-link") }
                    val linkLastModifiedTime = 1620798740000123000L
                    val fileLastModifiedTime = 1000798749999123000L
                    val fileContent = "Some file content"

                    beforeGroup {
                        filePath.toFile().deleteOnExit()
                        linkPath.toFile().deleteOnExit()

                        Files.write(filePath, fileContent.toByteArray(Charsets.UTF_8))
                        runCommand("chmod", "0711", filePath.toString())
                        runCommand("xattr", "-w", "target-attribute-1", "target-attribute-1-value", filePath.toString())
                        runCommand("xattr", "-w", "target-attribute-2", "target-attribute-2-value", filePath.toString())
                        Files.setLastModifiedTime(filePath, FileTime.from(fileLastModifiedTime, TimeUnit.NANOSECONDS))

                        Files.delete(linkPath)
                        Files.createSymbolicLink(linkPath, filePath)
                        runCommand("chmod", "0755", linkPath.toString())
                        runCommand("xattr", "-w", "-s", "link-attribute-1", "link-attribute-1-value", linkPath.toString())
                        runCommand("xattr", "-w", "-s", "link-attribute-2", "link-attribute-2-value", linkPath.toString())
                        Files.setLastModifiedTime(linkPath, FileTime.from(linkLastModifiedTime, TimeUnit.NANOSECONDS))
                    }

                    val stat by createForGroup { factory.createStat(linkPath, "the-test-link") }

                    it("returns the relative path provided") {
                        assertThat(stat.path, equalTo("the-test-link"))
                    }

                    it("returns the mode of the symlink") {
                        assertThat(stat.mode, equalTo(octal("120755")))
                    }

                    it("returns the owner of the symlink") {
                        assertThat(stat.uid, equalTo(currentUserId))
                    }

                    it("returns the group of the symlink") {
                        assertThat(stat.gid, equalTo(currentGroupId))
                    }

                    it("returns the size of the symlink, not the file content") {
                        val linkSize = Files.readAttributes(linkPath, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).size()

                        assertThat(stat.size, equalTo(linkSize))
                    }

                    it("returns the last modified time of the symlink, not the file") {
                        assertThat(stat.modTime, equalTo(linkLastModifiedTime))
                    }

                    it("returns the target of the symlink") {
                        assertThat(stat.linkname, equalTo(filePath.toString()))
                    }

                    it("returns empty major and minor device numbers") {
                        assertThat(stat.devmajor, equalTo(0))
                        assertThat(stat.devminor, equalTo(0))
                    }

                    it("returns the extended attributes of the symlink, not the file") {
                        assertThat(
                            stat.xattrs,
                            equalTo(
                                mapOf(
                                    "link-attribute-1" to "link-attribute-1-value".encodeUtf8(),
                                    "link-attribute-2" to "link-attribute-2-value".encodeUtf8()
                                )
                            )
                        )
                    }
                }

                given("a directory") {
                    val directoryPath by createForGroup { Files.createTempDirectory("batect-${StatFactorySpec::class.simpleName!!}-test-directory") }
                    val lastModifiedTime = 1620798740000123000L

                    beforeGroup {
                        directoryPath.toFile().deleteOnExit()

                        runCommand("chmod", "0751", directoryPath.toString())
                        runCommand("xattr", "-w", "attribute-1", "attribute-1-value", directoryPath.toString())
                        runCommand("xattr", "-w", "attribute-2", "attribute-2-value", directoryPath.toString())
                        Files.setLastModifiedTime(directoryPath, FileTime.from(lastModifiedTime, TimeUnit.NANOSECONDS))
                    }

                    val stat by createForGroup { factory.createStat(directoryPath, "the-test-directory") }

                    it("returns the relative path provided") {
                        assertThat(stat.path, equalTo("the-test-directory"))
                    }

                    it("returns the mode of the directory") {
                        assertThat(stat.mode, equalTo(octal("40751")))
                    }

                    it("returns the owner of the directory") {
                        assertThat(stat.uid, equalTo(currentUserId))
                    }

                    it("returns the group of the directory") {
                        assertThat(stat.gid, equalTo(currentGroupId))
                    }

                    it("returns the size of the directory") {
                        assertThat(stat.size, equalTo(Files.size(directoryPath)))
                    }

                    it("returns the last modified time of the directory") {
                        assertThat(stat.modTime, equalTo(lastModifiedTime))
                    }

                    it("returns an empty link name") {
                        assertThat(stat.linkname, isEmptyString)
                    }

                    it("returns empty major and minor device numbers") {
                        assertThat(stat.devmajor, equalTo(0))
                        assertThat(stat.devminor, equalTo(0))
                    }

                    it("returns the extended attributes of the directory") {
                        assertThat(
                            stat.xattrs,
                            equalTo(
                                mapOf(
                                    "attribute-1" to "attribute-1-value".encodeUtf8(),
                                    "attribute-2" to "attribute-2-value".encodeUtf8()
                                )
                            )
                        )
                    }
                }

                given("a symlink to a directory") {
                    val directoryPath by createForGroup { Files.createTempDirectory("batect-${StatFactorySpec::class.simpleName!!}-test-directory") }
                    val linkPath by createForGroup { Files.createTempFile("batect-${StatFactorySpec::class.simpleName!!}", "-test-link") }
                    val linkLastModifiedTime = 1620798740000123000L
                    val directoryLastModifiedTime = 1000798749999123000L

                    beforeGroup {
                        directoryPath.toFile().deleteOnExit()
                        linkPath.toFile().deleteOnExit()

                        runCommand("chmod", "0751", directoryPath.toString())
                        runCommand("xattr", "-w", "target-attribute-1", "target-attribute-1-value", directoryPath.toString())
                        runCommand("xattr", "-w", "target-attribute-2", "target-attribute-2-value", directoryPath.toString())
                        Files.setLastModifiedTime(directoryPath, FileTime.from(directoryLastModifiedTime, TimeUnit.NANOSECONDS))

                        Files.delete(linkPath)
                        Files.createSymbolicLink(linkPath, directoryPath)
                        runCommand("chmod", "0755", linkPath.toString())
                        runCommand("xattr", "-w", "-s", "link-attribute-1", "link-attribute-1-value", linkPath.toString())
                        runCommand("xattr", "-w", "-s", "link-attribute-2", "link-attribute-2-value", linkPath.toString())
                        Files.setLastModifiedTime(linkPath, FileTime.from(linkLastModifiedTime, TimeUnit.NANOSECONDS))
                    }

                    val stat by createForGroup { factory.createStat(linkPath, "the-test-directory") }

                    it("returns the relative path provided") {
                        assertThat(stat.path, equalTo("the-test-directory"))
                    }

                    it("returns the mode of the symlink") {
                        assertThat(stat.mode, equalTo(octal("120755")))
                    }

                    it("returns the owner of the symlink") {
                        assertThat(stat.uid, equalTo(currentUserId))
                    }

                    it("returns the group of the symlink") {
                        assertThat(stat.gid, equalTo(currentGroupId))
                    }

                    it("returns the size of the symlink, not the file content") {
                        val linkSize = Files.readAttributes(linkPath, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).size()

                        assertThat(stat.size, equalTo(linkSize))
                    }

                    it("returns the last modified time of the symlink, not the file") {
                        assertThat(stat.modTime, equalTo(linkLastModifiedTime))
                    }

                    it("returns the target of the symlink") {
                        assertThat(stat.linkname, equalTo(directoryPath.toString()))
                    }

                    it("returns empty major and minor device numbers") {
                        assertThat(stat.devmajor, equalTo(0))
                        assertThat(stat.devminor, equalTo(0))
                    }

                    it("returns the extended attributes of the symlink, not the file") {
                        assertThat(
                            stat.xattrs,
                            equalTo(
                                mapOf(
                                    "link-attribute-1" to "link-attribute-1-value".encodeUtf8(),
                                    "link-attribute-2" to "link-attribute-2-value".encodeUtf8()
                                )
                            )
                        )
                    }
                }
            }
        }
    }
})

private fun octal(value: String): Int = value.toInt(8)

private fun runCommand(vararg args: String) {
    val process = ProcessBuilder(args.toList())
        .redirectErrorStream(true)
        .start()

    val output = InputStreamReader(process.inputStream).readText()
    val exitCode = process.waitFor()

    if (exitCode != 0) {
        throw RuntimeException("Command $args failed with exit code $exitCode and output $output")
    }
}
