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
import jnr.posix.POSIX
import java.io.FileDescriptor

class ConsoleInfo(
    private val posix: POSIX,
    private val environment: Map<String, String>,
    private val logger: Logger
) {
    constructor(posix: POSIX, logger: Logger) : this(posix, System.getenv(), logger)

    val stdinIsTTY: Boolean by lazy {
        val result = posix.isatty(FileDescriptor.`in`)

        logger.info {
            message("Called 'isatty' to determine if STDIN is a TTY.")
            data("result", result)
        }

        result
    }

    val supportsInteractivity: Boolean by lazy {
        logger.info {
            message("Checking if terminal supports interactivity.")
            data("stdinIsTTY", stdinIsTTY)
            data("terminalType", terminalType)
            data("isTravis", isTravis)
        }

        stdinIsTTY && !isTravis && terminalType != "dumb" && terminalType != null
    }

    val terminalType: String? = environment["TERM"]
    private val isTravis: Boolean = environment["TRAVIS"] == "true"
}
