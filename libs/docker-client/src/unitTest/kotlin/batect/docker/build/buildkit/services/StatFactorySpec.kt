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

import batect.testutils.createForGroup
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.isEmptyMap
import batect.testutils.onlyOn
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isEmptyString
import jnr.ffi.Platform
import jnr.posix.POSIXFactory
import okio.ByteString.Companion.encodeUtf8
import okio.utf8Size
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit

object StatFactorySpec : Spek({
    describe("a Stat factory") {
        val posix = POSIXFactory.getNativePOSIX()
        val factory by createForGroup { StatFactory.create(posix) }

        onlyOn(Platform.OS.WINDOWS) {
            given("the application is running on Windows") {
                given("an ordinary file") {
                    val filePath by createForGroup { Files.createTempFile("batect-${StatFactorySpec::class.simpleName!!}", "-test-file") }
                    val lastModifiedTime = 1620798740000123000L
                    val fileContent = "Some file content"

                    beforeGroup {
                        filePath.toFile().deleteOnExit()

                        Files.write(filePath, fileContent.toByteArray(Charsets.UTF_8))
                        Files.setLastModifiedTime(filePath, FileTime.from(lastModifiedTime, TimeUnit.NANOSECONDS))
                    }

                    val stat by createForGroup { factory.createStat(filePath, "the-test-file") }

                    it("returns the relative path provided") {
                        assertThat(stat.path, equalTo("the-test-file"))
                    }

                    it("returns the Golang platform-independent mode of the file, with the permission bits set to rwxr-xr-x") {
                        assertThat(stat.mode, equalTo(octal("755")))
                    }

                    it("returns root as the owner of the file") {
                        assertThat(stat.uid, equalTo(0))
                    }

                    it("returns root as the group of the file") {
                        assertThat(stat.gid, equalTo(0))
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

                    it("returns no extended attributes") {
                        assertThat(stat.xattrs, isEmptyMap())
                    }
                }

                given("a symlink to an ordinary file in the same directory") {
                    val linkPath by createForGroup { Files.createTempFile("batect-${StatFactorySpec::class.simpleName!!}", "-test-link") }
                    val targetName by createForGroup { "${linkPath.fileName}-target" }
                    val targetPath by createForGroup { linkPath.resolveSibling(targetName) }
                    val linkLastModifiedTime = 1620798740000123000L
                    val targetLastModifiedTime = 1000798749999123000L
                    val fileContent = "Some file content"

                    beforeGroup {
                        targetPath.toFile().deleteOnExit()
                        linkPath.toFile().deleteOnExit()

                        Files.write(targetPath, fileContent.toByteArray(Charsets.UTF_8))
                        Files.setLastModifiedTime(targetPath, FileTime.from(targetLastModifiedTime, TimeUnit.NANOSECONDS))

                        Files.delete(linkPath)
                        Files.createSymbolicLink(linkPath, targetPath)
                        Files.setLastModifiedTime(linkPath, FileTime.from(linkLastModifiedTime, TimeUnit.NANOSECONDS))
                    }

                    val stat by createForGroup { factory.createStat(linkPath, "the-test-link") }

                    it("returns the relative path provided") {
                        assertThat(stat.path, equalTo("the-test-link"))
                    }

                    it("returns the Golang platform-independent mode of the symlink, with the permission bits set to rwxr-xr-x") {
                        assertThat(stat.mode, equalTo(octal("1000000755")))
                    }

                    it("returns root as the owner of the file") {
                        assertThat(stat.uid, equalTo(0))
                    }

                    it("returns root as the group of the file") {
                        assertThat(stat.gid, equalTo(0))
                    }

                    it("returns the size of the symlink, not the file content") {
                        val linkSize = Files.readAttributes(linkPath, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).size()

                        assertThat(stat.size, equalTo(linkSize))
                    }

                    it("returns the last modified time of the symlink, not the file") {
                        assertThat(stat.modTime, equalTo(linkLastModifiedTime))
                    }

                    it("returns the relative path of the target of the symlink") {
                        assertThat(stat.linkname, equalTo(targetName))
                    }

                    it("returns empty major and minor device numbers") {
                        assertThat(stat.devmajor, equalTo(0))
                        assertThat(stat.devminor, equalTo(0))
                    }

                    it("returns no extended attributes") {
                        assertThat(stat.xattrs, isEmptyMap())
                    }
                }

                given("a symlink to an ordinary file in a different directory") {
                    val parentDirectory by createForGroup { Files.createTempDirectory("batect-${StatFactorySpec::class.simpleName!!}-test-dir") }
                    val linkDirectory by createForGroup { Files.createDirectory(parentDirectory.resolve("link-dir")) }
                    val targetDirectory by createForGroup { Files.createDirectory(parentDirectory.resolve("target-dir")) }
                    val linkPath by createForGroup { linkDirectory.resolve("the-link") }
                    val targetPath by createForGroup { targetDirectory.resolve("the-target") }
                    val linkLastModifiedTime = 1620798740000123000L
                    val targetLastModifiedTime = 1000798749999123000L
                    val fileContent = "Some file content"

                    beforeGroup {
                        parentDirectory.toFile().deleteOnExit()
                        linkDirectory.toFile().deleteOnExit()
                        targetDirectory.toFile().deleteOnExit()
                        targetPath.toFile().deleteOnExit()
                        linkPath.toFile().deleteOnExit()

                        Files.write(targetPath, fileContent.toByteArray(Charsets.UTF_8))
                        Files.setLastModifiedTime(targetPath, FileTime.from(targetLastModifiedTime, TimeUnit.NANOSECONDS))

                        Files.createSymbolicLink(linkPath, targetPath)
                        Files.setLastModifiedTime(linkPath, FileTime.from(linkLastModifiedTime, TimeUnit.NANOSECONDS))
                    }

                    val stat by createForGroup { factory.createStat(linkPath, "the-test-link") }

                    it("returns the relative path of the target of the symlink") {
                        assertThat(stat.linkname, equalTo("..\\target-dir\\the-target"))
                    }
                }

                given("a directory") {
                    val directoryPath by createForGroup { Files.createTempDirectory("batect-${StatFactorySpec::class.simpleName!!}-test-directory") }
                    val lastModifiedTime = 1620798740000123000L

                    beforeGroup {
                        directoryPath.toFile().deleteOnExit()
                        Files.setLastModifiedTime(directoryPath, FileTime.from(lastModifiedTime, TimeUnit.NANOSECONDS))
                    }

                    val stat by createForGroup { factory.createStat(directoryPath, "the-test-directory") }

                    it("returns the relative path provided") {
                        assertThat(stat.path, equalTo("the-test-directory"))
                    }

                    it("returns the Golang platform-independent mode of the directory, with the permission bits set to rwxr-xr-x") {
                        assertThat(stat.mode, equalTo(octal("20000000755")))
                    }

                    it("returns root as the owner of the file") {
                        assertThat(stat.uid, equalTo(0))
                    }

                    it("returns root as the group of the file") {
                        assertThat(stat.gid, equalTo(0))
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

                    it("returns no extended attributes") {
                        assertThat(stat.xattrs, isEmptyMap())
                    }
                }

                given("a symlink to a directory") {
                    val linkPath by createForGroup { Files.createTempFile("batect-${StatFactorySpec::class.simpleName!!}", "-test-link") }
                    val targetName by createForGroup { "${linkPath.fileName}-target" }
                    val targetPath by createForGroup { Files.createDirectory(linkPath.resolveSibling(targetName)) }
                    val linkLastModifiedTime = 1620798740000123000L
                    val directoryLastModifiedTime = 1000798749999123000L

                    beforeGroup {
                        targetPath.toFile().deleteOnExit()
                        linkPath.toFile().deleteOnExit()

                        Files.setLastModifiedTime(targetPath, FileTime.from(directoryLastModifiedTime, TimeUnit.NANOSECONDS))

                        Files.delete(linkPath)
                        Files.createSymbolicLink(linkPath, targetPath)
                        Files.setLastModifiedTime(linkPath, FileTime.from(linkLastModifiedTime, TimeUnit.NANOSECONDS))
                    }

                    val stat by createForGroup { factory.createStat(linkPath, "the-test-directory") }

                    it("returns the relative path provided") {
                        assertThat(stat.path, equalTo("the-test-directory"))
                    }

                    it("returns the Golang platform-independent mode of the symlink, with the permission bits set to rwxr-xr-x") {
                        assertThat(stat.mode, equalTo(octal("1000000755")))
                    }

                    it("returns root as the owner of the file") {
                        assertThat(stat.uid, equalTo(0))
                    }

                    it("returns root as the group of the file") {
                        assertThat(stat.gid, equalTo(0))
                    }

                    it("returns the size of the symlink, not the file content") {
                        val linkSize = Files.readAttributes(linkPath, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).size()

                        assertThat(stat.size, equalTo(linkSize))
                    }

                    it("returns the last modified time of the symlink, not the file") {
                        assertThat(stat.modTime, equalTo(linkLastModifiedTime))
                    }

                    it("returns the relative path to the target of the symlink") {
                        assertThat(stat.linkname, equalTo(targetName))
                    }

                    it("returns empty major and minor device numbers") {
                        assertThat(stat.devmajor, equalTo(0))
                        assertThat(stat.devminor, equalTo(0))
                    }

                    it("returns no extended attributes") {
                        assertThat(stat.xattrs, isEmptyMap())
                    }
                }
            }
        }

        onlyOn(setOf(Platform.OS.DARWIN, Platform.OS.LINUX)) {
            given("the application is not running on Windows") {
                val currentUserId = posix.geteuid()
                val currentGroupId = posix.getegid()

                // See https://bugs.launchpad.net/ubuntu/+source/linux/+bug/919896/comments/3 for an explanation of this.
                val extendedAttributesSupportedOnSymlinks = Platform.getNativePlatform().os == Platform.OS.DARWIN

                given("an ordinary file") {
                    val filePath by createForGroup { Files.createTempFile("batect-${StatFactorySpec::class.simpleName!!}", "-test-file") }
                    val lastModifiedTime = 1620798740000123000L
                    val fileContent = "Some file content"

                    beforeGroup {
                        filePath.toFile().deleteOnExit()

                        Files.write(filePath, fileContent.toByteArray(Charsets.UTF_8))
                        runCommand("chmod", "0751", filePath.toString())
                        setExtendedAttribute(filePath, "attribute-1", "attribute-1-value")
                        setExtendedAttribute(filePath, "attribute-2", "attribute-2-value")
                        Files.setLastModifiedTime(filePath, FileTime.from(lastModifiedTime, TimeUnit.NANOSECONDS))
                    }

                    val stat by createForGroup { factory.createStat(filePath, "the-test-file") }

                    it("returns the relative path provided") {
                        assertThat(stat.path, equalTo("the-test-file"))
                    }

                    it("returns the Golang platform-independent mode of the file") {
                        assertThat(stat.mode, equalTo(octal("751")))
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
                        setExtendedAttribute(filePath, "target-attribute-1", "target-attribute-1-value")
                        setExtendedAttribute(filePath, "target-attribute-2", "target-attribute-2-value")
                        Files.setLastModifiedTime(filePath, FileTime.from(fileLastModifiedTime, TimeUnit.NANOSECONDS))

                        Files.delete(linkPath)
                        Files.createSymbolicLink(linkPath, filePath)
                        runCommand("chmod", "0755", linkPath.toString())

                        if (extendedAttributesSupportedOnSymlinks) {
                            setExtendedAttribute(linkPath, "link-attribute-1", "link-attribute-1-value")
                            setExtendedAttribute(linkPath, "link-attribute-2", "link-attribute-2-value")
                        }

                        Files.setLastModifiedTime(linkPath, FileTime.from(linkLastModifiedTime, TimeUnit.NANOSECONDS))
                    }

                    val stat by createForGroup { factory.createStat(linkPath, "the-test-link") }

                    it("returns the relative path provided") {
                        assertThat(stat.path, equalTo("the-test-link"))
                    }

                    it("returns the Golang platform-independent mode of the symlink") {
                        val expectedMode = when (Platform.getNativePlatform().os) {
                            Platform.OS.DARWIN -> octal("1000000755")
                            Platform.OS.LINUX -> octal("1000000777") // Symlinks are always 0777 on Linux
                            else -> throw UnsupportedOperationException()
                        }

                        assertThat(stat.mode, equalTo(expectedMode))
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
                        val expectedAttributes = if (extendedAttributesSupportedOnSymlinks) {
                            mapOf(
                                "link-attribute-1" to "link-attribute-1-value".encodeUtf8(),
                                "link-attribute-2" to "link-attribute-2-value".encodeUtf8()
                            )
                        } else {
                            emptyMap()
                        }

                        assertThat(stat.xattrs, equalTo(expectedAttributes))
                    }
                }

                given("a directory") {
                    val directoryPath by createForGroup { Files.createTempDirectory("batect-${StatFactorySpec::class.simpleName!!}-test-directory") }
                    val lastModifiedTime = 1620798740000123000L

                    beforeGroup {
                        directoryPath.toFile().deleteOnExit()

                        runCommand("chmod", "0751", directoryPath.toString())
                        setExtendedAttribute(directoryPath, "attribute-1", "attribute-1-value")
                        setExtendedAttribute(directoryPath, "attribute-2", "attribute-2-value")
                        Files.setLastModifiedTime(directoryPath, FileTime.from(lastModifiedTime, TimeUnit.NANOSECONDS))
                    }

                    val stat by createForGroup { factory.createStat(directoryPath, "the-test-directory") }

                    it("returns the relative path provided") {
                        assertThat(stat.path, equalTo("the-test-directory"))
                    }

                    it("returns the Golang platform-independent mode of the directory") {
                        assertThat(stat.mode, equalTo(octal("20000000751")))
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
                        setExtendedAttribute(directoryPath, "target-attribute-1", "target-attribute-1-value")
                        setExtendedAttribute(directoryPath, "target-attribute-2", "target-attribute-2-value")
                        Files.setLastModifiedTime(directoryPath, FileTime.from(directoryLastModifiedTime, TimeUnit.NANOSECONDS))

                        Files.delete(linkPath)
                        Files.createSymbolicLink(linkPath, directoryPath)
                        runCommand("chmod", "0755", linkPath.toString())

                        if (extendedAttributesSupportedOnSymlinks) {
                            setExtendedAttribute(linkPath, "link-attribute-1", "link-attribute-1-value")
                            setExtendedAttribute(linkPath, "link-attribute-2", "link-attribute-2-value")
                        }

                        Files.setLastModifiedTime(linkPath, FileTime.from(linkLastModifiedTime, TimeUnit.NANOSECONDS))
                    }

                    val stat by createForGroup { factory.createStat(linkPath, "the-test-directory") }

                    it("returns the relative path provided") {
                        assertThat(stat.path, equalTo("the-test-directory"))
                    }

                    it("returns the Golang platform-independent mode of the symlink") {
                        val expectedMode = when (Platform.getNativePlatform().os) {
                            Platform.OS.DARWIN -> octal("1000000755")
                            Platform.OS.LINUX -> octal("1000000777") // Symlinks are always 0777 on Linux
                            else -> throw UnsupportedOperationException()
                        }

                        assertThat(stat.mode, equalTo(expectedMode))
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
                        val expectedAttributes = if (extendedAttributesSupportedOnSymlinks) {
                            mapOf(
                                "link-attribute-1" to "link-attribute-1-value".encodeUtf8(),
                                "link-attribute-2" to "link-attribute-2-value".encodeUtf8()
                            )
                        } else {
                            emptyMap()
                        }

                        assertThat(stat.xattrs, equalTo(expectedAttributes))
                    }
                }
            }
        }
    }
})

private fun octal(value: String): Int = value.toUInt(8).toInt()

private fun runCommand(vararg args: String) {
    val process = ProcessBuilder(args.toList())
        .redirectErrorStream(true)
        .start()

    val output = InputStreamReader(process.inputStream).readText()
    val exitCode = process.waitFor()

    if (exitCode != 0) {
        throw RuntimeException("Command ${args.joinToString(" ")} failed with exit code $exitCode and output $output")
    }
}

private fun setExtendedAttribute(path: Path, name: String, value: String) = when (Platform.getNativePlatform().os) {
    Platform.OS.DARWIN -> runCommand("xattr", "-w", "-s", name, value, path.toString())
    Platform.OS.LINUX -> runCommand("attr", "-s", name, "-V", value, path.toString())
    else -> throw UnsupportedOperationException()
}
