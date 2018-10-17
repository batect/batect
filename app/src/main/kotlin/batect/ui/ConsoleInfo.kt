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

package batect.ui

import batect.logging.Logger
import batect.os.NativeMethodException
import batect.os.NativeMethods
import batect.os.ProcessRunner
import jnr.constants.platform.Errno
import jnr.posix.POSIX
import java.io.FileDescriptor

class ConsoleInfo(
    private val posix: POSIX,
    private val nativeMethods: NativeMethods,
    private val processRunner: ProcessRunner,
    private val environment: Map<String, String>,
    private val logger: Logger
) {
    constructor(posix: POSIX, nativeMethods: NativeMethods, processRunner: ProcessRunner, logger: Logger) : this(posix, nativeMethods, processRunner, System.getenv(), logger)

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

    val dimensions: Dimensions?
        get() {
            try {
                val result = nativeMethods.getConsoleDimensions()

                logger.info {
                    message("Got console dimensions.")
                    data("result", result)
                }

                return result
            } catch (e: NativeMethodException) {
                if (e.error == Errno.ENOTTY) {
                    return null
                } else {
                    logger.warn {
                        message("Getting console dimensions failed.")
                        exception(e)
                    }

                    throw e
                }
            }
        }

    fun enterRawMode(): AutoCloseable {
        if (!stdinIsTTY) {
            return object : AutoCloseable {
                override fun close() {}
            }
        }

        val existingState = getExistingTerminalState()
        startRawMode()

        return TerminalStateRestorer(existingState, processRunner)
    }

    private fun getExistingTerminalState(): String {
        val output = processRunner.runWithStdinAttached(listOf("stty", "-g"))

        if (output.exitCode != 0) {
            throw RuntimeException("Invoking 'stty -g' failed with exit code ${output.exitCode}: ${output.output.trim()}")
        }

        return output.output.trim()
    }

    private fun startRawMode() {
        val output = processRunner.runWithStdinAttached(listOf("stty", "raw"))

        if (output.exitCode != 0) {
            throw RuntimeException("Invoking 'stty raw' failed with exit code ${output.exitCode}: ${output.output.trim()}")
        }
    }
}

data class Dimensions(val height: Int, val width: Int)

data class TerminalStateRestorer(private val oldState: String, private val processRunner: ProcessRunner) : AutoCloseable {
    override fun close() {
        val output = processRunner.runWithStdinAttached(listOf("stty", oldState))

        if (output.exitCode != 0) {
            throw RuntimeException("Invoking 'stty $oldState' failed with exit code ${output.exitCode}: ${output.output.trim()}")
        }
    }
}
