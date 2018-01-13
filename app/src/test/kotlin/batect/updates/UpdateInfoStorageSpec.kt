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

package batect.updates

import batect.os.SystemInfo
import batect.testutils.createForEachTest
import batect.utils.Version
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.nio.file.Files
import java.time.ZoneOffset
import java.time.ZonedDateTime

object UpdateInfoStorageSpec : Spek({
    describe("update information storage") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }

        val homeDir = "/some/home/dir"
        val expectedUpdateInfoDirectory = "$homeDir/.batect/updates"
        val expectedUpdateInfoPath = "$expectedUpdateInfoDirectory/latestVersion"

        val systemInfo = mock<SystemInfo> {
            on { homeDirectory } doReturn homeDir
        }

        val storage by createForEachTest { UpdateInfoStorage(fileSystem, systemInfo) }

        describe("reading update information from disk") {
            on("when no update information has been written to disk") {
                val updateInfo = storage.read()

                it("returns no update information") {
                    assertThat(updateInfo, absent())
                }
            }

            on("when some update information has been written to disk already") {
                Files.createDirectories(fileSystem.getPath(expectedUpdateInfoDirectory))
                Files.write(fileSystem.getPath(expectedUpdateInfoPath), listOf(
                    "version=1.2.3",
                    "url=https://www.something.com/batect-updates/abc",
                    "lastUpdated=2017-10-03T11:31:12Z"
                ))

                val updateInfo = storage.read()

                it("returns the update information from the file") {
                    assertThat(updateInfo!!, has(UpdateInfo::version, equalTo(Version(1, 2, 3))))
                    assertThat(updateInfo, has(UpdateInfo::url, equalTo("https://www.something.com/batect-updates/abc")))
                    assertThat(updateInfo, has(UpdateInfo::lastUpdated, equalTo(ZonedDateTime.of(2017, 10, 3, 11, 31, 12, 0, ZoneOffset.UTC))))
                }
            }
        }

        describe("writing update information to disk") {
            val updateInfo = UpdateInfo(Version(2, 3, 4), "https://www.something.com/batect-updates/abc", ZonedDateTime.of(2017, 10, 3, 11, 31, 12, 0, ZoneOffset.UTC))
            val expectedFileContents = listOf(
                "version=2.3.4",
                "url=https://www.something.com/batect-updates/abc",
                "lastUpdated=2017-10-03T11:31:12Z"
            )

            on("when the update information directory does not exist") {
                storage.write(updateInfo)

                it("writes the update information to disk") {
                    val fileContents = Files.readAllLines(fileSystem.getPath(expectedUpdateInfoPath))

                    assertThat(fileContents, equalTo(expectedFileContents))
                }
            }

            on("when the update information directory does exist but is empty") {
                Files.createDirectories(fileSystem.getPath(expectedUpdateInfoDirectory))
                storage.write(updateInfo)

                it("writes the update information to disk") {
                    val fileContents = Files.readAllLines(fileSystem.getPath(expectedUpdateInfoPath))

                    assertThat(fileContents, equalTo(expectedFileContents))
                }
            }

            on("when the update information file has been written previously") {
                Files.createDirectories(fileSystem.getPath(expectedUpdateInfoDirectory))
                Files.write(fileSystem.getPath(expectedUpdateInfoPath), listOf("Some old file content"))
                storage.write(updateInfo)

                it("overwrites the contents of the file") {
                    val fileContents = Files.readAllLines(fileSystem.getPath(expectedUpdateInfoPath))

                    assertThat(fileContents, equalTo(expectedFileContents))
                }
            }
        }
    }
})
