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
                .start();

        val exitCode = process.waitFor()
        val output = InputStreamReader(process.inputStream).readText()

        return ProcessOutput(exitCode, output)
    }
}

data class ProcessOutput(val exitCode: Int, val output: String)
