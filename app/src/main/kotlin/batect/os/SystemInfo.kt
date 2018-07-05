/*
   Copyright 2017-2018 Charles Korn.

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

class SystemInfo(private val processRunner: ProcessRunner, private val systemProperties: Properties = System.getProperties()) {
    private val jvmVendor = systemProperties.getProperty("java.vm.vendor")
    private val jvmName = systemProperties.getProperty("java.vm.name")
    private val javaVersion = systemProperties.getProperty("java.version")

    private val osName = systemProperties.getProperty("os.name")
    private val osArch = systemProperties.getProperty("os.arch")
    private val rawOSVersion = systemProperties.getProperty("os.version")

    val jvmVersion = "$jvmVendor $jvmName $javaVersion"
    val osVersion = "$osName $rawOSVersion ($osArch)"
    val homeDirectory: String = systemProperties.getProperty("user.home")

    val operatingSystem = when {
        osName.equals("Mac OS X", ignoreCase = true) -> OperatingSystem.Mac
        osName.equals("Linux", ignoreCase = true) -> OperatingSystem.Linux
        else -> OperatingSystem.Other
    }

    val userId: Int by lazy { processRunner.runAndCaptureOutput(listOf("id", "-u")).output.trim().toInt() }
    val userName: String by lazy { processRunner.runAndCaptureOutput(listOf("id", "-un")).output.trim() }
    val groupId: Int by lazy { processRunner.runAndCaptureOutput(listOf("id", "-g")).output.trim().toInt() }
    val groupName: String by lazy { processRunner.runAndCaptureOutput(listOf("id", "-gn")).output.trim() }
}
