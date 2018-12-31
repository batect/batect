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

package batect.cli.commands

import batect.VersionInfo
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.mockGet
import batect.ui.Console
import batect.ui.text.Text
import batect.updates.UpdateInfo
import batect.updates.UpdateInfoDownloadException
import batect.updates.UpdateInfoDownloader
import batect.utils.Version
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.Call
import okhttp3.OkHttpClient
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.IOException
import java.nio.file.Files
import java.time.ZonedDateTime

object UpgradeCommandSpec : Spek({
    describe("an upgrade command") {
        val httpClient by createForEachTest { mock<OkHttpClient>() }
        val console by createForEachTest { mock<Console>() }
        val errorConsole by createForEachTest { mock<Console>() }

        val logger by createLoggerForEachTest()

        given("the application was launched without using the wrapper script") {
            val updateInfoDownloader = mock<UpdateInfoDownloader>()
            val versionInfo = mock<VersionInfo>()
            val fileSystem = Jimfs.newFileSystem(Configuration.unix())
            val environmentVariables = emptyMap<String, String>()

            on("running the command") {
                val command = UpgradeCommand(updateInfoDownloader, versionInfo, httpClient, fileSystem, console, errorConsole, logger, environmentVariables)
                val exitCode = command.run()

                it("returns a non-zero exit code") {
                    assertThat(exitCode, !equalTo(0))
                }

                it("prints an appropriate error message") {
                    verify(errorConsole).println(Text.red("batect was started without using the wrapper script and so cannot upgrade it."))
                }
            }
        }

        given("the application was launched using the wrapper script") {
            val wrapperScriptLocation = "/wrapper.sh"
            val environmentVariables = mapOf(
                "BATECT_WRAPPER_SCRIPT_PATH" to wrapperScriptLocation
            )

            given("downloading the update information succeeds") {
                given("the update information does includes a script download URL") {
                    val fileSystem by createForEachTest {
                        val fileSystem = Jimfs.newFileSystem(Configuration.unix())
                        Files.write(fileSystem.getPath(wrapperScriptLocation), listOf("This is the existing wrapper script content"))

                        fileSystem
                    }

                    val wrapperScriptPath by createForEachTest { fileSystem.getPath(wrapperScriptLocation) }

                    val scriptDownloadUrl = "https://batect.com/script-download"
                    val updateInfoDownloader = mock<UpdateInfoDownloader> {
                        on { getLatestVersionInfo() } doReturn UpdateInfo(Version(1, 2, 3), "https://batect.com/release-notes/1.2.2", ZonedDateTime.now(), scriptDownloadUrl)
                    }

                    given("the update information indicates that there is a newer version available") {
                        val versionInfo = mock<VersionInfo> {
                            on { version } doReturn Version(1, 1, 1)
                        }

                        given("downloading the new wrapper script succeeds") {
                            beforeEachTest {
                                val scriptContent = "This is the new wrapper script content"
                                httpClient.mockGet(scriptDownloadUrl, scriptContent)
                            }

                            on("running the command") {
                                val command = UpgradeCommand(updateInfoDownloader, versionInfo, httpClient, fileSystem, console, errorConsole, logger, environmentVariables)
                                val exitCode = command.run()

                                it("returns a zero exit code") {
                                    assertThat(exitCode, equalTo(0))
                                }

                                it("prints status information to the console as it performs the upgrade") {
                                    inOrder(updateInfoDownloader, httpClient, console) {
                                        verify(console).println("Downloading latest update information...")
                                        verify(updateInfoDownloader).getLatestVersionInfo()
                                        verify(console).println("Current version is 1.1.1, latest version is 1.2.3.")
                                        verify(console).println("Downloading latest version of the wrapper script...")
                                        verify(httpClient).newCall(any())
                                        verify(console).println("Replacing existing wrapper script...")
                                        verify(console).println("Upgrade complete! You can read more about this version at: https://batect.com/release-notes/1.2.2")
                                    }
                                }

                                it("does not print anything to the error console") {
                                    verifyZeroInteractions(errorConsole)
                                }

                                it("overwrites the content of the existing wrapper script with the new file") {
                                    assertThat(Files.readAllLines(wrapperScriptPath), equalTo(listOf("This is the new wrapper script content")))
                                }
                            }
                        }

                        given("downloading the new wrapper script fails with an HTTP error") {
                            beforeEachTest {
                                val scriptContent = "This is the new wrapper script content"
                                httpClient.mockGet(scriptDownloadUrl, scriptContent, 404)
                            }

                            on("running the command") {
                                val command = UpgradeCommand(updateInfoDownloader, versionInfo, httpClient, fileSystem, console, errorConsole, logger, environmentVariables)
                                val exitCode = command.run()

                                it("returns a non-zero exit code") {
                                    assertThat(exitCode, !equalTo(0))
                                }

                                it("prints status information to the console as it performs the upgrade, including an error message when the download fails") {
                                    inOrder(updateInfoDownloader, httpClient, console, errorConsole) {
                                        verify(console).println("Downloading latest update information...")
                                        verify(updateInfoDownloader).getLatestVersionInfo()
                                        verify(console).println("Current version is 1.1.1, latest version is 1.2.3.")
                                        verify(console).println("Downloading latest version of the wrapper script...")
                                        verify(httpClient).newCall(any())
                                        verify(errorConsole).println(Text.red("Download failed. Could not download https://batect.com/script-download: The server returned HTTP 404."))
                                    }
                                }

                                it("does not overwrite the content of the existing wrapper script") {
                                    assertThat(Files.readAllLines(wrapperScriptPath), equalTo(listOf("This is the existing wrapper script content")))
                                }
                            }
                        }

                        given("downloading the new wrapper script fails with an exception") {
                            beforeEachTest {
                                val exception = IOException("Could not do what you asked because stuff happened.")

                                whenever(httpClient.newCall(any())).then {
                                    mock<Call> {
                                        on { execute() } doThrow exception
                                    }
                                }
                            }

                            on("running the command") {
                                val command = UpgradeCommand(updateInfoDownloader, versionInfo, httpClient, fileSystem, console, errorConsole, logger, environmentVariables)
                                val exitCode = command.run()

                                it("returns a non-zero exit code") {
                                    assertThat(exitCode, !equalTo(0))
                                }

                                it("prints status information to the console as it performs the upgrade, including an error message when the download fails") {
                                    inOrder(updateInfoDownloader, httpClient, console, errorConsole) {
                                        verify(console).println("Downloading latest update information...")
                                        verify(updateInfoDownloader).getLatestVersionInfo()
                                        verify(console).println("Current version is 1.1.1, latest version is 1.2.3.")
                                        verify(console).println("Downloading latest version of the wrapper script...")
                                        verify(httpClient).newCall(any())
                                        verify(errorConsole).println(Text.red("Download failed. Could not download https://batect.com/script-download: Could not do what you asked because stuff happened."))
                                    }
                                }

                                it("does not overwrite the content of the existing wrapper script") {
                                    assertThat(Files.readAllLines(wrapperScriptPath), equalTo(listOf("This is the existing wrapper script content")))
                                }
                            }
                        }
                    }

                    given("the current version matches the latest available version") {
                        val versionInfo = mock<VersionInfo> {
                            on { version } doReturn Version(1, 2, 3)
                        }

                        on("running the command") {
                            val command = UpgradeCommand(updateInfoDownloader, versionInfo, httpClient, fileSystem, console, errorConsole, logger, environmentVariables)
                            val exitCode = command.run()

                            it("returns a zero exit code") {
                                assertThat(exitCode, equalTo(0))
                            }

                            it("prints status information to the console as it performs the upgrade, including a message indicating that batect is already up to date") {
                                inOrder(updateInfoDownloader, console) {
                                    verify(console).println("Downloading latest update information...")
                                    verify(updateInfoDownloader).getLatestVersionInfo()
                                    verify(console).println("The current version of batect (1.2.3) is already up to date.")
                                }
                            }
                        }
                    }

                    given("the current version is newer than the available version") {
                        val versionInfo = mock<VersionInfo> {
                            on { version } doReturn Version(1, 2, 4)
                        }

                        on("running the command") {
                            val command = UpgradeCommand(updateInfoDownloader, versionInfo, httpClient, fileSystem, console, errorConsole, logger, environmentVariables)
                            val exitCode = command.run()

                            it("returns a zero exit code") {
                                assertThat(exitCode, equalTo(0))
                            }

                            it("prints status information to the console as it performs the upgrade, including a message indicating that batect is already up to date") {
                                inOrder(updateInfoDownloader, console) {
                                    verify(console).println("Downloading latest update information...")
                                    verify(updateInfoDownloader).getLatestVersionInfo()
                                    verify(console).println("The current version of batect (1.2.4) is already up to date.")
                                }
                            }
                        }
                    }
                }

                given("the update information does not include a script download URL") {
                    val fileSystem = Jimfs.newFileSystem(Configuration.unix())

                    val updateInfoDownloader = mock<UpdateInfoDownloader> {
                        on { getLatestVersionInfo() } doReturn UpdateInfo(Version(1, 2, 3), "https://batect.com/release-notes/1.2.2", ZonedDateTime.now(), null)
                    }

                    given("the update information indicates that there is a newer version available") {
                        val versionInfo = mock<VersionInfo> {
                            on { version } doReturn Version(1, 2, 1)
                        }

                        on("running the command") {
                            val command = UpgradeCommand(updateInfoDownloader, versionInfo, httpClient, fileSystem, console, errorConsole, logger, environmentVariables)
                            val exitCode = command.run()

                            it("returns a non-zero exit code") {
                                assertThat(exitCode, !equalTo(0))
                            }

                            it("prints status information to the console as it performs the upgrade, including a message indicating that the upgrade must be performed manually") {
                                inOrder(updateInfoDownloader, console, errorConsole) {
                                    verify(console).println("Downloading latest update information...")
                                    verify(updateInfoDownloader).getLatestVersionInfo()
                                    verify(console).println("Current version is 1.2.1, latest version is 1.2.3.")
                                    verify(errorConsole).println(Text.red("A newer version of batect (1.2.3) is available, but the upgrade cannot be performed automatically."))
                                    verify(errorConsole).println(Text.red("Visit https://batect.com/release-notes/1.2.2 for more information."))
                                }
                            }
                        }
                    }

                    given("the current version matches the latest available version") {
                        val versionInfo = mock<VersionInfo> {
                            on { version } doReturn Version(1, 2, 3)
                        }

                        on("running the command") {
                            val command = UpgradeCommand(updateInfoDownloader, versionInfo, httpClient, fileSystem, console, errorConsole, logger, environmentVariables)
                            val exitCode = command.run()

                            it("returns a zero exit code") {
                                assertThat(exitCode, equalTo(0))
                            }

                            it("prints status information to the console as it performs the upgrade, including a message indicating that batect is already up to date") {
                                inOrder(updateInfoDownloader, console) {
                                    verify(console).println("Downloading latest update information...")
                                    verify(updateInfoDownloader).getLatestVersionInfo()
                                    verify(console).println("The current version of batect (1.2.3) is already up to date.")
                                }
                            }
                        }
                    }

                    given("the current version is newer than the available version") {
                        val versionInfo = mock<VersionInfo> {
                            on { version } doReturn Version(1, 2, 4)
                        }

                        on("running the command") {
                            val command = UpgradeCommand(updateInfoDownloader, versionInfo, httpClient, fileSystem, console, errorConsole, logger, environmentVariables)
                            val exitCode = command.run()

                            it("returns a zero exit code") {
                                assertThat(exitCode, equalTo(0))
                            }

                            it("prints status information to the console as it performs the upgrade, including a message indicating that batect is already up to date") {
                                inOrder(updateInfoDownloader, console) {
                                    verify(console).println("Downloading latest update information...")
                                    verify(updateInfoDownloader).getLatestVersionInfo()
                                    verify(console).println("The current version of batect (1.2.4) is already up to date.")
                                }
                            }
                        }
                    }
                }
            }

            given("downloading the update information fails") {
                val updateInfoDownloader = mock<UpdateInfoDownloader> {
                    on { getLatestVersionInfo() } doThrow UpdateInfoDownloadException("Something went wrong.")
                }

                val fileSystem = Jimfs.newFileSystem(Configuration.unix())
                val versionInfo = mock<VersionInfo>()

                on("running the command") {
                    val command = UpgradeCommand(updateInfoDownloader, versionInfo, httpClient, fileSystem, console, errorConsole, logger, environmentVariables)
                    val exitCode = command.run()

                    it("returns a non-zero exit code") {
                        assertThat(exitCode, !equalTo(0))
                    }

                    it("prints an appropriate error message") {
                        verify(errorConsole).println(Text.red("Downloading update information failed: Something went wrong."))
                    }
                }
            }
        }
    }
})
