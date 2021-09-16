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

package batect.cli.commands

import batect.VersionInfo
import batect.cli.CommandLineOptionsParser
import batect.docker.client.SystemInfoClient
import batect.git.GitClient
import batect.os.SystemInfo
import batect.updates.UpdateNotifier
import java.io.PrintStream

class VersionInfoCommand(
    private val versionInfo: VersionInfo,
    private val outputStream: PrintStream,
    private val systemInfo: SystemInfo,
    private val dockerSystemInfoClient: SystemInfoClient,
    private val gitClient: GitClient,
    private val updateNotifier: UpdateNotifier
) : Command {
    override fun run(): Int {
        outputStream.println("Batect version:    ${versionInfo.version}")
        outputStream.println("Built:             ${versionInfo.buildDate}")
        outputStream.println("Built from commit: ${versionInfo.gitCommitHash} (commit date: ${versionInfo.gitCommitDate})")
        outputStream.println("JVM version:       ${systemInfo.jvmVersion}")
        outputStream.println("OS version:        ${systemInfo.osSummary}")
        outputStream.println("Docker version:    ${dockerSystemInfoClient.getDockerVersionInfo()}")
        outputStream.println("Git version:       ${gitClient.version}")
        outputStream.println()
        outputStream.println(CommandLineOptionsParser.helpBlurb)
        outputStream.println()

        updateNotifier.run()

        return 0
    }
}
