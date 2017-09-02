package decompose.journeytests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
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
                assertThat(result.output, containsSubstring("""
                    |- task-1
                    |- task-2
                    |- task-3""".trimMargin()))
            }

            it("returns a zero exit code") {
                assertThat(result.exitCode, equalTo(0))
            }
        }

        on("running a task") {
            val runner = ApplicationRunner("non-standard-name", listOf("-f", "another-name.yml", "run", "task-1"))
            val result = runner.run()

            it("prints the output of the task ") {
                assertThat(result.output, containsSubstring("This is some output from task 1"))
            }

            it("returns the exit code from the task") {
                assertThat(result.exitCode, equalTo(123))
            }

            it("cleans up all containers it creates") {
                assertThat(result.potentiallyOrphanedContainers, isEmpty)
            }
        }
    }
})
