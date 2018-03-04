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

package batect.cli.commands

import batect.VersionInfo
import batect.logging.Logger
import batect.ui.Console
import batect.ui.ConsoleColor
import batect.updates.UpdateInfo
import batect.updates.UpdateInfoDownloadException
import batect.updates.UpdateInfoDownloader
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path

class UpgradeCommand(
    private val updateInfoDownloader: UpdateInfoDownloader,
    private val versionInfo: VersionInfo,
    private val httpClient: OkHttpClient,
    private val fileSystem: FileSystem,
    private val console: Console,
    private val errorConsole: Console,
    private val logger: Logger,
    private val environmentVariables: Map<String, String>
) : Command {
    constructor(updateInfoDownloader: UpdateInfoDownloader, versionInfo: VersionInfo, httpClient: OkHttpClient, fileSystem: FileSystem, console: Console, errorConsole: Console, logger: Logger) :
        this(updateInfoDownloader, versionInfo, httpClient, fileSystem, console, errorConsole, logger, System.getenv())

    override fun run(): Int {
        val wrapperScriptLocation = environmentVariables["BATECT_WRAPPER_SCRIPT_PATH"]

        if (wrapperScriptLocation == null) {
            logger.info {
                message("batect was started without using the wrapper script and so cannot upgrade it.")
            }

            errorConsole.withColor(ConsoleColor.Red) {
                println("batect was started without using the wrapper script and so cannot upgrade it.")
            }

            return -1
        }

        val updateInfo = tryToDownloadLatestVersionInfo()

        if (updateInfo == null) {
            return -1
        }

        if (versionInfo.version >= updateInfo.version) {
            logger.info {
                message("Current version of batect is already up to date.")
                data("currentVersion", versionInfo.version)
                data("latestVersion", updateInfo.version)
            }

            console.println("The current version of batect (${versionInfo.version}) is already up to date.")
            return 0
        }

        console.println("Current version is ${versionInfo.version}, latest version is ${updateInfo.version}.")

        if (updateInfo.scriptDownloadUrl == null) {
            errorConsole.withColor(ConsoleColor.Red) {
                println("A newer version of batect (${updateInfo.version}) is available, but the upgrade cannot be performed automatically.")
                println("Visit ${updateInfo.url} for more information.")
            }

            return -1
        }

        val newScriptContents = downloadNewScript(updateInfo.scriptDownloadUrl)

        if (newScriptContents == null) {
            return -1
        }

        val wrapperScriptPath = fileSystem.getPath(wrapperScriptLocation)
        writeNewScript(wrapperScriptPath, newScriptContents)

        console.println("Upgrade complete! You can read more about this version at: ${updateInfo.url}")

        return 0
    }

    private fun tryToDownloadLatestVersionInfo(): UpdateInfo? {
        try {
            console.println("Downloading latest update information...")

            val updateInfo = updateInfoDownloader.getLatestVersionInfo()

            logger.info {
                message("Downloaded latest version info.")
                data("updateInfo", updateInfo)
            }

            return updateInfo
        } catch (e: UpdateInfoDownloadException) {
            errorConsole.withColor(ConsoleColor.Red) {
                println("Downloading update information failed: ${e.message}")
            }

            return null
        }
    }

    private fun downloadNewScript(url: String): ByteArray? {
        console.println("Downloading latest version...")

        val request = Request.Builder()
            .url(url)
            .build()

        try {
            logger.info {
                message("Downloading new wrapper script.")
                data("url", request.url().toString())
            }

            httpClient.newCall(request).execute().use { response ->
                logger.info {
                    message("Finished downloading new wrapper script.")
                    data("successful", response.isSuccessful)
                    data("httpResponseCode", response.code())
                    data("httpResponseMessage", response.message())
                }

                if (!response.isSuccessful) {
                    throw UpdateInfoDownloadException("The server returned HTTP ${response.code()}.")
                }

                return response.body()!!.bytes()
            }
        } catch (e: Throwable) {
            logger.error {
                message("Downloading new wrapper script failed with an exception.")
                data("url", request.url().toString())
                exception(e)
            }

            errorConsole.withColor(ConsoleColor.Red) {
                println("Downloading latest version failed. Could not download $url: ${e.message}")
            }

            return null
        }
    }

    private fun writeNewScript(path: Path, content: ByteArray) {
        console.println("Replacing existing version...")
        Files.write(path, content)
    }
}
