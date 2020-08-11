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

package batect.telemetry

import batect.cli.commands.Command
import batect.git.GitClient
import batect.git.GitVersionRetrievalResult
import batect.os.ConsoleInfo
import batect.os.HostEnvironmentVariables
import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Properties
import kotlin.reflect.KClass

class TelemetryEnvironmentCollector(
    private val telemetrySessionBuilder: TelemetrySessionBuilder,
    private val hostEnvironmentVariables: HostEnvironmentVariables,
    private val gitClient: GitClient,
    private val consoleInfo: ConsoleInfo,
    private val runtimeMXBean: RuntimeMXBean = ManagementFactory.getRuntimeMXBean(),
    private val systemProperties: Properties = System.getProperties()
) {
    fun collect(commandType: KClass<out Command>) {
        addCommandAttributes(commandType)
        addConsoleAttributes()
        addOSAttributes()
        addJVMAttributes()
        addGitAttributes()
        addWrapperAttributes()
    }

    private fun addCommandAttributes(commandType: KClass<out Command>) {
        telemetrySessionBuilder.addAttribute("commandType", commandType.simpleName!!)
    }

    private fun addConsoleAttributes() {
        telemetrySessionBuilder.addAttribute("terminal", hostEnvironmentVariables["TERM"])
        telemetrySessionBuilder.addAttribute("shell", hostEnvironmentVariables["SHELL"]?.substringAfterLast('/'))
        telemetrySessionBuilder.addAttribute("stdinIsTTY", consoleInfo.stdinIsTTY)
        telemetrySessionBuilder.addAttribute("stdoutIsTTY", consoleInfo.stdoutIsTTY)
        telemetrySessionBuilder.addAttribute("consoleSupportsInteractivity", consoleInfo.supportsInteractivity)
    }

    private fun addOSAttributes() {
        telemetrySessionBuilder.addAttribute("osName", systemProperties.getProperty("os.name"))
        telemetrySessionBuilder.addAttribute("osArchitecture", systemProperties.getProperty("os.arch"))
        telemetrySessionBuilder.addAttribute("osVersion", systemProperties.getProperty("os.version"))
    }

    private fun addJVMAttributes() {
        telemetrySessionBuilder.addAttribute("jvmVendor", systemProperties.getProperty("java.vendor"))
        telemetrySessionBuilder.addAttribute("jvmName", systemProperties.getProperty("java.vm.name"))
        telemetrySessionBuilder.addAttribute("jvmVersion", systemProperties.getProperty("java.version"))

        val jvmStartTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(runtimeMXBean.startTime), ZoneOffset.UTC)
        telemetrySessionBuilder.addAttribute("jvmStartTime", jvmStartTime.toString())
    }

    private fun addGitAttributes() {
        when (val result = gitClient.version) {
            is GitVersionRetrievalResult.Succeeded -> telemetrySessionBuilder.addAttribute("gitVersion", result.version)
            is GitVersionRetrievalResult.Failed -> telemetrySessionBuilder.addNullAttribute("gitVersion")
        }
    }

    private fun addWrapperAttributes() {
        when (hostEnvironmentVariables["BATECT_WRAPPER_DID_DOWNLOAD"]) {
            "true" -> telemetrySessionBuilder.addAttribute("wrapperDidDownload", true)
            "false" -> telemetrySessionBuilder.addAttribute("wrapperDidDownload", false)
            else -> telemetrySessionBuilder.addNullAttribute("wrapperDidDownload")
        }
    }
}
