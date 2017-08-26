package decompose.docker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import decompose.config.Container
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DockerImageLabellingStrategySpec : Spek({
    describe("a Docker image labelling strategy") {
        val imageLabellingStrategy = DockerImageLabellingStrategy()

        given("a project name and a container definition") {
            val projectName = "the-project"
            val container = Container("the-container", "/build-dir/doesnt/matter")

            on("generating a label for the image") {
                val label = imageLabellingStrategy.labelImage(projectName, container)

                it("returns the expected image name") {
                    assertThat(label, equalTo("the-project-the-container:latest"))
                }
            }
        }
    }
})
