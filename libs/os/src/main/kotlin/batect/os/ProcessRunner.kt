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

import batect.logging.Logger
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset

class ProcessRunner(private val logger: Logger) {
    // NOTESTS (for input redirection)
    // stty behaves differently if stdin is a TTY or not a TTY.
    // So we have to make sure that we don't redirect it.
    // However, while in theory we could use something like http://stackoverflow.com/a/911213/1668119
    // to test this, JUnit, Gradle and a dozen other things redirect the output of our tests, which means
    // that that method wouldn't work.
    // I can't find a nice way to test this, and once we call ioctl() directly rather than using stty, this
    // whole method becomes unnecessary anyway.
    fun runWithStdinAttached(command: Iterable<String>): ProcessOutput {
        logger.debug {
            message("Starting process.")
            data("command", command.toList())
        }

        runAndConvertExceptions(command.first()) {
            val process = ProcessBuilder(command.toList())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .start()

            val output = InputStreamReader(process.inputStream).readText()
            val exitCode = process.waitFor()

            logger.debug {
                message("Process exited.")
                data("command", command.toList())
                data("exitCode", exitCode)
                data("output", output)
            }

            return ProcessOutput(exitCode, output)
        }
    }

    // NOTESTS (for input / output redirection)
    fun runWithConsoleAttached(command: Iterable<String>): Int {
        logger.debug {
            message("Starting process.")
            data("command", command.toList())
        }

        runAndConvertExceptions(command.first()) {
            val process = ProcessBuilder(command.toList())
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .start()

            val exitCode = process.waitFor()

            logger.debug {
                message("Process exited.")
                data("command", command.toList())
                data("exitCode", exitCode)
            }

            return exitCode
        }
    }

    fun runAndCaptureOutput(command: Iterable<String>, stdin: String = ""): ProcessOutput {
        logger.debug {
            message("Starting process.")
            data("command", command.toList())
        }

        runAndConvertExceptions(command.first()) {
            val process = ProcessBuilder(command.toList())
                .redirectErrorStream(true)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .start()

            process.outputStream.write(stdin.toByteArray(Charset.defaultCharset()))
            process.outputStream.close()

            val output = InputStreamReader(process.inputStream).readText()
            val exitCode = process.waitFor()

            logger.debug {
                message("Process exited.")
                data("command", command.toList())
                data("exitCode", exitCode)
                data("output", output)
            }

            return ProcessOutput(exitCode, output)
        }
    }

    private inline fun <T> runAndConvertExceptions(executableName: String, action: () -> T): T {
        try {
            return action()
        } catch (e: IOException) {
            val unixDoesNotExistError = "error=2, No such file or directory"
            val unixNotExecutableError = "error=13, Permission denied"
            val windowsError = "CreateProcess error=2, The system cannot find the file specified"

            if (e.cause?.message in setOf(unixDoesNotExistError, unixNotExecutableError, windowsError)) {
                throw ExecutableDoesNotExistException(executableName, e)
            }

            throw e
        }
    }
}

data class ProcessOutput(val exitCode: Int, val output: String)

class ExecutableDoesNotExistException(executableName: String, cause: Throwable?) : RuntimeException("The executable '$executableName' could not be found or is not executable.", cause)
