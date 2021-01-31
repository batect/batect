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

package batect.cli.commands.completion

import batect.cli.CommandLineOptions
import batect.config.Configuration
import batect.config.Task
import batect.config.TaskMap
import batect.config.includes.SilentGitRepositoryCacheNotificationListener
import batect.config.io.ConfigurationLoadResult
import batect.config.io.ConfigurationLoader
import batect.os.HostEnvironmentVariables
import batect.telemetry.AttributeValue
import batect.telemetry.TelemetrySessionBuilder
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.runForEachTest
import batect.testutils.withPlatformSpecificLineSeparator
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files

object GenerateShellTabCompletionTaskInformationCommandSpec : Spek({
    describe("a 'generate shell tab completion task information' command") {
        val config = Configuration(
            "the-project",
            TaskMap(
                Task("first-task", null),
                Task("second-task", null),
                Task("third-task", null),
                Task("fourth-task", null),
            )
        )

        val fileSystem by createForEachTest { Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix()) }
        val configurationFileName by createForEachTest { fileSystem.getPath("/my-project/batect.yml") }
        val includedFile1 by createForEachTest { fileSystem.getPath("/some/other/file.yml") }
        val includedFile2 by createForEachTest { fileSystem.getPath("/another/file.yml") }

        val commandLineOptions by createForEachTest { CommandLineOptions(configurationFileName = configurationFileName, generateShellTabCompletionTaskInformation = Shell.Fish) }
        val configurationLoader by createForEachTest {
            mock<ConfigurationLoader> {
                on { loadConfig(configurationFileName, SilentGitRepositoryCacheNotificationListener) } doReturn ConfigurationLoadResult(
                    config,
                    setOf(
                        configurationFileName,
                        includedFile1,
                        includedFile2,
                    )
                )
            }
        }

        val output by createForEachTest { ByteArrayOutputStream() }
        val telemetrySessionBuilder by createForEachTest { mock<TelemetrySessionBuilder>() }
        val hostEnvironmentVariables = HostEnvironmentVariables("BATECT_COMPLETION_PROXY_VERSION" to "4.5.6")
        val command by createForEachTest { GenerateShellTabCompletionTaskInformationCommand(commandLineOptions, PrintStream(output), configurationLoader, telemetrySessionBuilder, hostEnvironmentVariables) }

        beforeEachTest {
            Files.createDirectories(configurationFileName.parent)
            Files.createDirectories(includedFile1.parent)
            Files.createDirectories(includedFile2.parent)

            Files.write(configurationFileName, "abc123".toByteArray(Charsets.UTF_8))
            Files.write(includedFile1, "def456".toByteArray(Charsets.UTF_8))
            Files.write(includedFile2, "ghi789".toByteArray(Charsets.UTF_8))
        }

        describe("when run") {
            val exitCode by runForEachTest { command.run() }

            it("returns a zero exit code") {
                assertThat(exitCode, equalTo(0))
            }

            // You can generate these hashes yourself with something like:
            // echo -n '<file content>' | sha256sum
            it("prints all loaded paths and all tasks in the project, both in alphabetical order, and with the SHA-256 hash of each file before each file's path") {
                assertThat(
                    output.toString(),
                    equalTo(
                        """
                        |### FILES ###
                        |5eb9480d37ff7490f465d5fad3b425fe4a4c65b46fd4eef160cb1b644df7f1e8  /another/file.yml
                        |6ca13d52ca70c883e0f0bb101e425a89e8624de51db2d2392593af6a84118090  /my-project/batect.yml
                        |8f61ad5cfa0c471c8cbf810ea285cb1e5f9c2c5e5e5e4f58a3229667703e1587  /some/other/file.yml
                        |### TASKS ###
                        |first-task
                        |fourth-task
                        |second-task
                        |third-task
                        |
                        """.trimMargin().withPlatformSpecificLineSeparator()
                    )
                )
            }

            it("records an event in telemetry with the shell name and proxy completion script version") {
                verify(telemetrySessionBuilder).addEvent(
                    "GeneratedShellTabCompletionTaskInformation",
                    mapOf(
                        "shell" to AttributeValue("Fish"),
                        "proxyCompletionScriptVersion" to AttributeValue("4.5.6")
                    )
                )
            }
        }
    }
})
