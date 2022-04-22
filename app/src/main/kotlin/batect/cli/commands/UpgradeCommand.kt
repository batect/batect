/*
    Copyright 2017-2022 Charles Korn.

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
import batect.logging.data
import batect.os.HostEnvironmentVariables
import batect.telemetry.TelemetrySessionBuilder
import batect.telemetry.addUnhandledExceptionEvent
import batect.ui.Console
import batect.ui.text.Text
import batect.updates.ScriptInfo
import batect.updates.UpdateInfo
import batect.updates.UpdateInfoDownloadException
import batect.updates.UpdateInfoDownloader
import batect.updates.data
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
    private val environmentVariables: HostEnvironmentVariables,
    private val telemetrySessionBuilder: TelemetrySessionBuilder,
    private val logger: Logger
) : Command {
    override fun run(): Int {
        val wrapperScriptDir = environmentVariables["BATECT_WRAPPER_SCRIPT_DIR"]

        if (wrapperScriptDir == null) {
            logger.error {
                message("Batect was started without using the wrapper script and so cannot upgrade it.")
            }

            errorConsole.println(Text.red("Batect was started without using the wrapper script and so cannot upgrade it."))
            return -1
        }

        val updateInfo = tryToDownloadLatestVersionInfo()

        if (updateInfo == null) {
            return -1
        }

        if (versionInfo.version >= updateInfo.version) {
            logger.info {
                message("Current version of Batect is already up to date.")
                data("currentVersion", versionInfo.version)
                data("latestVersion", updateInfo.version)
            }

            console.println("The current version of Batect (${versionInfo.version}) is already up to date.")
            return 0
        }

        console.println("Current version is ${versionInfo.version}, latest version is ${updateInfo.version}.")

        if (updateInfo.scripts.isEmpty()) {
            errorConsole.println(Text.red("A newer version of Batect (${updateInfo.version}) is available, but the upgrade cannot be performed automatically."))
            errorConsole.println(Text.red("Visit ${updateInfo.url} for more information."))
            return -1
        }

        updateInfo.scripts.forEach { script ->
            val newScriptContents = downloadNewScript(script)

            if (newScriptContents == null) {
                return -1
            }

            val wrapperScriptPath = fileSystem.getPath(wrapperScriptDir, script.name)
            writeNewScript(script.name, wrapperScriptPath, newScriptContents)
        }

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
            telemetrySessionBuilder.addUnhandledExceptionEvent(e, isUserFacing = true)

            errorConsole.println(Text.red("Downloading update information failed: ${e.message}"))
            return null
        }
    }

    private fun downloadNewScript(script: ScriptInfo): ByteArray? {
        console.println("Downloading latest version of the '${script.name}' wrapper script...")

        val request = Request.Builder()
            .url(script.downloadUrl)
            .build()

        try {
            logger.info {
                message("Downloading wrapper script.")
                data("url", request.url)
            }

            httpClient.newCall(request).execute().use { response ->
                logger.info {
                    message("Finished downloading wrapper script.")
                    data("successful", response.isSuccessful)
                    data("httpResponseCode", response.code)
                    data("httpResponseMessage", response.message)
                }

                if (!response.isSuccessful) {
                    throw UpdateInfoDownloadException("The server returned HTTP ${response.code}.")
                }

                return response.body!!.bytes()
            }
        } catch (e: Throwable) {
            logger.error {
                message("Downloading wrapper script failed with an exception.")
                data("url", request.url)
                exception(e)
            }

            telemetrySessionBuilder.addUnhandledExceptionEvent(e, isUserFacing = true)

            errorConsole.println(Text.red("Download failed. Could not download ${script.downloadUrl}: ${e.message}"))
            return null
        }
    }

    private fun writeNewScript(name: String, path: Path, content: ByteArray) {
        if (Files.exists(path)) {
            console.println("Replacing existing '$name' wrapper script...")
        } else {
            console.println("Creating new '$name' wrapper script...")
        }

        Files.write(path, content)
    }
}
