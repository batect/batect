/*
   Copyright 2017-2019 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.config.io

import batect.config.BuildImage
import batect.config.Container
import batect.config.ContainerMap
import batect.config.LiteralValue
import batect.config.PortMapping
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.config.VolumeMount
import batect.os.Command
import batect.os.PathResolver
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.nhaarman.mockitokotlin2.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ConfigurationFileSpec : Spek({
    describe("a configuration file") {
        describe("converting to a configuration model object") {
            on("converting an empty configuration file") {
                val configFile = ConfigurationFile("the_project_name")
                val pathResolver = mock<PathResolver>()
                val resultingConfig = configFile.toConfiguration(pathResolver)

                it("returns a configuration object with the project name") {
                    assertThat(resultingConfig.projectName, equalTo("the_project_name"))
                }

                it("returns a configuration object with no tasks") {
                    assertThat(resultingConfig.tasks, isEmpty)
                }

                it("returns a configuration object with no containers") {
                    assertThat(resultingConfig.containers, isEmpty)
                }
            }

            on("converting a configuration file with a task") {
                val runConfiguration = TaskRunConfiguration("some_container", Command.parse("some_command"), mapOf("SOME_VAR" to LiteralValue("some value")), setOf(PortMapping(123, 456)))
                val task = Task("the_task_name", runConfiguration, "Some description", setOf("dependency-1"), listOf("other-task"))
                val configFile = ConfigurationFile("the_project_name", TaskMap(task))
                val pathResolver = mock<PathResolver>()
                val resultingConfig = configFile.toConfiguration(pathResolver)

                it("returns a configuration object with the project name") {
                    assertThat(resultingConfig.projectName, equalTo("the_project_name"))
                }

                it("returns a configuration object with the task") {
                    assertThat(resultingConfig.tasks, equalTo(TaskMap(task)))
                }

                it("returns a configuration object with no containers") {
                    assertThat(resultingConfig.containers, isEmpty)
                }
            }

            on("converting a configuration file with a container") {
                val container = ContainerFromFile(
                    imageSource = BuildImage("/the_resolved_build_dir"),
                    command = Command.parse("the-command"),
                    environment = mapOf("ENV_VAR" to LiteralValue("/here")),
                    workingDirectory = "working_dir",
                    volumeMounts = setOf(VolumeMount("/local_volume_dir", "/remote", "some-options")),
                    portMappings = setOf(PortMapping(1234, 5678)),
                    dependencies = setOf("some-dependency"))

                val containerName = "the_container_name"
                val configFile = ConfigurationFile("the_project_name", containers = mapOf(containerName to container))

                val pathResolver = mock<PathResolver>()

                val resultingConfig = configFile.toConfiguration(pathResolver)

                it("returns a configuration object with the project name") {
                    assertThat(resultingConfig.projectName, equalTo("the_project_name"))
                }

                it("returns a configuration object with no tasks") {
                    assertThat(resultingConfig.tasks, isEmpty)
                }

                it("returns a configuration object with the container") {
                    assertThat(resultingConfig.containers, equalTo(ContainerMap(
                        Container(
                            containerName,
                            container.imageSource,
                            container.command,
                            container.environment,
                            container.workingDirectory,
                            container.volumeMounts,
                            container.portMappings,
                            container.dependencies)
                    )))
                }
            }
        }
    }
})
