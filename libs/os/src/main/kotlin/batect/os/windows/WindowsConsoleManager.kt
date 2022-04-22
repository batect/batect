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

package batect.os.windows

import batect.logging.Logger
import batect.os.ConsoleInfo
import batect.os.ConsoleManager

class WindowsConsoleManager(
    private val consoleInfo: ConsoleInfo,
    private val nativeMethods: WindowsNativeMethods,
    private val logger: Logger
) : ConsoleManager {
    override fun enableConsoleEscapeSequences() {
        if (!consoleInfo.stdoutIsTTY) {
            logger.info {
                message("STDOUT is not a TTY, won't enable console escape sequences")
            }

            return
        }

        nativeMethods.enableConsoleEscapeSequences()
    }

    override fun enterRawMode(): AutoCloseable {
        if (!consoleInfo.stdinIsTTY) {
            logger.info {
                message("STDIN is not a TTY, won't enter raw mode.")
            }

            return AutoCloseable { }
        }

        val existingState = nativeMethods.enableConsoleRawMode()

        return TerminalStateRestorer(existingState, nativeMethods)
    }
}

data class TerminalStateRestorer(
    private val previousState: Int,
    private val nativeMethods: WindowsNativeMethods
) : AutoCloseable {
    override fun close() = nativeMethods.restoreConsoleMode(previousState)
}
