package decompose.journeytests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskWithUnhealthyDependencyTest : Spek({
    given("a task with an unhealthy dependency") {
        val runner = ApplicationRunner("task-with-unhealthy-dependency", listOf("run", "the-task"))

        on("running that task") {
            val result = runner.run()

            it("prints an appropriate error message") {
                assertThat(result.output, containsSubstring("Dependency 'http-server' did not become healthy: The configured health check did not report the container as healthy within the timeout period."))
            }

            it("returns a non-zero exit code") {
                assertThat(result.exitCode, !equalTo(0))
            }

            it("cleans up all containers it creates") {
                assertThat(result.potentiallyOrphanedContainers, isEmpty)
            }
        }
    }
})
