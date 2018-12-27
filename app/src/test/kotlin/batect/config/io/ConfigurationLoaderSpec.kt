/*
   Copyright 2017-2018 Charles Korn.

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
import batect.config.Configuration
import batect.config.HealthCheckConfig
import batect.config.LiteralValue
import batect.config.PortMapping
import batect.config.PullImage
import batect.config.RunAsCurrentUserConfig
import batect.config.VolumeMount
import batect.os.Command
import batect.os.PathResolutionResult
import batect.os.PathResolverFactory
import batect.os.PathType
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.withFileName
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.isEmptyString
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.mockito.ArgumentMatchers.anyString
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

object ConfigurationLoaderSpec : Spek({
    describe("a configuration loader") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix()) }

        val pathResolverFactory by createForEachTest {
            mock<PathResolverFactory> {
                on { createResolver(any()) } doAnswer { invocation ->
                    val rootPath = invocation.arguments[0] as Path

                    mock {
                        on { resolve(anyString()) } doAnswer { invocation ->
                            val path = invocation.arguments[0] as String
                            PathResolutionResult.Resolved(fileSystem.getPath("/resolved/$path"), PathType.Directory)
                        }

                        on { relativeTo } doReturn rootPath
                    }
                }
            }
        }

        val logger by createLoggerForEachTest()
        val testFileName = "/theTestFile.yml"
        val loader by createForEachTest { ConfigurationLoader(pathResolverFactory, logger) }

        fun loadConfiguration(config: String, path: String = testFileName): Configuration {
            val filePath = fileSystem.getPath(path)
            val directory = filePath.parent

            if (directory != null) {
                Files.createDirectories(directory)
            }

            Files.write(filePath, config.toByteArray())

            try {
                return loader.loadConfig(filePath)
            } finally {
                Files.delete(filePath)
            }
        }

        on("loading an empty configuration file") {
            it("should fail with an error message") {
                assertThat({ loadConfiguration("") }, throws(withMessage("File '$testFileName' contains no configuration.") and withFileName(testFileName)))
            }
        }

        given("a valid configuration file with no explicit project name") {
            val configString = """
                |tasks:
                |  the-task:
                |    run:
                |      container: build-env
                """.trimMargin()

            on("loading that file from the root directory") {
                val path = "/config.yml"

                it("should fail with an error message") {
                    assertThat({ loadConfiguration(configString, path) }, throws(withMessage("No project name has been given explicitly, but the configuration file is in the root directory and so a project name cannot be inferred.") and withFileName(path)))
                }
            }

            on("loading that file from a directory in the root directory") {
                val path = "/project/config.yml"
                val config = loadConfiguration(configString, path)

                it("should use the parent directory's name as the project name") {
                    assertThat(config.projectName, equalTo("project"))
                }
            }

            on("loading that file from a subdirectory") {
                val path = "/code/project/config.yml"
                val config = loadConfiguration(configString, path)

                it("should use the parent directory's name as the project name") {
                    assertThat(config.projectName, equalTo("project"))
                }
            }
        }

        on("loading a valid configuration file with no containers or tasks defined") {
            val config = loadConfiguration("project_name: the_cool_project")

            it("should return a populated configuration object with the project name specified") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }
        }

        on("loading a valid configuration file with a task with no dependencies") {
            val configString = """
                |project_name: the_cool_project
                |
                |tasks:
                |  first_task:
                |    run:
                |      container: build-env
                |      command: ./gradlew doStuff
                |      environment:
                |        OPTS: -Dthing
                |        INT_VALUE: 1
                |        FLOAT_VALUE: 12.6000
                |        BOOL_VALUE: true
                |        OTHER_VALUE: "the value"
                |      ports:
                |        - 123:456
                |        - local: 1000
                |          container: 2000
                """.trimMargin()

            val config = loadConfiguration(configString)

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single task specified") {
                assertThat(config.tasks.keys, equalTo(setOf("first_task")))
            }

            it("should load the configuration for the task") {
                val task = config.tasks["first_task"]!!
                assertThat(task.name, equalTo("first_task"))
                assertThat(task.runConfiguration.container, equalTo("build-env"))
                assertThat(task.runConfiguration.command, equalTo(Command.parse("./gradlew doStuff")))
                assertThat(task.runConfiguration.additionalEnvironmentVariables, equalTo(mapOf(
                    "OPTS" to LiteralValue("-Dthing"),
                    "INT_VALUE" to LiteralValue("1"),
                    "FLOAT_VALUE" to LiteralValue("12.6000"),
                    "BOOL_VALUE" to LiteralValue("true"),
                    "OTHER_VALUE" to LiteralValue("the value")
                )))
                assertThat(task.runConfiguration.additionalPortMappings, equalTo(setOf(PortMapping(123, 456), PortMapping(1000, 2000))))
                assertThat(task.dependsOnContainers, isEmpty)
                assertThat(task.prerequisiteTasks, isEmpty)
                assertThat(task.description, isEmptyString)
            }
        }

        on("loading a valid configuration file with a task with no command") {
            val configString = """
                |project_name: the_cool_project
                |
                |tasks:
                |  first_task:
                |    run:
                |      container: build-env
                """.trimMargin()

            val config = loadConfiguration(configString)

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single task specified") {
                assertThat(config.tasks.keys, equalTo(setOf("first_task")))
            }

            it("should load the configuration for the task") {
                val task = config.tasks["first_task"]!!
                assertThat(task.name, equalTo("first_task"))
                assertThat(task.runConfiguration.container, equalTo("build-env"))
                assertThat(task.runConfiguration.command, absent())
                assertThat(task.dependsOnContainers, isEmpty)
                assertThat(task.prerequisiteTasks, isEmpty)
                assertThat(task.description, isEmptyString)
            }
        }

        on("loading a valid configuration file with a task with a description") {
            val configString = """
                |project_name: the_cool_project
                |
                |tasks:
                |  first_task:
                |    description: The very first task.
                |    run:
                |      container: build-env
                """.trimMargin()

            val config = loadConfiguration(configString)

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single task specified") {
                assertThat(config.tasks.keys, equalTo(setOf("first_task")))
            }

            it("should load the configuration for the task") {
                val task = config.tasks["first_task"]!!
                assertThat(task.name, equalTo("first_task"))
                assertThat(task.runConfiguration.container, equalTo("build-env"))
                assertThat(task.runConfiguration.command, absent())
                assertThat(task.dependsOnContainers, isEmpty)
                assertThat(task.prerequisiteTasks, isEmpty)
                assertThat(task.description, equalTo("The very first task."))
            }
        }

        on("loading a valid configuration file with a task with some container dependencies") {
            val configString = """
                |project_name: the_cool_project
                |
                |tasks:
                |  first_task:
                |    run:
                |      container: build-env
                |      command: ./gradlew doStuff
                |    dependencies:
                |      - dependency-1
                |      - dependency-2
                """.trimMargin()

            val config = loadConfiguration(configString)

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single task specified") {
                assertThat(config.tasks.keys, equalTo(setOf("first_task")))
            }

            it("should load the configuration for the task") {
                val task = config.tasks["first_task"]!!
                assertThat(task.name, equalTo("first_task"))
                assertThat(task.runConfiguration.container, equalTo("build-env"))
                assertThat(task.runConfiguration.command, equalTo(Command.parse("./gradlew doStuff")))
                assertThat(task.dependsOnContainers, equalTo(setOf("dependency-1", "dependency-2")))
                assertThat(task.prerequisiteTasks, isEmpty)
                assertThat(task.description, isEmptyString)
            }
        }

        on("loading a configuration file with a task that has a container dependency defined twice") {
            val config = """
                |project_name: the_cool_project
                |
                |tasks:
                |  first_task:
                |    run:
                |      container: build-env
                |    dependencies:
                |      - dependency-1
                |      - dependency-1
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("The dependency 'dependency-1' is given more than once") and withLineNumber(9) and withFileName(testFileName)))
            }
        }

        on("loading a valid configuration file with a task with some prerequisite tasks") {
            val configString = """
                |project_name: the_cool_project
                |
                |tasks:
                |  first_task:
                |    run:
                |      container: build-env
                |      command: ./gradlew doStuff
                |    prerequisites:
                |      - other-task
                |      - another-task
                """.trimMargin()

            val config = loadConfiguration(configString)

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single task specified") {
                assertThat(config.tasks.keys, equalTo(setOf("first_task")))
            }

            it("should load the configuration for the task") {
                val task = config.tasks["first_task"]!!
                assertThat(task.name, equalTo("first_task"))
                assertThat(task.runConfiguration.container, equalTo("build-env"))
                assertThat(task.runConfiguration.command, equalTo(Command.parse("./gradlew doStuff")))
                assertThat(task.dependsOnContainers, isEmpty)
                assertThat(task.prerequisiteTasks, equalTo(listOf("other-task", "another-task")))
                assertThat(task.description, isEmptyString)
            }
        }

        on("loading a configuration file with a task that has a prerequisite task defined twice") {
            val config = """
                |project_name: the_cool_project
                |
                |tasks:
                |  first_task:
                |    run:
                |      container: build-env
                |    prerequisites:
                |      - dependency-1
                |      - dependency-1
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("The prerequisite 'dependency-1' is given more than once") and withLineNumber(9) and withFileName(testFileName)))
            }
        }

        on("loading a valid configuration file with a container with just a build directory configured") {
            val configString = """
                    |project_name: the_cool_project
                    |
                    |containers:
                    |  container-1:
                    |    build_directory: container-1-build-dir
                    """.trimMargin()

            val config = loadConfiguration(configString)

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single container specified") {
                assertThat(config.containers.keys, equalTo(setOf("container-1")))
            }

            it("should load the build directory specified for the container and resolve it to an absolute path") {
                val container = config.containers["container-1"]!!
                assertThat(container.name, equalTo("container-1"))
                assertThat(container.imageSource, equalTo(BuildImage("/resolved/container-1-build-dir")))
            }
        }

        on("loading a valid configuration file with a container with just an image configured") {
            val configString = """
                    |project_name: the_cool_project
                    |
                    |containers:
                    |  container-1:
                    |    image: some-image:1.2.3
                    """.trimMargin()

            val config = loadConfiguration(configString)

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single container specified") {
                assertThat(config.containers.keys, equalTo(setOf("container-1")))
            }

            it("should load the image specified for the container") {
                val container = config.containers["container-1"]!!
                assertThat(container.name, equalTo("container-1"))
                assertThat(container.imageSource, equalTo(PullImage("some-image:1.2.3")))
            }
        }

        on("loading a configuration file with a container without a build directory or image") {
            val config = """
                    |project_name: the_cool_project
                    |
                    |containers:
                    |  container-1:
                    |    working_directory: /there
                    """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Container 'container-1' is invalid: either build_directory or image must be specified.") and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a container with both a build directory and an image") {
            val config = """
                    |project_name: the_cool_project
                    |
                    |containers:
                    |  container-1:
                    |    build_directory: /there
                    |    image: some-image:1.2.3
                    """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Container 'container-1' is invalid: only one of build_directory or image can be specified, but both have been provided.") and withFileName(testFileName)))
            }
        }

        on("loading a valid configuration file with a container with all optional fields given") {
            val configString = """
                    |project_name: the_cool_project
                    |
                    |containers:
                    |  container-1:
                    |    build_directory: container-1-build-dir
                    |    command: do-the-thing.sh some-param
                    |    environment:
                    |      OPTS: -Dthing
                    |      INT_VALUE: 1
                    |      FLOAT_VALUE: 12.6000
                    |      BOOL_VALUE: true
                    |      OTHER_VALUE: "the value"
                    |    working_directory: /here
                    |    volumes:
                    |      - ../:/here
                    |      - /somewhere:/else:ro
                    |    ports:
                    |      - "1234:5678"
                    |      - "9012:3456"
                    |    health_check:
                    |      interval: 2s
                    |      retries: 10
                    |      start_period: 1s
                    |    run_as_current_user:
                    |      enabled: true
                    |      home_directory: /home/something
                    """.trimMargin()

            val config = loadConfiguration(configString)

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single container specified") {
                assertThat(config.containers.keys, equalTo(setOf("container-1")))
            }

            it("should load all of the configuration specified for the container") {
                val container = config.containers["container-1"]!!
                assertThat(container.name, equalTo("container-1"))
                assertThat(container.imageSource, equalTo(BuildImage("/resolved/container-1-build-dir")))
                assertThat(container.command, equalTo(Command.parse("do-the-thing.sh some-param")))
                assertThat(container.environment, equalTo(mapOf(
                    "OPTS" to LiteralValue("-Dthing"),
                    "INT_VALUE" to LiteralValue("1"),
                    "FLOAT_VALUE" to LiteralValue("12.6000"),
                    "BOOL_VALUE" to LiteralValue("true"),
                    "OTHER_VALUE" to LiteralValue("the value")
                )))
                assertThat(container.workingDirectory, equalTo("/here"))
                assertThat(container.portMappings, equalTo(setOf(PortMapping(1234, 5678), PortMapping(9012, 3456))))
                assertThat(container.healthCheckConfig, equalTo(HealthCheckConfig(Duration.ofSeconds(2), 10, Duration.ofSeconds(1))))
                assertThat(container.runAsCurrentUserConfig, equalTo(RunAsCurrentUserConfig(true, "/home/something")))
                assertThat(container.volumeMounts, equalTo(setOf(
                    VolumeMount("/resolved/..", "/here", null),
                    VolumeMount("/resolved/somewhere", "/else", "ro")
                )))
            }
        }

        on("loading a valid configuration file with a container with a volume specified in expanded format") {
            val configString = """
                    |project_name: the_cool_project
                    |
                    |containers:
                    |  container-1:
                    |    build_directory: container-1-build-dir
                    |    volumes:
                    |      - local: ../
                    |        container: /here
                    |      - local: /somewhere
                    |        container: /else
                    |        options: ro
                    """.trimMargin()

            val config = loadConfiguration(configString)

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single container specified") {
                assertThat(config.containers.keys, equalTo(setOf("container-1")))
            }

            it("should load all of the configuration specified for the container") {
                val container = config.containers["container-1"]!!
                assertThat(container.name, equalTo("container-1"))
                assertThat(container.imageSource, equalTo(BuildImage("/resolved/container-1-build-dir")))
                assertThat(container.volumeMounts, equalTo(setOf(
                    VolumeMount("/resolved/..", "/here", null),
                    VolumeMount("/resolved/somewhere", "/else", "ro")
                )))
            }
        }

        on("loading a valid configuration file with a container with a port mapping specified in expanded format") {
            val configString = """
                    |project_name: the_cool_project
                    |
                    |containers:
                    |  container-1:
                    |    build_directory: container-1-build-dir
                    |    ports:
                    |      - local: 1234
                    |        container: 5678
                    |      - local: 9012
                    |        container: 3456
                    """.trimMargin()

            val config = loadConfiguration(configString)

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single container specified") {
                assertThat(config.containers.keys, equalTo(setOf("container-1")))
            }

            it("should load all of the configuration specified for the container") {
                val container = config.containers["container-1"]!!
                assertThat(container.name, equalTo("container-1"))
                assertThat(container.imageSource, equalTo(BuildImage("/resolved/container-1-build-dir")))
                assertThat(container.portMappings, equalTo(setOf(PortMapping(1234, 5678), PortMapping(9012, 3456))))
            }
        }

        on("loading a valid configuration file with a container with a dependency") {
            val configString = """
                    |project_name: the_cool_project
                    |
                    |containers:
                    |  container-1:
                    |    build_directory: container-1-build-dir
                    |    dependencies:
                    |      - container-2
                    """.trimMargin()

            val config = loadConfiguration(configString)

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single container specified") {
                assertThat(config.containers.keys, equalTo(setOf("container-1")))
            }

            it("should load the dependency specified") {
                val container = config.containers["container-1"]!!
                assertThat(container.dependencies, equalTo(setOf("container-2")))
            }
        }

        on("loading a configuration file with a container that has a dependency defined twice") {
            val config = """
                |project_name: the_cool_project
                |
                |containers:
                |  container-1:
                |    build_directory: container-1-build-dir
                |    dependencies:
                |      - container-2
                |      - container-2
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("The dependency 'container-2' is given more than once") and withLineNumber(8) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file where the project name is given twice") {
            val config = """
                |project_name: the_cool_project
                |project_name: the_really_cool_project
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Duplicate key 'project_name'. It was previously given at line 1, column 1.") and withLineNumber(2) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file where an unknown field name is used") {
            val config = """
                |project_name: the_cool_project
                |thing: value
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Unknown property 'thing'. Known properties are: containers, project_name, tasks") and withLineNumber(2) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a full-line comment") {
            val configString = """
                |# This is a comment
                |project_name: the_cool_project
                """.trimMargin()

            val config = loadConfiguration(configString)

            it("should ignore the comment") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }
        }

        on("loading a configuration file with an end-of-line comment") {
            val config = loadConfiguration("project_name: the_cool_project # This is a comment")

            it("should ignore the comment") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }
        }

        on("loading a configuration file with a task defined twice") {
            val config = """
                |project_name: the_cool_project
                |
                |tasks:
                |  first_task:
                |    run:
                |      container: build-env
                |  first_task:
                |    run:
                |      container: other-build-env
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Duplicate key 'first_task'. It was previously given at line 4, column 3.") and withLineNumber(7) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a container defined twice") {
            val config = """
                |project_name: the_cool_project
                |
                |containers:
                |  container-1:
                |    build_directory: container-1
                |  container-1:
                |    build_directory: other-container-1
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Duplicate key 'container-1'. It was previously given at line 4, column 3.") and withLineNumber(6) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a container with an environment variable defined twice") {
            val config = """
                |project_name: the_cool_project
                |
                |containers:
                |  container-1:
                |    build_directory: container-1
                |    environment:
                |      THING: value1
                |      THING: value2
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Duplicate key 'THING'. It was previously given at line 7, column 7.") and withLineNumber(8) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a task with an environment variable defined twice") {
            val config = """
                |project_name: the_cool_project
                |
                |tasks:
                |  the-task:
                |    run:
                |      container: the-container
                |      environment:
                |        THING: value1
                |        THING: value2
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Duplicate key 'THING'. It was previously given at line 8, column 9.") and withLineNumber(9) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a container with an environment variable with no value") {
            val config = """
                |project_name: the_cool_project
                |
                |containers:
                |  container-1:
                |    build_directory: container-1
                |    environment:
                |      THING:
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Value for 'THING' is invalid: Unexpected null or empty value for non-null field.") and withLineNumber(7) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a task with an environment variable with no value") {
            val config = """
                |project_name: the_cool_project
                |
                |tasks:
                |  the-task:
                |    run:
                |      container: the-container
                |      environment:
                |        THING:
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Value for 'THING' is invalid: Unexpected null or empty value for non-null field.") and withLineNumber(8) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a container with 'run as current user' explicitly disabled but a home directory provided") {
            val config = """
                |project_name: the_cool_project
                |
                |containers:
                |  container-1:
                |    build_directory: container-1
                |    run_as_current_user:
                |      enabled: false
                |      home_directory: /home/something
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Container 'container-1' is invalid: running as the current user has not been enabled, but a home directory for that user has been provided.") and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a container with 'run as current user' enabled but no home directory provided") {
            val config = """
                |project_name: the_cool_project
                |
                |containers:
                |  container-1:
                |    build_directory: container-1
                |    run_as_current_user:
                |      enabled: true
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Container 'container-1' is invalid: running as the current user has been enabled, but a home directory for that user has not been provided.") and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a container with 'run as current user' not enabled but a home directory provided") {
            val config = """
                |project_name: the_cool_project
                |
                |containers:
                |  container-1:
                |    build_directory: container-1
                |    run_as_current_user:
                |      home_directory: /home/something
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Container 'container-1' is invalid: running as the current user has not been enabled, but a home directory for that user has been provided.") and withFileName(testFileName)))
            }
        }

        on("loading attempting to load a configuration file that does not exist") {
            it("should fail with an appropriate error message") {
                assertThat({ loader.loadConfig(fileSystem.getPath("/doesntexist.yml")) }, throws(withMessage("The file '/doesntexist.yml' does not exist.")))
            }
        }

        on("loading a configuration file with an empty container") {
            val config = """
                |project_name: the_cool_project
                |
                |containers:
                |  container-1:
                |    build_directory: container-1
                |  container-2:
                |
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Value for 'container-2' is invalid: Unexpected null or empty value for non-null field.") and withLineNumber(6) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with an empty task") {
            val config = """
                |project_name: the_cool_project
                |
                |containers:
                |  container-1:
                |    build_directory: container-1
                |tasks:
                |   task-1:
                |
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Value for 'task-1' is invalid: Unexpected null or empty value for non-null field.") and withLineNumber(7) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a container with an invalid local port in the expanded format") {
            val config = """
                    |project_name: the_cool_project
                    |
                    |containers:
                    |  container-1:
                    |    image: some-image:1.2.3
                    |    ports:
                    |      - local: abc123
                    |        container: 1000
                    """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Value for 'local' is invalid: Value 'abc123' is not a valid integer value.") and withLineNumber(7) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a container with an invalid container port in the expanded format") {
            val config = """
                    |project_name: the_cool_project
                    |
                    |containers:
                    |  container-1:
                    |    image: some-image:1.2.3
                    |    ports:
                    |      - local: 1000
                    |        container: abc123
                    """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Value for 'container' is invalid: Value 'abc123' is not a valid integer value.") and withLineNumber(8) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a container with an invalid port mapping in the concise format") {
            val config = """
                    |project_name: the_cool_project
                    |
                    |containers:
                    |  container-1:
                    |    image: some-image:1.2.3
                    |    ports:
                    |      - abc123:1000
                    """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Port mapping definition 'abc123:1000' is not valid. It must be in the form 'local_port:container_port' and each port must be a positive integer.") and withLineNumber(7) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a task with an invalid local port in the expanded format") {
            val config = """
                    |project_name: the_cool_project
                    |
                    |tasks:
                    |  task-1:
                    |    run:
                    |       container: some-container
                    |       ports:
                    |         - local: abc123
                    |           container: 1000
                    """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Value for 'local' is invalid: Value 'abc123' is not a valid integer value.") and withLineNumber(8) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a task with an invalid container port in the expanded format") {
            val config = """
                    |project_name: the_cool_project
                    |
                    |tasks:
                    |  task-1:
                    |    run:
                    |      container: some-container
                    |      ports:
                    |        - local: 1000
                    |          container: abc123
                    """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Value for 'container' is invalid: Value 'abc123' is not a valid integer value.") and withLineNumber(9) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a task with an invalid port mapping in the concise format") {
            val config = """
                    |project_name: the_cool_project
                    |
                    |tasks:
                    |  task-1:
                    |    run:
                    |      container: some-container
                    |      ports:
                    |        - abc123:1000
                    """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Port mapping definition 'abc123:1000' is not valid. It must be in the form 'local_port:container_port' and each port must be a positive integer.") and withLineNumber(8) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a task with an invalid command line") {
            val config = """
                |project_name: the_cool_project
                |
                |tasks:
                |  the-task:
                |    run:
                |      container: the-container
                |      command: "'"
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Command `'` is invalid: it contains an unbalanced single quote") and withLineNumber(7) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a container with an invalid command line") {
            val config = """
                |project_name: the_cool_project
                |
                |containers:
                |  container-1:
                |    build_directory: container-1
                |    command: "'"
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Command `'` is invalid: it contains an unbalanced single quote") and withLineNumber(6) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a volume mount in expanded format missing the container path") {
            val configString = """
                |containers:
                |  container-1:
                |    build_directory: container-1
                |    volumes:
                |      - local: thing
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(configString) }, throws(withMessage("Field 'container' is required but it is missing.") and withLineNumber(5) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a volume mount in expanded format missing the local path") {
            val configString = """
                |containers:
                |  container-1:
                |    build_directory: container-1
                |    volumes:
                |      - container: thing
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(configString) }, throws(withMessage("Field 'local' is required but it is missing.") and withLineNumber(5) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a volume mount in expanded format with an unknown field") {
            val configString = """
                |containers:
                |  container-1:
                |    build_directory: container-1
                |    volumes:
                |      - local: here
                |        container: there
                |        something_else: value
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(configString) }, throws(withMessage("Unknown property 'something_else'. Known properties are: container, local, options") and withLineNumber(7) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a port mapping for a container in expanded format missing the container port") {
            val configString = """
                |containers:
                |  container-1:
                |    build_directory: container-1
                |    ports:
                |      - local: 1234
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(configString) }, throws(withMessage("Field 'container' is required but it is missing.") and withLineNumber(5) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a port mapping for a container in expanded format missing the local port") {
            val configString = """
                |containers:
                |  container-1:
                |    build_directory: container-1
                |    ports:
                |      - container: 1234
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(configString) }, throws(withMessage("Field 'local' is required but it is missing.") and withLineNumber(5) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a port mapping for a container in expanded format with an unknown field") {
            val configString = """
                |containers:
                |  container-1:
                |    build_directory: container-1
                |    ports:
                |      - local: 1234
                |        container: 5678
                |        something_else: value
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(configString) }, throws(withMessage("Unknown property 'something_else'. Known properties are: container, local") and withLineNumber(7) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a port mapping for a task in expanded format missing the container port") {
            val configString = """
                |tasks:
                |  task-1:
                |    run:
                |      container: container-1
                |      ports:
                |        - local: 1234
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(configString) }, throws(withMessage("Field 'container' is required but it is missing.") and withLineNumber(6) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a port mapping for a task in expanded format missing the local port") {
            val configString = """
                |tasks:
                |  task-1:
                |    run:
                |      container: container-1
                |      ports:
                |        - container: 1234
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(configString) }, throws(withMessage("Field 'local' is required but it is missing.") and withLineNumber(6) and withFileName(testFileName)))
            }
        }

        on("loading a configuration file with a port mapping for a task in expanded format with an unknown field") {
            val configString = """
                |tasks:
                |  task-1:
                |    run:
                |      container: container-1
                |      ports:
                |        - local: 1234
                |          container: 5678
                |          something_else: value
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(configString) }, throws(withMessage("Unknown property 'something_else'. Known properties are: container, local") and withLineNumber(8) and withFileName(testFileName)))
            }
        }
    }
})
