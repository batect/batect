package decompose.journeytests

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmptyString
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

object TaskWithExplicitDependencyJourneyTest : Spek({
    given("a task with an explicit dependency") {
        val testDirectory = Paths.get("src/journeyTest/resources/task-with-explicit-dependency").toAbsolutePath()
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

            process.waitFor()

            it("displays the output from that task") {
                val output = InputStreamReader(process.getInputStream()).readText()

                assert.that(output, containsSubstring("Status code for request: 200"))
            }

            it("returns the exit code from that task") {
                assert.that(process.exitValue(), equalTo(0))
            }
        }
    }
})
