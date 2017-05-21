package decompose.journeytests

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

object SimpleTaskJourneyTest : Spek({
    mapOf(
            "simple-task" to "a simple task with the command specified in the configuration file",
            "simple-task-dockerfile-command" to "a simple task with the command specified in the Dockerfile"
    ).forEach { testName, description ->
        given(description) {
            val testDirectory = Paths.get("src/journeyTest/resources/$testName").toAbsolutePath()
            val applicationPath = Paths.get("build/install/decompose-kt/bin/decompose-kt").toAbsolutePath()

            beforeGroup {
                assert.that(Files.isDirectory(testDirectory), equalTo(true))
                assert.that(Files.isExecutable(applicationPath), equalTo(true))
            }

            on("running that task") {
                val process = ProcessBuilder(applicationPath.toString(), "decompose.yml", "the-task")
                        .directory(testDirectory.toFile())
                        .redirectErrorStream(true)
                        .start()

                process.waitFor(30, TimeUnit.SECONDS)

                it("prints the output from that task") {
                    val outputReader = BufferedReader(InputStreamReader(process.getInputStream()))
                    val output = outputReader.lines().collect(Collectors.joining("\n"))

                    assert.that(output, containsSubstring("This is some output from the task"))
                }

                it("returns the exit code from that task") {
                    assert.that(process.exitValue(), equalTo(123))
                }
            }
        }
    }
})
