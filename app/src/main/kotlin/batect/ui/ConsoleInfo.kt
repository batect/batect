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

package batect.ui

import batect.logging.Logger
import batect.os.OperatingSystem
import batect.os.SystemInfo
import batect.os.data
import jnr.posix.POSIX
import java.io.FileDescriptor

class ConsoleInfo(
    private val posix: POSIX,
    private val systemInfo: SystemInfo,
    private val environment: Map<String, String>,
    private val logger: Logger
) {
    constructor(posix: POSIX, systemInfo: SystemInfo, logger: Logger) : this(posix, systemInfo, System.getenv(), logger)

    val stdinIsTTY: Boolean by lazy {
        val result = posix.isatty(FileDescriptor.`in`)

        logger.info {
            message("Called 'isatty' to determine if STDIN is a TTY.")
            data("result", result)
        }

        result
    }

    val stdoutIsTTY: Boolean by lazy {
        val result = posix.isatty(FileDescriptor.`out`)

        logger.info {
            message("Called 'isatty' to determine if STDOUT is a TTY.")
            data("result", result)
        }

        result
    }

    val supportsInteractivity: Boolean by lazy {
        logger.info {
            message("Checking if terminal supports interactivity.")
            data("stdoutIsTTY", stdoutIsTTY)
            data("terminalType", terminalType)
            data("isTravis", isTravis)
            data("operatingSystem", systemInfo.operatingSystem)
        }

        stdoutIsTTY && !isTravis && terminalType != "dumb" && (systemInfo.operatingSystem == OperatingSystem.Windows || terminalType != null)
    }

    val terminalType: String? = environment["TERM"]
    private val isTravis: Boolean = environment["TRAVIS"] == "true"
}
