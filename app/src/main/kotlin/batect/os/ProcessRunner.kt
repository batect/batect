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

import batect.logging.Logger
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset

class ProcessRunner(private val logger: Logger) {
    // NOTESTS (for input / output redirection)
    // The Docker CLI behaves differently if stdin, stdout or stderr are redirected.
    // For example, the fancy progress display while pulling an image is disabled if it detects that
    // stdout is redirected.
    // So we have to make sure that we don't redirect them.
    // However, while in theory we could use something like http://stackoverflow.com/a/911213/1668119
    // to test this, JUnit, Gradle and a dozen other things redirect the output of our tests, which means
    // that that method wouldn't work.
    // I can't find a nice way to test this. Once we move to using the Docker API directly,
    // this whole method becomes unnecessary anyway, so I'm not too concerned about this.
    fun run(command: Iterable<String>): Int {
        logger.debug {
            message("Starting process.")
            data("command", command)
        }

        val exitCode = ProcessBuilder(command.toList())
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectInput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor()

        logger.debug {
            message("Process exited.")
            data("command", command)
            data("exitCode", exitCode)
        }

        return exitCode
    }

    fun runAndCaptureOutput(command: Iterable<String>, stdin: String = ""): ProcessOutput {
        logger.debug {
            message("Starting process.")
            data("command", command)
        }

        try {
            val process = ProcessBuilder(command.toList())
                .redirectErrorStream(true)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .start()

            process.outputStream.write(stdin.toByteArray(Charset.defaultCharset()))
            process.outputStream.close()

            val exitCode = process.waitFor()
            val output = InputStreamReader(process.inputStream).readText()

            logger.debug {
                message("Process exited.")
                data("command", command)
                data("exitCode", exitCode)
                data("output", output)
            }

            return ProcessOutput(exitCode, output)
        } catch (e: IOException) {
            if (e.cause?.message == "error=2, No such file or directory") {
                val executableName = command.first()
                throw ExecutableDoesNotExistException(executableName, e)
            }

            throw e
        }
    }
}

data class ProcessOutput(val exitCode: Int, val output: String)

class ExecutableDoesNotExistException(val executableName: String, cause: Throwable?) : RuntimeException("The executable '$executableName' could not be found.", cause)
