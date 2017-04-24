package decompose.config.io

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import decompose.config.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ConfigurationFileSpec : Spek({
    describe("a configuration file") {
        describe("converting to a configuration model object") {
            on("converting an empty configuration file") {
                val configFile = ConfigurationFile("the_project_name")
                val resultingConfig = configFile.toConfiguration()

                it("returns a configuration object with the project name") {
                    assert.that(resultingConfig.projectName, equalTo("the_project_name"))
                }

                it("returns a configuration object with no tasks") {
                    assert.that(resultingConfig.tasks, isEmpty)
                }

                it("returns a configuration object with no containers") {
                    assert.that(resultingConfig.containers, isEmpty)
                }
            }

            on("converting a configuration file with a task") {
                val task = TaskFromFile(TaskRunConfiguration("some_container", "some_command"), setOf("dependency-1"))
                val taskName = "the_task_name"
                val configFile = ConfigurationFile("the_project_name", mapOf(taskName to task))
                val resultingConfig = configFile.toConfiguration()

                it("returns a configuration object with the project name") {
                    assert.that(resultingConfig.projectName, equalTo("the_project_name"))
                }

                it("returns a configuration object with the task") {
                    assert.that(resultingConfig.tasks, equalTo(TaskMap(
                            Task(taskName, task.runConfiguration, task.dependencies)
                    )))
                }

                it("returns a configuration object with no containers") {
                    assert.that(resultingConfig.containers, isEmpty)
                }
            }

            on("converting a configuration file with a container") {
                val container = ContainerFromFile(
                        "build_dir",
                        mapOf("PATH" to "/here"),
                        "working_dir",
                        setOf(VolumeMount("/local", "/remote")),
                        setOf(PortMapping(1234, 5678)))

                val containerName = "the_container_name"
                val configFile = ConfigurationFile("the_project_name", containers = mapOf(containerName to container))
                val resultingConfig = configFile.toConfiguration()

                it("returns a configuration object with the project name") {
                    assert.that(resultingConfig.projectName, equalTo("the_project_name"))
                }

                it("returns a configuration object with no tasks") {
                    assert.that(resultingConfig.tasks, isEmpty)
                }

                it("returns a configuration object with the container") {
                    assert.that(resultingConfig.containers, equalTo(ContainerMap(
                            Container(containerName, container.buildDirectory, container.environment, container.workingDirectory, container.volumeMounts, container.portMappings)
                    )))
                }
            }
        }
    }
})
