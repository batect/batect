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

package batect.cli

import batect.cli.options.defaultvalues.EnvironmentVariableDefaultValueProviderFactory
import batect.docker.DockerHttpConfigDefaults
import batect.execution.CacheType
import batect.os.HostEnvironmentVariables
import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.os.PathResolverFactory
import batect.os.PathType
import batect.os.SystemInfo
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.ui.OutputStyle
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CommandLineOptionsParserSpec : Spek({
    describe("a command line interface") {
        val fileSystem = Jimfs.newFileSystem(Configuration.unix())

        val pathResolver = mock<PathResolver> {
            on { resolve(any()) } doAnswer { invocation ->
                val originalPath = invocation.getArgument<String>(0)
                val pathType = if (originalPath.contains("dir")) { PathType.Directory } else { PathType.File }
                val resolvedPath = fileSystem.getPath("/resolved", originalPath)

                PathResolutionResult.Resolved(originalPath, resolvedPath, pathType, "resolved to $resolvedPath")
            }
        }

        val pathResolverFactory = mock<PathResolverFactory> {
            on { createResolverForCurrentDirectory() } doReturn pathResolver
        }

        val environmentVariableDefaultValueProviderFactory = EnvironmentVariableDefaultValueProviderFactory(HostEnvironmentVariables())

        val defaultDockerHost = "http://some-docker-host"
        val dockerHttpConfigDefaults = mock<DockerHttpConfigDefaults> {
            on { this.defaultDockerHost } doReturn defaultDockerHost
        }

        val systemInfo = mock<SystemInfo> {
            on { homeDirectory } doReturn fileSystem.getPath("home-dir")
        }

        val defaultCommandLineOptions = CommandLineOptions(
            configVariablesSourceFile = fileSystem.getPath("/resolved/batect.local.yml"),
            dockerHost = defaultDockerHost,
            dockerTlsCACertificatePath = fileSystem.getPath("home-dir", ".docker", "ca.pem"),
            dockerTLSCertificatePath = fileSystem.getPath("home-dir", ".docker", "cert.pem"),
            dockerTLSKeyPath = fileSystem.getPath("home-dir", ".docker", "key.pem")
        )

        given("no arguments") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser(pathResolverFactory, environmentVariableDefaultValueProviderFactory, dockerHttpConfigDefaults, systemInfo).parse(emptyList())

                it("returns an error message") {
                    assertThat(result, equalTo(CommandLineOptionsParsingResult.Failed("No task name provided. (Run './batect --list-tasks' for a list of all tasks in this project, or './batect --help' for help.)")))
                }
            }
        }

        given("a single argument for the task name") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser(pathResolverFactory, environmentVariableDefaultValueProviderFactory, dockerHttpConfigDefaults, systemInfo).parse(listOf("some-task"))

                it("returns a set of options with just the task name populated") {
                    assertThat(result, equalTo(CommandLineOptionsParsingResult.Succeeded(defaultCommandLineOptions.copy(
                        taskName = "some-task"
                    ))))
                }
            }
        }

        given("multiple arguments without a '--' prefix") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser(pathResolverFactory, environmentVariableDefaultValueProviderFactory, dockerHttpConfigDefaults, systemInfo).parse(listOf("some-task", "some-extra-arg"))

                it("returns an error message") {
                    assertThat(result, equalTo(CommandLineOptionsParsingResult.Failed(
                        "Too many arguments provided. The first extra argument is 'some-extra-arg'.\n" +
                            "To pass additional arguments to the task command, prefix them with '--', for example, './batect my-task -- --extra-option-1 --extra-option-2 value'."
                    )))
                }
            }
        }

        given("multiple arguments with a '--' prefix") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser(pathResolverFactory, environmentVariableDefaultValueProviderFactory, dockerHttpConfigDefaults, systemInfo).parse(listOf("some-task", "--", "some-extra-arg"))

                it("returns a set of options with the task name and additional arguments populated") {
                    assertThat(result, equalTo(CommandLineOptionsParsingResult.Succeeded(defaultCommandLineOptions.copy(
                        taskName = "some-task",
                        additionalTaskCommandArguments = listOf("some-extra-arg")
                    ))))
                }
            }
        }

        given("a flag followed by a single argument") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser(pathResolverFactory, environmentVariableDefaultValueProviderFactory, dockerHttpConfigDefaults, systemInfo).parse(listOf("--no-color", "some-task"))

                it("returns a set of options with the task name populated and the flag set") {
                    assertThat(result, equalTo(CommandLineOptionsParsingResult.Succeeded(defaultCommandLineOptions.copy(
                        disableColorOutput = true,
                        taskName = "some-task"
                    ))))
                }
            }
        }

        given("a flag followed by multiple arguments") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser(pathResolverFactory, environmentVariableDefaultValueProviderFactory, dockerHttpConfigDefaults, systemInfo).parse(listOf("--no-color", "some-task", "some-extra-arg"))

                it("returns an error message") {
                    assertThat(result, equalTo(CommandLineOptionsParsingResult.Failed(
                        "Too many arguments provided. The first extra argument is 'some-extra-arg'.\n" +
                            "To pass additional arguments to the task command, prefix them with '--', for example, './batect my-task -- --extra-option-1 --extra-option-2 value'."
                    )))
                }
            }
        }

        given("colour output has been disabled and fancy output mode has been selected") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser(pathResolverFactory, environmentVariableDefaultValueProviderFactory, dockerHttpConfigDefaults, systemInfo).parse(listOf("--no-color", "--output=fancy", "some-task", "some-extra-arg"))

                it("returns an error message") {
                    assertThat(result, equalTo(CommandLineOptionsParsingResult.Failed("Fancy output mode cannot be used when color output has been disabled.")))
                }
            }
        }

        mapOf(
            listOf("--help") to defaultCommandLineOptions.copy(showHelp = true),
            listOf("--help", "some-task") to defaultCommandLineOptions.copy(showHelp = true),
            listOf("--version") to defaultCommandLineOptions.copy(showVersionInfo = true),
            listOf("--version", "some-task") to defaultCommandLineOptions.copy(showVersionInfo = true),
            listOf("--list-tasks") to defaultCommandLineOptions.copy(listTasks = true),
            listOf("-T") to defaultCommandLineOptions.copy(listTasks = true),
            listOf("--list-tasks", "some-task") to defaultCommandLineOptions.copy(listTasks = true),
            listOf("--upgrade") to defaultCommandLineOptions.copy(runUpgrade = true),
            listOf("--upgrade", "some-task") to defaultCommandLineOptions.copy(runUpgrade = true),
            listOf("--clean") to defaultCommandLineOptions.copy(runCleanup = true),
            listOf("--clean", "some-task") to defaultCommandLineOptions.copy(runCleanup = true),
            listOf("-f=somefile.yml", "some-task") to defaultCommandLineOptions.copy(configurationFileName = fileSystem.getPath("/resolved/somefile.yml"), taskName = "some-task"),
            listOf("--config-file=somefile.yml", "some-task") to defaultCommandLineOptions.copy(configurationFileName = fileSystem.getPath("/resolved/somefile.yml"), taskName = "some-task"),
            listOf("--config-vars-file=somefile.yml", "some-task") to defaultCommandLineOptions.copy(configVariablesSourceFile = fileSystem.getPath("/resolved/somefile.yml"), taskName = "some-task"),
            listOf("--log-file=somefile.log", "some-task") to defaultCommandLineOptions.copy(logFileName = fileSystem.getPath("/resolved/somefile.log"), taskName = "some-task"),
            listOf("--output=simple", "some-task") to defaultCommandLineOptions.copy(requestedOutputStyle = OutputStyle.Simple, taskName = "some-task"),
            listOf("--output=quiet", "some-task") to defaultCommandLineOptions.copy(requestedOutputStyle = OutputStyle.Quiet, taskName = "some-task"),
            listOf("--output=fancy", "some-task") to defaultCommandLineOptions.copy(requestedOutputStyle = OutputStyle.Fancy, taskName = "some-task"),
            listOf("--no-color", "some-task") to defaultCommandLineOptions.copy(disableColorOutput = true, taskName = "some-task"),
            listOf("--no-update-notification", "some-task") to defaultCommandLineOptions.copy(disableUpdateNotification = true, taskName = "some-task"),
            listOf("--no-wrapper-cache-cleanup", "some-task") to defaultCommandLineOptions.copy(disableWrapperCacheCleanup = true, taskName = "some-task"),
            listOf("--no-cleanup-after-failure", "some-task") to defaultCommandLineOptions.copy(disableCleanupAfterFailure = true, taskName = "some-task"),
            listOf("--no-cleanup-after-success", "some-task") to defaultCommandLineOptions.copy(disableCleanupAfterSuccess = true, taskName = "some-task"),
            listOf("--no-cleanup", "some-task") to defaultCommandLineOptions.copy(disableCleanupAfterFailure = true, disableCleanupAfterSuccess = true, taskName = "some-task"),
            listOf("--no-proxy-vars", "some-task") to defaultCommandLineOptions.copy(dontPropagateProxyEnvironmentVariables = true, taskName = "some-task"),
            listOf("--config-var", "a=b", "--config-var", "c=d", "some-task") to defaultCommandLineOptions.copy(configVariableOverrides = mapOf("a" to "b", "c" to "d"), taskName = "some-task"),
            listOf("--override-image", "container-1=image-1", "--override-image", "container-2=image-2", "some-task") to defaultCommandLineOptions.copy(imageOverrides = mapOf("container-1" to "image-1", "container-2" to "image-2"), taskName = "some-task"),
            listOf("--docker-host=some-host", "some-task") to defaultCommandLineOptions.copy(dockerHost = "some-host", taskName = "some-task"),
            listOf("--docker-tls", "some-task") to defaultCommandLineOptions.copy(dockerUseTLS = true, taskName = "some-task"),
            listOf("--docker-tls-verify", "some-task") to defaultCommandLineOptions.copy(dockerUseTLS = true, dockerVerifyTLS = true, taskName = "some-task"),
            listOf("--docker-tls-ca-cert=some-ca-cert", "some-task") to defaultCommandLineOptions.copy(dockerTlsCACertificatePath = fileSystem.getPath("/resolved/some-ca-cert"), taskName = "some-task"),
            listOf("--docker-tls-cert=some-cert", "some-task") to defaultCommandLineOptions.copy(dockerTLSCertificatePath = fileSystem.getPath("/resolved/some-cert"), taskName = "some-task"),
            listOf("--docker-tls-key=some-key", "some-task") to defaultCommandLineOptions.copy(dockerTLSKeyPath = fileSystem.getPath("/resolved/some-key"), taskName = "some-task"),
            listOf("--docker-cert-path=some-cert-dir", "some-task") to defaultCommandLineOptions.copy(
                dockerTlsCACertificatePath = fileSystem.getPath("/resolved/some-cert-dir/ca.pem"),
                dockerTLSCertificatePath = fileSystem.getPath("/resolved/some-cert-dir/cert.pem"),
                dockerTLSKeyPath = fileSystem.getPath("/resolved/some-cert-dir/key.pem"),
                taskName = "some-task"
            ),
            listOf("--docker-cert-path=some-cert-dir", "--docker-tls-ca-cert=some-ca-cert", "--docker-tls-cert=some-cert", "--docker-tls-key=some-key", "some-task") to defaultCommandLineOptions.copy(
                dockerTlsCACertificatePath = fileSystem.getPath("/resolved/some-ca-cert"),
                dockerTLSCertificatePath = fileSystem.getPath("/resolved/some-cert"),
                dockerTLSKeyPath = fileSystem.getPath("/resolved/some-key"),
                taskName = "some-task"
            ),
            listOf("--cache-type=volume", "some-task") to defaultCommandLineOptions.copy(cacheType = CacheType.Volume, taskName = "some-task"),
            listOf("--cache-type=directory", "some-task") to defaultCommandLineOptions.copy(cacheType = CacheType.Directory, taskName = "some-task"),
            listOf("--linux-cache-init-image=cache-init:123", "some-task") to defaultCommandLineOptions.copy(linuxCacheInitImageName = "cache-init:123", taskName = "some-task"),
            listOf("--use-network=my-network", "some-task") to defaultCommandLineOptions.copy(existingNetworkToUse = "my-network", taskName = "some-task"),
            listOf("--skip-prerequisites", "some-task") to defaultCommandLineOptions.copy(skipPrerequisites = true, taskName = "some-task"),
            listOf("--permanently-disable-telemetry") to defaultCommandLineOptions.copy(permanentlyDisableTelemetry = true),
            listOf("--permanently-enable-telemetry") to defaultCommandLineOptions.copy(permanentlyEnableTelemetry = true),
            listOf("--disable-telemetry", "some-task") to defaultCommandLineOptions.copy(disableTelemetry = true, taskName = "some-task")
        ).forEach { (args, expectedResult) ->
            given("the arguments $args") {
                on("parsing the command line") {
                    val result = CommandLineOptionsParser(pathResolverFactory, environmentVariableDefaultValueProviderFactory, dockerHttpConfigDefaults, systemInfo).parse(args)

                    it("returns a set of options with the expected options populated") {
                        assertThat(result, equalTo(CommandLineOptionsParsingResult.Succeeded(expectedResult)))
                    }
                }
            }
        }
    }
})
