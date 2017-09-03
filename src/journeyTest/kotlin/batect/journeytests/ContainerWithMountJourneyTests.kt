package batect.journeytests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerWithMountJourneyTests : Spek({
    mapOf(
            "container-with-mount" to "a simple task with a volume mount",
            "container-with-cached-mount" to "a simple task with a cached volume mount"
    ).forEach { testName, description ->
        given(description) {
            val runner = ApplicationRunner(testName, listOf("run", "the-task"))

            on("running that task") {
                val result = runner.run()

                it("prints the output from that task") {
                    assertThat(result.output, containsSubstring("This is some output from the script"))
                }

                it("returns the exit code from that task") {
                    assertThat(result.exitCode, equalTo(123))
                }

                it("cleans up all containers it creates") {
                    assertThat(result.potentiallyOrphanedContainers, isEmpty)
                }
            }
        }
    }
})
