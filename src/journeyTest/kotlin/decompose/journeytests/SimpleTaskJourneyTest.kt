package decompose.journeytests

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object SimpleTaskJourneyTest : Spek({
    mapOf(
            "simple-task" to "a simple task with the command specified in the configuration file",
            "simple-task-dockerfile-command" to "a simple task with the command specified in the Dockerfile"
    ).forEach { testName, description ->
        given(description) {
            val runner = ApplicationRunner(testName, listOf("decompose.yml", "the-task"))

            on("running that task") {
                val result = runner.run()

                it("prints the output from that task") {
                    assert.that(result.output, containsSubstring("This is some output from the task"))
                }

                it("returns the exit code from that task") {
                    assert.that(result.exitCode, equalTo(123))
                }
            }
        }
    }
})
