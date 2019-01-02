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
import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.os.PathType
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.nio.file.Paths

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
                val task = TaskFromFile(runConfiguration, "Some description", setOf("dependency-1"), listOf("other-task"))
                val taskName = "the_task_name"
                val configFile = ConfigurationFile("the_project_name", mapOf(taskName to task))
                val pathResolver = mock<PathResolver>()
                val resultingConfig = configFile.toConfiguration(pathResolver)

                it("returns a configuration object with the project name") {
                    assertThat(resultingConfig.projectName, equalTo("the_project_name"))
                }

                it("returns a configuration object with the task") {
                    assertThat(resultingConfig.tasks, equalTo(TaskMap(
                        Task(
                            taskName,
                            TaskRunConfiguration(runConfiguration.container, runConfiguration.command, runConfiguration.additionalEnvironmentVariables, runConfiguration.additionalPortMappings),
                            "Some description",
                            task.dependsOnContainers,
                            task.prerequisiteTasks
                        )
                    )))
                }

                it("returns a configuration object with no containers") {
                    assertThat(resultingConfig.containers, isEmpty)
                }
            }

            on("converting a configuration file with a container") {
                val originalBuildDirectory = "build_dir"
                val resolvedBuildDirectory = Paths.get("/the_resolved_build_dir")
                val volumeMountTargetPath = "/remote"

                val container = ContainerFromFile(
                    buildDirectory = originalBuildDirectory,
                    command = Command.parse("the-command"),
                    environment = mapOf("ENV_VAR" to LiteralValue("/here")),
                    workingDirectory = "working_dir",
                    volumeMounts = setOf(VolumeMount("/local_volume_dir", volumeMountTargetPath, "some-options")),
                    portMappings = setOf(PortMapping(1234, 5678)),
                    dependencies = setOf("some-dependency"))

                val containerName = "the_container_name"
                val configFile = ConfigurationFile("the_project_name", containers = mapOf(containerName to container))

                val pathResolver = mock<PathResolver> {
                    on { resolve(originalBuildDirectory) } doReturn PathResolutionResult.Resolved(resolvedBuildDirectory, PathType.Directory)
                }

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
                            BuildImage(resolvedBuildDirectory.toString()),
                            container.command,
                            container.environment,
                            container.workingDirectory,
                            container.volumeMounts,
                            container.portMappings,
                            container.dependencies)
                    )))
                }
            }

            data class BuildDirectoryResolutionTestCase(val description: String, val resolution: PathResolutionResult, val expectedMessage: String)

            setOf(
                BuildDirectoryResolutionTestCase(
                    "does not exist",
                    PathResolutionResult.Resolved(Paths.get("/some_resolved_path"), PathType.DoesNotExist),
                    "Build directory 'build_dir' (resolved to '/some_resolved_path') for container 'the_container_name' does not exist."
                ),
                BuildDirectoryResolutionTestCase(
                    "is a file",
                    PathResolutionResult.Resolved(Paths.get("/some_resolved_path"), PathType.File),
                    "Build directory 'build_dir' (resolved to '/some_resolved_path') for container 'the_container_name' is not a directory."
                ),
                BuildDirectoryResolutionTestCase(
                    "is neither a file or directory",
                    PathResolutionResult.Resolved(Paths.get("/some_resolved_path"), PathType.Other),
                    "Build directory 'build_dir' (resolved to '/some_resolved_path') for container 'the_container_name' is not a directory."
                ),
                BuildDirectoryResolutionTestCase(
                    "is an invalid path",
                    PathResolutionResult.InvalidPath("build_dir"),
                    "Build directory 'build_dir' for container 'the_container_name' is not a valid path."
                )
            ).forEach { (description, resolution, expectedMessage) ->
                on("converting a configuration file with a container that has a build directory that $description") {
                    val originalBuildDirectory = "build_dir"
                    val container = ContainerFromFile(originalBuildDirectory)
                    val configFile = ConfigurationFile("the_project_name", containers = mapOf("the_container_name" to container))

                    val pathResolver = mock<PathResolver> {
                        on { resolve(originalBuildDirectory) } doReturn resolution
                    }

                    it("fails with an appropriate error message") {
                        assertThat({ configFile.toConfiguration(pathResolver) }, throws(withMessage(expectedMessage)))
                    }
                }
            }
        }
    }
})
