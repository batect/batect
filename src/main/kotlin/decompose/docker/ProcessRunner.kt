package decompose.docker

interface ProcessRunner {
    fun run(command: Iterable<String>): Int
    fun runAndCaptureOutput(command: Iterable<String>): ProcessOutput
}

data class ProcessOutput(val exitCode: Int, val output: String)
