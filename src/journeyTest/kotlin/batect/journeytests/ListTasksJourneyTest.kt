package batect.journeytests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ListTasksJourneyTest : Spek({
    given("a configuration file with multiple tasks") {
        val runner = ApplicationRunner("many-tasks", listOf("tasks"))

        on("listing available tasks") {
            val result = runner.run()

            it("prints a list of all available tasks") {
                assertThat(result.output, containsSubstring("""
                    |- task-1: do the first thing
                    |- task-2: do the second thing
                    |- task-3: do the third thing
                    """.trimMargin()))
            }

            it("returns a zero exit code") {
                assertThat(result.exitCode, equalTo(0))
            }
        }
    }
})
