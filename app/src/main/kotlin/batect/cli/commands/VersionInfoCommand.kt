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
import batect.cli.CommandLineOptionsParser
import batect.dockerclient.DockerClient
import batect.git.GitClient
import batect.logging.Logger
import batect.os.SystemInfo
import batect.updates.UpdateNotifier
import kotlinx.coroutines.runBlocking
import java.io.PrintStream

class VersionInfoCommand(
    private val versionInfo: VersionInfo,
    private val outputStream: PrintStream,
    private val systemInfo: SystemInfo,
    private val dockerClient: DockerClient,
    private val gitClient: GitClient,
    private val updateNotifier: UpdateNotifier,
    private val logger: Logger
) : Command {
    override fun run(): Int {
        outputStream.println("Batect version:    ${versionInfo.version}")
        outputStream.println("Built:             ${versionInfo.buildDate}")
        outputStream.println("Built from commit: ${versionInfo.gitCommitHash} (commit date: ${versionInfo.gitCommitDate})")
        outputStream.println("JVM version:       ${systemInfo.jvmVersion}")
        outputStream.println("OS version:        ${systemInfo.osSummary}")
        outputStream.println("Docker version:    ${getDockerVersionInfo()}")
        outputStream.println("Git version:       ${gitClient.version}")
        outputStream.println()
        outputStream.println(CommandLineOptionsParser.helpBlurb)
        outputStream.println()

        updateNotifier.run()

        return 0
    }

    private fun getDockerVersionInfo(): String = runBlocking {
        try {
            val versionInfo = dockerClient.getDaemonVersionInformation()

            "${versionInfo.version} (API version: ${versionInfo.apiVersion}, minimum supported API version: ${versionInfo.minAPIVersion}, commit: ${versionInfo.gitCommit}, operating system: '${versionInfo.operatingSystem}', experimental: ${versionInfo.experimental})"
        } catch (e: Throwable) {
            logger.warn {
                message("Could not get Docker version information")
                exception(e)
            }

            "(could not get Docker version information because ${e.javaClass.simpleName} was thrown: ${e.message})"
        }
    }
}
