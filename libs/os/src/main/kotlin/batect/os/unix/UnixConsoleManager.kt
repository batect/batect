/*
   Copyright 2017-2021 Charles Korn.

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

package batect.os.unix

import batect.logging.Logger
import batect.os.ConsoleInfo
import batect.os.ConsoleManager
import batect.os.ProcessRunner

class UnixConsoleManager(
    private val consoleInfo: ConsoleInfo,
    private val applicationResolver: ApplicationResolver,
    private val processRunner: ProcessRunner,
    private val logger: Logger
) : ConsoleManager {
    override fun enableConsoleEscapeSequences() {
        // Nothing to do on Linux or macOS.
    }

    override fun enterRawMode(): AutoCloseable {
        if (!consoleInfo.stdinIsTTY) {
            logger.info {
                message("Terminal is not a TTY, won't enter raw mode.")
            }

            return AutoCloseable { }
        }

        val existingState = getExistingTerminalState()
        startRawMode()

        return TerminalStateRestorer(applicationResolver.stty, existingState, processRunner)
    }

    private fun getExistingTerminalState(): String {
        val output = processRunner.runWithStdinAttached(listOf(applicationResolver.stty, "-g"))

        if (output.exitCode != 0) {
            throw RuntimeException("Invoking '${applicationResolver.stty} -g' failed with exit code ${output.exitCode}: ${output.output.trim()}")
        }

        return output.output.trim()
    }

    private fun startRawMode() {
        val command = listOf(
            applicationResolver.stty, "-ignbrk", "-brkint", "-parmrk", "-istrip", "-inlcr", "-igncr", "-icrnl", "-ixon", "-opost", "-echo", "-echonl",
            "-icanon", "-isig", "-iexten", "-parenb", "cs8", "min", "1", "time", "0"
        )

        val output = processRunner.runWithStdinAttached(command)

        if (output.exitCode != 0) {
            throw RuntimeException("Invoking '${command.joinToString(" ")}' failed with exit code ${output.exitCode}: ${output.output.trim()}")
        }
    }
}

data class TerminalStateRestorer(private val stty: String, private val oldState: String, private val processRunner: ProcessRunner) : AutoCloseable {
    override fun close() {
        val output = processRunner.runWithStdinAttached(listOf(stty, oldState))

        if (output.exitCode != 0) {
            throw RuntimeException("Invoking '$stty $oldState' failed with exit code ${output.exitCode}: ${output.output.trim()}")
        }
    }
}
