package decompose.docker

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.File

object ProcessRunnerSpec : Spek({
    describe("a process runner") {
        val runner = ProcessRunner()

        on("running a process") {
            it("executes the command given") {
                val filePath = File.createTempFile("processrunner", ".tmp")
                filePath.deleteOnExit()

                val command = listOf("rm", filePath.absolutePath)
                runner.run(command)

                assert.that(filePath.exists(), equalTo(false))
            }

            it("returns the exit code of the command") {
                val command = listOf("sh", "-c", "exit 123")
                val exitCode = runner.run(command)

                assert.that(exitCode, equalTo(123))
            }
        }

        on("running a process and capturing the output") {
            val command = listOf("sh", "-c", "echo hello world && echo hello error world 2>&1 && echo more non-error output && exit 201")
            val result = runner.runAndCaptureOutput(command)

            it("returns the exit code of the command") {
                assert.that(result.exitCode, equalTo(201))
            }

            it("returns the combined standard output and standard error of the command") {
                assert.that(result.output, equalTo("hello world\nhello error world\nmore non-error output\n"))
            }
        }
    }
})
