/*
   Copyright 2017-2019 Charles Korn.

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

import java.util.Properties

class SystemInfo(private val nativeMethods: NativeMethods, private val systemProperties: Properties = System.getProperties()) {
    private val jvmVendor = systemProperties.getProperty("java.vm.vendor")
    private val jvmName = systemProperties.getProperty("java.vm.name")
    private val javaVersion = systemProperties.getProperty("java.version")

    private val osName = systemProperties.getProperty("os.name")
    private val osArch = systemProperties.getProperty("os.arch")
    private val rawOSVersion = systemProperties.getProperty("os.version")

    val operatingSystem = when {
        osName.equals("Mac OS X", ignoreCase = true) -> OperatingSystem.Mac
        osName.equals("Linux", ignoreCase = true) -> OperatingSystem.Linux
        osName.startsWith("Windows", ignoreCase = true) -> OperatingSystem.Windows
        else -> OperatingSystem.Other
    }

    val jvmVersion = "$jvmVendor $jvmName $javaVersion"
    val osVersion = "$osName $rawOSVersion ($osArch)"
    val homeDirectory: String = systemProperties.getProperty("user.home")
    val lineSeparator: String = systemProperties.getProperty("line.separator")

    val tempDirectory: String = if (operatingSystem in setOf(OperatingSystem.Mac, OperatingSystem.Linux)) {
        "/tmp"
    } else {
        systemProperties.getProperty("java.io.tmpdir")
    }

    val isSupportedOperatingSystem = operatingSystem in setOf(OperatingSystem.Mac, OperatingSystem.Linux, OperatingSystem.Windows)

    val userName: String by lazy { nativeMethods.getUserName() }
}
