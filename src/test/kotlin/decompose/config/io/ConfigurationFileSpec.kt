package decompose.config.io

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import decompose.config.Container
import decompose.config.ContainerMap
import decompose.config.PortMapping
import decompose.config.Task
import decompose.config.TaskMap
import decompose.config.TaskRunConfiguration
import decompose.config.VolumeMount
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.data_driven.data
import org.jetbrains.spek.data_driven.on

object ConfigurationFileSpec : Spek({
    describe("a configuration file") {
        describe("converting to a configuration model object") {
            on("converting an empty configuration file") {
                val configFile = ConfigurationFile("the_project_name")
                val pathResolver = mock<PathResolver>()
                val resultingConfig = configFile.toConfiguration(pathResolver)

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
                val task = TaskFromFile(TaskRunConfiguration("some_container", "some_command"), "Some description", setOf("dependency-1"))
                val taskName = "the_task_name"
                val configFile = ConfigurationFile("the_project_name", mapOf(taskName to task))
                val pathResolver = mock<PathResolver>()
                val resultingConfig = configFile.toConfiguration(pathResolver)

                it("returns a configuration object with the project name") {
                    assert.that(resultingConfig.projectName, equalTo("the_project_name"))
                }

                it("returns a configuration object with the task") {
                    assert.that(resultingConfig.tasks, equalTo(TaskMap(
                            Task(taskName, task.runConfiguration, "Some description", task.dependencies)
                    )))
                }

                it("returns a configuration object with no containers") {
                    assert.that(resultingConfig.containers, isEmpty)
                }
            }

            on("converting a configuration file with a container") {
                val originalBuildDirectory = "build_dir"
                val resolvedBuildDirectory = "/the_resolved_build_dir"
                val originalVolumeMountPath = "local_volume_dir"
                val resolvedVolumeMountPath = "/the_resolved_local_volume_dir"
                val volumeMountTargetPath = "/remote"

                val container = ContainerFromFile(
                        originalBuildDirectory,
                        "the-command",
                        mapOf("ENV_VAR" to "/here"),
                        "working_dir",
                        setOf(VolumeMount(originalVolumeMountPath, volumeMountTargetPath)),
                        setOf(PortMapping(1234, 5678)))

                val containerName = "the_container_name"
                val configFile = ConfigurationFile("the_project_name", containers = mapOf(containerName to container))

                val pathResolver = mock<PathResolver> {
                    on { resolve(originalBuildDirectory) } doReturn ResolvedToDirectory(resolvedBuildDirectory)
                    on { resolve(originalVolumeMountPath) } doReturn ResolvedToDirectory(resolvedVolumeMountPath)
                }

                val resultingConfig = configFile.toConfiguration(pathResolver)

                it("returns a configuration object with the project name") {
                    assert.that(resultingConfig.projectName, equalTo("the_project_name"))
                }

                it("returns a configuration object with no tasks") {
                    assert.that(resultingConfig.tasks, isEmpty)
                }

                it("returns a configuration object with the container") {
                    assert.that(resultingConfig.containers, equalTo(ContainerMap(
                            Container(
                                    containerName,
                                    resolvedBuildDirectory,
                                    container.command,
                                    container.environment,
                                    container.workingDirectory,
                                    setOf(VolumeMount(resolvedVolumeMountPath, volumeMountTargetPath)),
                                    container.portMappings)
                    )))
                }
            }

            on("converting a configuration file with a container that has a build directory that %s",
                    data("does not exist", NotFound("/some_resolved_path") as PathResolutionResult, "Build directory 'build_dir' (resolved to '/some_resolved_path') for container 'the_container_name' does not exist."),
                    data("is not a directory", ResolvedToFile("/some_resolved_path") as PathResolutionResult, "Build directory 'build_dir' (resolved to '/some_resolved_path') for container 'the_container_name' is not a directory."),
                    data("is an invalid path", InvalidPath as PathResolutionResult, "Build directory 'build_dir' for container 'the_container_name' is not a valid path."))
            { _, resolution, expectedMessage ->
                val originalBuildDirectory = "build_dir"
                val container = ContainerFromFile(originalBuildDirectory)
                val configFile = ConfigurationFile("the_project_name", containers = mapOf("the_container_name" to container))

                val pathResolver = mock<PathResolver> {
                    on { resolve(originalBuildDirectory) } doReturn resolution
                }

                it("fails with an appropriate error message") {
                    assert.that({ configFile.toConfiguration(pathResolver) }, throws(withMessage(expectedMessage)))
                }
            }

            on("converting a configuration file with a container that has a volume mount that is a %s",
                    data("directory", ResolvedToDirectory("/some_resolved_path") as PathResolutionResult),
                    data("file", ResolvedToFile("/some_resolved_path") as PathResolutionResult))
            { _, resolution ->
                val originalBuildDirectory = "build_dir"
                val originalVolumeMountPath = "local_volume_path"
                val container = ContainerFromFile(originalBuildDirectory, volumeMounts = setOf(VolumeMount(originalVolumeMountPath, "/container_path")))
                val configFile = ConfigurationFile("the_project_name", containers = mapOf("the_container_name" to container))

                val pathResolver = mock<PathResolver> {
                    on { resolve(originalBuildDirectory) } doReturn ResolvedToDirectory("/resolved_build_dir")
                    on { resolve(originalVolumeMountPath) } doReturn resolution
                }

                val resultingConfig = configFile.toConfiguration(pathResolver)
                val resultingContainer = resultingConfig.containers.getValue("the_container_name")

                it("returns a configuration object with the volume mount path resolved") {
                    assert.that(resultingContainer.volumeMounts, equalTo(setOf(VolumeMount("/some_resolved_path", "/container_path"))))
                }
            }

            on("converting a configuration file with a container that has a volume mount that has a local path that %s",
                    data("does not exist", NotFound("/some_resolved_path") as PathResolutionResult, "Local path 'local_volume_path' (resolved to '/some_resolved_path') for volume mount in container 'the_container_name' does not exist."),
                    data("is an invalid path", InvalidPath as PathResolutionResult, "Local path 'local_volume_path' for volume mount in container 'the_container_name' is not a valid path."))
            { _, resolution, expectedMessage ->
                val originalBuildDirectory = "build_dir"
                val originalVolumeMountPath = "local_volume_path"
                val container = ContainerFromFile(originalBuildDirectory, volumeMounts = setOf(VolumeMount(originalVolumeMountPath, "/container_path")))
                val configFile = ConfigurationFile("the_project_name", containers = mapOf("the_container_name" to container))

                val pathResolver = mock<PathResolver> {
                    on { resolve(originalBuildDirectory) } doReturn ResolvedToDirectory("/resolved_build_dir")
                    on { resolve(originalVolumeMountPath) } doReturn resolution
                }

                it("fails with an appropriate error message") {
                    assert.that({ configFile.toConfiguration(pathResolver) }, throws(withMessage(expectedMessage)))
                }
            }
        }
    }
})
