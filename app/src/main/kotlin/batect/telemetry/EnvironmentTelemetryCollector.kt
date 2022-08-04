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

package batect.telemetry

import batect.cli.CommandLineOptions
import batect.cli.commands.Command
import batect.git.GitClient
import batect.git.GitVersionRetrievalResult
import batect.os.ConsoleInfo
import batect.os.HostEnvironmentVariables
import batect.os.SystemInfo
import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Properties
import kotlin.reflect.KClass

class EnvironmentTelemetryCollector(
    private val telemetryCaptor: TelemetryCaptor,
    private val hostEnvironmentVariables: HostEnvironmentVariables,
    private val gitClient: GitClient,
    private val consoleInfo: ConsoleInfo,
    private val commandLineOptions: CommandLineOptions,
    private val ciEnvironmentDetector: CIEnvironmentDetector,
    private val systemInfo: SystemInfo,
    private val runtimeMXBean: RuntimeMXBean = ManagementFactory.getRuntimeMXBean(),
    private val systemProperties: Properties = System.getProperties()
) {
    fun collect(commandType: KClass<out Command>) {
        addCommandAttributes(commandType)
        addCommandLineOptionAttributes()
        addConsoleAttributes()
        addOSAttributes()
        addJVMAttributes()
        addGitAttributes()
        addWrapperAttributes()
        addCIAttributes()
    }

    private fun addCommandAttributes(commandType: KClass<out Command>) {
        telemetryCaptor.addAttribute("commandType", commandType.simpleName!!)
    }

    private fun addCommandLineOptionAttributes() {
        telemetryCaptor.addAttribute("usingNonDefaultConfigurationFileName", commandLineOptions.configurationFileName.fileName.toString() != "batect.yml")
        telemetryCaptor.addAttribute("usingConfigVariablesFile", commandLineOptions.configVariablesSourceFile != null)
        telemetryCaptor.addAttribute("requestedOutputStyle", commandLineOptions.requestedOutputStyle?.toString()?.lowercase())
        telemetryCaptor.addAttribute("colorOutputDisabled", commandLineOptions.disableColorOutput)
        telemetryCaptor.addAttribute("updateNotificationsDisabled", commandLineOptions.disableUpdateNotification)
        telemetryCaptor.addAttribute("wrapperCacheCleanupDisabled", commandLineOptions.disableWrapperCacheCleanup)
        telemetryCaptor.addAttribute("cleanupAfterSuccessDisabled", commandLineOptions.disableCleanupAfterSuccess)
        telemetryCaptor.addAttribute("cleanupAfterFailureDisabled", commandLineOptions.disableCleanupAfterFailure)
        telemetryCaptor.addAttribute("proxyEnvironmentVariablePropagationDisabled", commandLineOptions.dontPropagateProxyEnvironmentVariables)
        telemetryCaptor.addAttribute("additionalTaskCommandArgumentCount", commandLineOptions.additionalTaskCommandArguments.count())
        telemetryCaptor.addAttribute("commandLineConfigVariableOverrideCount", commandLineOptions.configVariableOverrides.size)
        telemetryCaptor.addAttribute("commandLineImageOverrideCount", commandLineOptions.imageOverrides.size)
        telemetryCaptor.addAttribute("usingTLSForDockerConnection", commandLineOptions.dockerUseTLS)
        telemetryCaptor.addAttribute("verifyingTLSForDockerConnection", commandLineOptions.dockerVerifyTLS)
        telemetryCaptor.addAttribute("usingExistingDockerNetwork", commandLineOptions.existingNetworkToUse != null)
        telemetryCaptor.addAttribute("skippingPrerequisites", commandLineOptions.skipPrerequisites)
        telemetryCaptor.addAttribute("maximumLevelOfParallelism", commandLineOptions.maximumLevelOfParallelism)
    }

    private fun addConsoleAttributes() {
        telemetryCaptor.addAttribute("terminal", hostEnvironmentVariables["TERM"])
        telemetryCaptor.addAttribute("shell", hostEnvironmentVariables["SHELL"]?.substringAfterLast('/'))
        telemetryCaptor.addAttribute("stdinIsTTY", consoleInfo.stdinIsTTY)
        telemetryCaptor.addAttribute("stdoutIsTTY", consoleInfo.stdoutIsTTY)
        telemetryCaptor.addAttribute("consoleSupportsInteractivity", consoleInfo.supportsInteractivity)
    }

    private fun addOSAttributes() {
        telemetryCaptor.addAttribute("osName", systemInfo.osName)
        telemetryCaptor.addAttribute("osArchitecture", systemInfo.osArchitecture)
        telemetryCaptor.addAttribute("osVersion", systemInfo.osVersion)
        telemetryCaptor.addAttribute("osDetails", systemInfo.osDetails)
    }

    private fun addJVMAttributes() {
        telemetryCaptor.addAttribute("jvmVendor", systemProperties.getProperty("java.vendor"))
        telemetryCaptor.addAttribute("jvmName", systemProperties.getProperty("java.vm.name"))
        telemetryCaptor.addAttribute("jvmVersion", systemProperties.getProperty("java.version"))

        val jvmStartTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(runtimeMXBean.startTime), ZoneOffset.UTC)
        telemetryCaptor.addAttribute("jvmStartTime", jvmStartTime.toString())
    }

    private fun addGitAttributes() {
        when (val result = gitClient.version) {
            is GitVersionRetrievalResult.Succeeded -> telemetryCaptor.addAttribute("gitVersion", result.version)
            is GitVersionRetrievalResult.Failed -> telemetryCaptor.addNullAttribute("gitVersion")
        }
    }

    private fun addWrapperAttributes() {
        when (hostEnvironmentVariables["BATECT_WRAPPER_DID_DOWNLOAD"]) {
            "true" -> telemetryCaptor.addAttribute("wrapperDidDownload", true)
            "false" -> telemetryCaptor.addAttribute("wrapperDidDownload", false)
            else -> telemetryCaptor.addNullAttribute("wrapperDidDownload")
        }
    }

    private fun addCIAttributes() {
        val detectionResult = ciEnvironmentDetector.detect()

        telemetryCaptor.addAttribute("suspectRunningOnCI", detectionResult.suspectRunningOnCI)
        telemetryCaptor.addAttribute("suspectedCISystem", detectionResult.suspectedCISystem)
    }
}
