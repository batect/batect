package decompose.docker

import java.io.InputStreamReader

class ProcessRunner {
    // NOTESTS
    // The Docker CLI behaves differently if stdin, stdout or stderr are redirected.
    // For example, the fancy progress display while pulling an image is disabled if it detects that
    // stdout is redirected.
    // So we have to make sure that we don't redirect them.
    // However, while in theory we could use something like http://stackoverflow.com/a/911213/1668119
    // to test this, JUnit, Gradle and a dozen other things redirect the output of our tests, which means
    // that that method wouldn't work.
    // I can't find a nice way to test this. Once we move to using the Docker API directly,
    // this whole method becomes unnecessary anyway, so I'm not too concerned about this.
    fun run(command: Iterable<String>): Int = ProcessBuilder(command.toList())
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectInput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor()

    fun runAndCaptureOutput(command: Iterable<String>): ProcessOutput {
        val process = ProcessBuilder(command.toList())
                .redirectErrorStream(true)
                .start()

        val exitCode = process.waitFor()
        val output = InputStreamReader(process.inputStream).readText()

        return ProcessOutput(exitCode, output)
    }

    fun <T> runAndProcessOutput(command: Iterable<String>, outputProcessor: (String) -> OutputProcessing<T>): RunAndProcessOutputResult<T> {
        val process = ProcessBuilder(command.toList())
                .redirectErrorStream(true)
                .start()

        try {
            val reader = InputStreamReader(process.inputStream)

            reader.useLines { lines ->
                for (line in lines) {
                    val result = outputProcessor(line)

                    if (result is KillProcess) {
                        process.destroy()
                        return KilledDuringProcessing(result.result)
                    }
                }
            }

            val exitCode = process.waitFor()
            return Exited(exitCode)
        } finally {
            process.destroyForcibly()
        }
    }
}

data class ProcessOutput(val exitCode: Int, val output: String)

sealed class OutputProcessing<T>
class Continue<T> : OutputProcessing<T>()
class KillProcess<T>(val result: T) : OutputProcessing<T>()

sealed class RunAndProcessOutputResult<T>
data class Exited<T>(val exitCode: Int) : RunAndProcessOutputResult<T>()
data class KilledDuringProcessing<T>(val result: T) : RunAndProcessOutputResult<T>()
