/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.cli.commands

import batect.cli.CommandLineOptions
import batect.cli.CommandLineOptionsParser
import batect.docker.DockerClientFactory
import batect.docker.DockerConnectivityCheckResult
import batect.docker.DockerContainerType
import batect.docker.toHumanReadableString
import batect.dockerclient.BuilderVersion
import batect.dockerclient.DockerClient
import batect.dockerclient.DockerClientException
import batect.ioc.DockerConfigurationKodeinFactory
import batect.logging.Logger
import batect.primitives.Version
import batect.primitives.VersionComparisonMode
import batect.telemetry.AttributeValue
import batect.telemetry.DockerTelemetryCollector
import batect.telemetry.TelemetryCaptor
import batect.telemetry.addUnhandledExceptionEvent
import batect.ui.Console
import batect.ui.text.Text
import kotlinx.coroutines.runBlocking
import org.kodein.di.DirectDI
import org.kodein.di.instance

class DockerConnectivity(
    private val dockerConfigurationKodeinFactory: DockerConfigurationKodeinFactory,
    private val dockerClientFactory: DockerClientFactory,
    private val errorConsole: Console,
    private val commandLineOptions: CommandLineOptions,
    private val telemetryCaptor: TelemetryCaptor,
    private val logger: Logger,
) {
    fun checkAndRun(task: TaskWithKodein): Int {
        val client = try {
            createClient()
        } catch (e: Throwable) {
            return handleCreatingClientFailed(e)
        }

        return when (val connectivityCheckResult = checkConnectivity(client)) {
            is DockerConnectivityCheckResult.Succeeded -> handleSuccessfulConnectivityCheck(connectivityCheckResult, client, task)
            is DockerConnectivityCheckResult.Failed -> handleFailedConnectivityCheck(connectivityCheckResult)
        }
    }

    private fun createClient(): DockerClient {
        return dockerClientFactory.create()
    }

    private fun handleCreatingClientFailed(exception: Throwable): Int {
        logger.error {
            message("Could not create Docker client.")
            exception(exception)
        }

        telemetryCaptor.addUnhandledExceptionEvent(exception, isUserFacing = true)

        return error("Could not establish connection to Docker daemon: ${exception.message}")
    }

    private fun handleSuccessfulConnectivityCheck(connectivityCheckResult: DockerConnectivityCheckResult.Succeeded, dockerClient: DockerClient, task: TaskWithKodein): Int {
        val builderVersion = when (commandLineOptions.enableBuildKit) {
            null -> connectivityCheckResult.builderVersion
            false -> BuilderVersion.Legacy
            true -> {
                if (isUntaggedDaemonVersion(connectivityCheckResult.dockerVersion)) {
                    // Assume that untagged Docker daemon versions (such as those seen on GitHub Actions' Windows runners) support BuildKit.
                    // See https://github.com/actions/runner-images/pull/6220.
                    BuilderVersion.BuildKit
                } else {
                    val dockerVersion = Version.parse(connectivityCheckResult.dockerVersion)

                    if (dockerVersion.isBefore(minimumDockerVersionWithBuildKitSupport)) {
                        return error(
                            "BuildKit has been enabled with --${CommandLineOptionsParser.enableBuildKitFlagName} or the ${CommandLineOptionsParser.enableBuildKitEnvironmentVariableName} environment variable, but the current version of Docker does not support BuildKit, even with experimental features enabled.",
                        )
                    }

                    if (dockerVersion.isBefore(minimumDockerVersionWithNonExperimentalBuildKitSupport) && !connectivityCheckResult.experimentalFeaturesEnabled) {
                        return error(
                            "BuildKit has been enabled with --${CommandLineOptionsParser.enableBuildKitFlagName} or the ${CommandLineOptionsParser.enableBuildKitEnvironmentVariableName} environment variable, but the current version of Docker requires experimental features to be enabled to use BuildKit and experimental features are currently disabled.",
                        )
                    }

                    BuilderVersion.BuildKit
                }
            }
        }

        val kodein = dockerConfigurationKodeinFactory.create(dockerClient, connectivityCheckResult.containerType, builderVersion)
        kodein.instance<DockerTelemetryCollector>().collectTelemetry(connectivityCheckResult, builderVersion)

        return task(kodein)
    }

    private fun Version.isBefore(minimumVersion: Version): Boolean {
        return this.compareTo(minimumVersion, VersionComparisonMode.DockerStyle) < 0
    }

    private fun isUntaggedDaemonVersion(version: String): Boolean = untaggedDaemonVersionRegex.matches(version)

    private fun handleFailedConnectivityCheck(connectivityCheckResult: DockerConnectivityCheckResult.Failed): Int {
        return error("Docker is not installed, not running or not compatible with Batect: ${connectivityCheckResult.message}")
    }

    private fun error(message: String): Int {
        errorConsole.println(Text.red(message))
        return -1
    }

    private fun checkConnectivity(dockerClient: DockerClient): DockerConnectivityCheckResult =
        runBlocking {
            try {
                val pingResponse = dockerClient.ping()
                val versionInfo = dockerClient.getDaemonVersionInformation()

                if (Version.parse(versionInfo.apiVersion).compareTo(Version.parse(minimumDockerAPIVersion), VersionComparisonMode.DockerStyle) < 0) {
                    telemetryCaptor.addEvent("IncompatibleDockerVersion", mapOf("dockerVersion" to AttributeValue(versionInfo.version)))

                    return@runBlocking DockerConnectivityCheckResult.Failed("Batect requires Docker $minimumDockerVersion or later, but version ${versionInfo.version} is installed.")
                }

                val osType = DockerContainerType.values().singleOrNull { it.name.equals(versionInfo.operatingSystem, ignoreCase = true) }

                if (osType == null) {
                    return@runBlocking DockerConnectivityCheckResult.Failed("Batect requires Docker to be running in Linux or Windows containers mode.")
                }

                logger.info {
                    message("Created Docker client and successfully connected.")
                    data("dockerVersionInfo", versionInfo.toHumanReadableString())
                }

                DockerConnectivityCheckResult.Succeeded(
                    osType,
                    versionInfo.version,
                    pingResponse.builderVersion,
                    pingResponse.experimental,
                )
            } catch (e: DockerClientException) {
                logger.warn {
                    message("Connectivity check failed.")
                    exception(e)
                }

                telemetryCaptor.addUnhandledExceptionEvent(e, isUserFacing = true)

                DockerConnectivityCheckResult.Failed(e.message!!)
            }
        }

    companion object {
        private val minimumDockerVersionWithBuildKitSupport = Version(17, 7, 0)
        private val minimumDockerVersionWithNonExperimentalBuildKitSupport = Version(18, 9, 0)
        private val untaggedDaemonVersionRegex = """^[^-]+-dockerproject-\d{4}-\d{2}-\d{2}$""".toRegex()

        private const val minimumDockerAPIVersion = "1.37"
        private const val minimumDockerVersion = "18.03.1" // This should be kept in sync with the above API version (see https://docs.docker.com/develop/sdk/#api-version-matrix for table)
    }
}

typealias TaskWithKodein = (DirectDI) -> Int
