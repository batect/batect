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

import batect.cli.CommandLineOptions
import batect.cli.CommandLineOptionsParser
import batect.docker.client.DockerConnectivityCheckResult
import batect.docker.client.SystemInfoClient
import batect.dockerclient.BuilderVersion
import batect.ioc.DockerConfigurationKodeinFactory
import batect.primitives.Version
import batect.primitives.VersionComparisonMode
import batect.telemetry.DockerTelemetryCollector
import batect.ui.Console
import batect.ui.text.Text
import org.kodein.di.DirectDI
import org.kodein.di.instance

class DockerConnectivity(
    private val dockerConfigurationKodeinFactory: DockerConfigurationKodeinFactory,
    private val dockerSystemInfoClient: SystemInfoClient,
    private val errorConsole: Console,
    private val commandLineOptions: CommandLineOptions
) {
    fun checkAndRun(task: TaskWithKodein): Int {
        return when (val connectivityCheckResult = dockerSystemInfoClient.checkConnectivity()) {
            is DockerConnectivityCheckResult.Succeeded -> handleSuccessfulConnectivityCheck(connectivityCheckResult, task)
            is DockerConnectivityCheckResult.Failed -> handleFailedConnectivityCheck(connectivityCheckResult)
        }
    }

    private fun handleSuccessfulConnectivityCheck(connectivityCheckResult: DockerConnectivityCheckResult.Succeeded, task: TaskWithKodein): Int {
        val builderVersion = when (commandLineOptions.enableBuildKit) {
            null -> when (connectivityCheckResult.builderVersion) {
                batect.docker.api.BuilderVersion.BuildKit -> BuilderVersion.BuildKit
                batect.docker.api.BuilderVersion.Legacy -> BuilderVersion.Legacy
            }
            false -> BuilderVersion.Legacy
            true -> {
                if (connectivityCheckResult.dockerVersion.compareTo(minimumDockerVersionWithBuildKitSupport, VersionComparisonMode.DockerStyle) < 0) {
                    return error("BuildKit has been enabled with --${CommandLineOptionsParser.enableBuildKitFlagName} or the ${CommandLineOptionsParser.enableBuildKitEnvironmentVariableName} environment variable, but the current version of Docker does not support BuildKit, even with experimental features enabled.")
                }

                if (connectivityCheckResult.dockerVersion.compareTo(minimumDockerVersionWithNonExperimentalBuildKitSupport, VersionComparisonMode.DockerStyle) < 0 && !connectivityCheckResult.experimentalFeaturesEnabled) {
                    return error("BuildKit has been enabled with --${CommandLineOptionsParser.enableBuildKitFlagName} or the ${CommandLineOptionsParser.enableBuildKitEnvironmentVariableName} environment variable, but the current version of Docker requires experimental features to be enabled to use BuildKit and experimental features are currently disabled.")
                }

                BuilderVersion.BuildKit
            }
        }

        val kodein = dockerConfigurationKodeinFactory.create(connectivityCheckResult.containerType, builderVersion)
        kodein.instance<DockerTelemetryCollector>().collectTelemetry(connectivityCheckResult, builderVersion)

        return task(kodein)
    }

    private fun handleFailedConnectivityCheck(connectivityCheckResult: DockerConnectivityCheckResult.Failed): Int {
        return error("Docker is not installed, not running or not compatible with Batect: ${connectivityCheckResult.message}")
    }

    private fun error(message: String): Int {
        errorConsole.println(Text.red(message))
        return -1
    }

    companion object {
        private val minimumDockerVersionWithBuildKitSupport = Version(17, 7, 0)
        private val minimumDockerVersionWithNonExperimentalBuildKitSupport = Version(18, 9, 0)
    }
}

typealias TaskWithKodein = (DirectDI) -> Int
