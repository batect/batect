/*
   Copyright 2017 Charles Korn.

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

import batect.PrintStreamType
import batect.VersionInfo
import batect.cli.CommandLineParser
import batect.docker.DockerClient
import batect.docker.DockerVersionInfoRetrievalFailedException
import batect.os.SystemInfo
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import java.io.PrintStream

class VersionInfoCommandDefinition : CommandDefinition("version", "Display version information for batect.", setOf("--version")) {
    override fun createCommand(kodein: Kodein): Command =
            VersionInfoCommand(kodein.instance(), kodein.instance(PrintStreamType.Output), kodein.instance(), kodein.instance(), kodein.instance())
}

data class VersionInfoCommand(
        private val versionInfo: VersionInfo,
        private val outputStream: PrintStream,
        private val systemInfo: SystemInfo,
        private val dockerClient: DockerClient,
        private val commandLineParser: CommandLineParser
) : Command {
    override fun run(): Int {
        outputStream.println("batect version:    ${versionInfo.version}")
        outputStream.println("Built:             ${versionInfo.buildDate}")
        outputStream.println("Built from commit: ${versionInfo.gitCommitHash} (commit date: ${versionInfo.gitCommitDate})")
        outputStream.println("JVM version:       ${systemInfo.jvmVersion}")
        outputStream.println("OS version:        ${systemInfo.osVersion}")
        outputStream.println("Docker version:    ${getDockerVersion()}")
        outputStream.println()
        outputStream.println(commandLineParser.helpBlurb)
        outputStream.println()

        return 0
    }

    private fun getDockerVersion(): String {
        try {
            return dockerClient.getDockerVersionInfo()
        } catch (e: DockerVersionInfoRetrievalFailedException) {
            return "(${e.message})"
        }
    }
}
