/*
   Copyright 2017 Charles Korn.

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
import batect.os.ProcessRunner

class ConsoleInfo(
    private val processRunner: ProcessRunner,
    private val environment: Map<String, String>,
    private val logger: Logger
) {
    constructor(processRunner: ProcessRunner, logger: Logger) : this(processRunner, System.getenv(), logger)

    val stdinIsTTY: Boolean by lazy {
        val result = processRunner.runAndCaptureOutput(listOf("tty"))

        logger.info {
            message("Ran 'tty' to determine if STDIN is a TTY.")
            data("result", result)
        }

        result.exitCode == 0
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
