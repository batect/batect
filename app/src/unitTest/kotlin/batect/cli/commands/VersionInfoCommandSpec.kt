/*
   Copyright 2017-2020 Charles Korn.

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
import batect.docker.client.DockerSystemInfoClient
import batect.docker.client.DockerVersionInfoRetrievalResult
import batect.os.SystemInfo
import batect.testutils.on
import batect.testutils.withPlatformSpecificLineSeparator
import batect.updates.UpdateNotifier
import batect.primitives.Version
import batect.git.GitClient
import batect.git.GitVersionRetrievalResult
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object VersionInfoCommandSpec : Spek({
    describe("a 'version info' command") {
        on("when invoked") {
            val versionInfo = mock<VersionInfo> {
                on { version } doReturn Version(1, 2, 3)
                on { buildDate } doReturn "THE BUILD DATE"
                on { gitCommitHash } doReturn "THE BUILD COMMIT"
                on { gitCommitDate } doReturn "COMMIT DATE"
            }

            val systemInfo = mock<SystemInfo> {
                on { jvmVersion } doReturn "THE JVM VERSION"
                on { osVersion } doReturn "THE OS VERSION"
            }

            val dockerSystemInfoClient = mock<DockerSystemInfoClient> {
                on { getDockerVersionInfo() } doReturn DockerVersionInfoRetrievalResult.Failed("DOCKER VERSION INFO")
            }

            val gitClient = mock<GitClient> {
                on { getVersion() } doReturn GitVersionRetrievalResult.Failed("GIT VERSION INFO")
            }

            val outputStream = ByteArrayOutputStream()
            val updateNotifier = mock<UpdateNotifier>()
            val command = VersionInfoCommand(versionInfo, PrintStream(outputStream), systemInfo, dockerSystemInfoClient, gitClient, updateNotifier)
            val exitCode = command.run()
            val output = outputStream.toString()

            it("prints version information") {
                assertThat(output, equalTo("""
                    |batect version:    1.2.3
                    |Built:             THE BUILD DATE
                    |Built from commit: THE BUILD COMMIT (commit date: COMMIT DATE)
                    |JVM version:       THE JVM VERSION
                    |OS version:        THE OS VERSION
                    |Docker version:    (DOCKER VERSION INFO)
                    |Git version:       (GIT VERSION INFO)
                    |
                    |For documentation and further information on batect, visit https://github.com/batect/batect.
                    |
                    |""".trimMargin().withPlatformSpecificLineSeparator()))
            }

            it("returns a zero exit code") {
                assertThat(exitCode, equalTo(0))
            }

            it("notifies the user of any updates") {
                verify(updateNotifier).run()
            }
        }
    }
})
