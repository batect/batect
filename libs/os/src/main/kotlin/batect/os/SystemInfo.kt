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

package batect.os

import batect.logging.LogMessageBuilder
import batect.logging.PathSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jsoftbiz.utils.OS
import java.nio.file.FileSystem
import java.nio.file.Path
import java.util.Properties

@Serializable()
class SystemInfo(
    val operatingSystem: OperatingSystem,
    val osName: String,
    val osVersion: String,
    val osArchitecture: String,
    val osDetails: String,
    val lineSeparator: String,
    val jvmVersion: String,
    val userName: String,
    @Serializable(with = PathSerializer::class) val homeDirectory: Path,
    @Serializable(with = PathSerializer::class) val tempDirectory: Path
) {
    constructor(nativeMethods: NativeMethods, fileSystem: FileSystem, osInfo: OS, systemProperties: Properties = System.getProperties()) : this(
        determineOperatingSystem(systemProperties.getProperty("os.name")),
        systemProperties.getProperty("os.name"),
        systemProperties.getProperty("os.version"),
        systemProperties.getProperty("os.arch"),
        osInfo.platformName,
        systemProperties.getProperty("line.separator"),
        "${systemProperties.getProperty("java.vm.vendor")} ${systemProperties.getProperty("java.vm.name")} ${systemProperties.getProperty("java.version")}",
        nativeMethods.getUserName(),
        fileSystem.getPath(systemProperties.getProperty("user.home")),
        systemProperties,
        fileSystem
    )

    constructor(
        operatingSystem: OperatingSystem,
        osName: String,
        osVersion: String,
        osArchitecture: String,
        osDetails: String,
        lineSeparator: String,
        jvmVersion: String,
        userName: String,
        homeDirectory: Path,
        systemProperties: Properties,
        fileSystem: FileSystem
    ) : this(
        operatingSystem,
        osName,
        osVersion,
        osArchitecture,
        osDetails,
        lineSeparator,
        jvmVersion,
        userName,
        homeDirectory,
        when (operatingSystem) {
            OperatingSystem.Mac, OperatingSystem.Linux -> fileSystem.getPath("/tmp")
            else -> fileSystem.getPath(systemProperties.getProperty("java.io.tmpdir"))
        }
    )

    @Transient
    val osSummary = when (operatingSystem) {
        OperatingSystem.Linux -> "$osName $osVersion $osArchitecture ($osDetails)"
        else -> "$osName $osVersion $osArchitecture"
    }

    @Transient
    val isSupportedOperatingSystem = operatingSystem in setOf(OperatingSystem.Mac, OperatingSystem.Linux, OperatingSystem.Windows)

    companion object {
        private fun determineOperatingSystem(osName: String) = when {
            osName.equals("Mac OS X", ignoreCase = true) -> OperatingSystem.Mac
            osName.equals("Linux", ignoreCase = true) -> OperatingSystem.Linux
            osName.startsWith("Windows", ignoreCase = true) -> OperatingSystem.Windows
            else -> OperatingSystem.Other
        }
    }
}

fun LogMessageBuilder.data(key: String, value: SystemInfo) = this.data(key, value, SystemInfo.serializer())
