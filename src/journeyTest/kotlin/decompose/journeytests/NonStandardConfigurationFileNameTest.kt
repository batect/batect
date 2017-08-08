package decompose.journeytests

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object NonStandardConfigurationFileNameTest : Spek({
    given("a configuration file with a non-standard name") {
        on("listing available tasks") {
            val runner = ApplicationRunner("non-standard-name", listOf("-f", "another-name.yml", "tasks"))
            val result = runner.run()

            it("prints a list of all available tasks") {
                assert.that(result.output, containsSubstring("task-1\ntask-2\ntask-3\n"))
            }

            it("returns a zero exit code") {
                assert.that(result.exitCode, equalTo(0))
            }
        }

        on("running a task") {
            val runner = ApplicationRunner("non-standard-name", listOf("-f", "another-name.yml", "run", "task-1"))
            val result = runner.run()

            it("prints the output of the task ") {
                assert.that(result.output, containsSubstring("This is some output from task 1"))
            }

            it("returns the exit code from the task") {
                assert.that(result.exitCode, equalTo(123))
            }
        }
    }
})
