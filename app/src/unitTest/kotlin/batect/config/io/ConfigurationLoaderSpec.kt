/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.config.io

import batect.config.BuildImage
import batect.config.ConfigVariableDefinition
import batect.config.ConfigVariableMap
import batect.config.Container
import batect.config.ContainerMap
import batect.config.FileInclude
import batect.config.GitInclude
import batect.config.HealthCheckConfig
import batect.config.Include
import batect.config.LiteralValue
import batect.config.LocalMount
import batect.config.PortMapping
import batect.config.PullImage
import batect.config.RunAsCurrentUserConfig
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.config.includes.GitRepositoryCacheNotificationListener
import batect.config.includes.GitRepositoryReference
import batect.config.includes.IncludeResolver
import batect.git.GitException
import batect.os.Command
import batect.os.DefaultPathResolutionContext
import batect.os.PathResolverFactory
import batect.telemetry.TelemetryConsent
import batect.telemetry.TestTelemetryCaptor
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.hasSize
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.isEmptyString
import com.natpryce.hamkrest.throws
import kotlinx.serialization.json.JsonPrimitive
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

object ConfigurationLoaderSpec : Spek({
    describe("a configuration loader") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix()) }
        val gitRepositoryCacheNotificationListener by createForEachTest { mock<GitRepositoryCacheNotificationListener>() }

        fun pathForGitInclude(repo: String, ref: String, path: String): Path = fileSystem.getPath("/git", repo, ref, path)

        val includeResolver by createForEachTest {
            mock<IncludeResolver> {
                on { resolve(any(), any()) } doAnswer { invocation ->
                    when (val include = invocation.arguments[0] as Include) {
                        is FileInclude -> include.path
                        is GitInclude -> pathForGitInclude(include.repo, include.ref, include.path)
                    }
                }

                on { rootPathFor(any(), any()) } doAnswer { invocation ->
                    val repositoryReference = invocation.arguments[0] as GitRepositoryReference

                    pathForGitInclude(repositoryReference.remote, repositoryReference.ref, "")
                }
            }
        }

        val pathResolverFactory by createForEachTest { PathResolverFactory(fileSystem) }
        val telemetryConsent by createForEachTest { mock<TelemetryConsent>() }
        val telemetryCaptor by createForEachTest { TestTelemetryCaptor() }
        val logger by createLoggerForEachTest()
        val testFileName = "/theTestFile.yml"
        val loader by createForEachTest { ConfigurationLoader(includeResolver, pathResolverFactory, telemetryCaptor, telemetryConsent, gitRepositoryCacheNotificationListener, logger) }

        fun createFile(path: Path, contents: String) {
            val directory = path.parent

            if (directory != null) {
                Files.createDirectories(directory)
            }

            Files.write(path, contents.toByteArray())
        }

        fun loadConfiguration(config: String, path: String = testFileName): ConfigurationLoadResult {
            val filePath = fileSystem.getPath(path)
            createFile(filePath, config)

            return loader.loadConfig(filePath, gitRepositoryCacheNotificationListener)
        }

        fun loadConfiguration(files: Map<Path, String>, rootConfig: Path): ConfigurationLoadResult {
            files.forEach { (path, contents) -> createFile(path, contents) }

            return loader.loadConfig(rootConfig, gitRepositoryCacheNotificationListener)
        }

        fun Suite.itReportsTelemetryAboutTheConfigurationFile(containerCount: Int = 0, taskCount: Int = 0, configVariableCount: Int = 0, fileIncludeCount: Int = 0, gitIncludeCount: Int = 0) {
            it("reports a telemetry span for loading the configuration file") {
                assertThat(telemetryCaptor.allSpans, hasSize(equalTo(1)))
            }

            val span by lazy { telemetryCaptor.allSpans.single() }

            it("reports the number of containers loaded") {
                assertThat(span.attributes["containerCount"], equalTo(JsonPrimitive(containerCount)))
            }

            it("reports the number of task loaded") {
                assertThat(span.attributes["taskCount"], equalTo(JsonPrimitive(taskCount)))
            }

            it("reports the number of config variables loaded") {
                assertThat(span.attributes["configVariableCount"], equalTo(JsonPrimitive(configVariableCount)))
            }

            it("reports the number of file includes loaded") {
                assertThat(span.attributes["fileIncludeCount"], equalTo(JsonPrimitive(fileIncludeCount)))
            }

            it("reports the number of Git includes loaded") {
                assertThat(span.attributes["gitIncludeCount"], equalTo(JsonPrimitive(gitIncludeCount)))
            }
        }

        on("loading an empty configuration file") {
            it("should fail with an error message") {
                assertThat({ loadConfiguration("") }, throws(withMessage("File contains no configuration.") and withFileName(testFileName)))
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
                val loadResult by runForEachTest { loadConfiguration(configString, path) }

                it("should use the parent directory's name as the project name") {
                    assertThat(loadResult.configuration.projectName, equalTo("project"))
                }

                it("reports that only the project file was loaded") {
                    assertThat(loadResult.pathsLoaded, equalTo(setOf(fileSystem.getPath(path))))
                }

                itReportsTelemetryAboutTheConfigurationFile(taskCount = 1)

                it("does not disable telemetry") {
                    verify(telemetryConsent).forbiddenByProjectConfig = false
                }
            }

            on("loading that file from a subdirectory") {
                val path = "/code/project/config.yml"
                val config by runForEachTest { loadConfiguration(configString, path).configuration }

                it("should use the parent directory's name as the project name") {
                    assertThat(config.projectName, equalTo("project"))
                }
            }

            on("loading that file from a directory containing uppercase letters") {
                val path = "/code/PROject/config.yml"
                val config by runForEachTest { loadConfiguration(configString, path).configuration }

                it("should use the lowercase version of the parent directory's name as the project name") {
                    assertThat(config.projectName, equalTo("project"))
                }
            }

            on("loading that file from a directory with a name that is not a valid project name") {
                val path = "/code/-project/config.yml"

                it("should fail with an error message") {
                    assertThat({ loadConfiguration(configString, path) }, throws(withMessage("The inferred project name '-project' is invalid. The project name must be a valid Docker reference: it must contain only lowercase letters, digits, dashes (-), single consecutive periods (.) or one or two consecutive underscores (_), and must not start or end with dashes, periods or underscores. Provide a valid project name explicitly with 'project_name'.") and withFileName(path)))
                }
            }
        }

        on("loading a valid configuration file with no containers or tasks defined") {
            val config by runForEachTest { loadConfiguration("project_name: the_cool_project").configuration }

            it("should return a populated configuration object with the project name specified") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            itReportsTelemetryAboutTheConfigurationFile()
        }

        on("loading a valid configuration file with telemetry forbidden") {
            val config = """
                |project_name: the_cool_project
                |forbid_telemetry: true
            """.trimMargin()

            beforeEachTest { loadConfiguration(config) }

            it("disables telemetry for the session") {
                verify(telemetryConsent).forbiddenByProjectConfig = true
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
                |      entrypoint: some-entrypoint
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
                |      working_directory: /some/dir
            """.trimMargin()

            val config by runForEachTest { loadConfiguration(configString).configuration }

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single task specified") {
                assertThat(config.tasks.keys, equalTo(setOf("first_task")))
            }

            it("should load the configuration for the task") {
                val task = config.tasks["first_task"]!!
                assertThat(task.name, equalTo("first_task"))
                assertThat(task.runConfiguration!!.container, equalTo("build-env"))
                assertThat(task.runConfiguration!!.command, equalTo(Command.parse("./gradlew doStuff")))
                assertThat(task.runConfiguration!!.entrypoint, equalTo(Command.parse("some-entrypoint")))
                assertThat(
                    task.runConfiguration!!.additionalEnvironmentVariables,
                    equalTo(
                        mapOf(
                            "OPTS" to LiteralValue("-Dthing"),
                            "INT_VALUE" to LiteralValue("1"),
                            "FLOAT_VALUE" to LiteralValue("12.6000"),
                            "BOOL_VALUE" to LiteralValue("true"),
                            "OTHER_VALUE" to LiteralValue("the value"),
                        ),
                    ),
                )
                assertThat(task.runConfiguration!!.additionalPortMappings, equalTo(setOf(PortMapping(123, 456), PortMapping(1000, 2000))))
                assertThat(task.runConfiguration!!.workingDiretory, equalTo("/some/dir"))
                assertThat(task.dependsOnContainers, isEmpty)
                assertThat(task.prerequisiteTasks, isEmpty)
                assertThat(task.description, isEmptyString)
            }

            itReportsTelemetryAboutTheConfigurationFile(taskCount = 1)
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

            val config by runForEachTest { loadConfiguration(configString).configuration }

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single task specified") {
                assertThat(config.tasks.keys, equalTo(setOf("first_task")))
            }

            it("should load the configuration for the task") {
                val task = config.tasks["first_task"]!!
                assertThat(task.name, equalTo("first_task"))
                assertThat(task.runConfiguration!!.container, equalTo("build-env"))
                assertThat(task.runConfiguration!!.command, absent())
                assertThat(task.runConfiguration!!.entrypoint, absent())
                assertThat(task.runConfiguration!!.workingDiretory, absent())
                assertThat(task.dependsOnContainers, isEmpty)
                assertThat(task.prerequisiteTasks, isEmpty)
                assertThat(task.description, isEmptyString)
            }

            itReportsTelemetryAboutTheConfigurationFile(taskCount = 1)
        }

        on("loading a valid configuration file with a task with a description and a group") {
            val configString = """
                |project_name: the_cool_project
                |
                |tasks:
                |  first_task:
                |    description: The very first task.
                |    group: Build tasks
                |    run:
                |      container: build-env
            """.trimMargin()

            val config by runForEachTest { loadConfiguration(configString).configuration }

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single task specified") {
                assertThat(config.tasks.keys, equalTo(setOf("first_task")))
            }

            it("should load the configuration for the task") {
                val task = config.tasks["first_task"]!!
                assertThat(task.name, equalTo("first_task"))
                assertThat(task.runConfiguration!!.container, equalTo("build-env"))
                assertThat(task.runConfiguration!!.command, absent())
                assertThat(task.runConfiguration!!.entrypoint, absent())
                assertThat(task.dependsOnContainers, isEmpty)
                assertThat(task.prerequisiteTasks, isEmpty)
                assertThat(task.description, equalTo("The very first task."))
                assertThat(task.group, equalTo("Build tasks"))
            }

            itReportsTelemetryAboutTheConfigurationFile(taskCount = 1)
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

            val config by runForEachTest { loadConfiguration(configString).configuration }

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single task specified") {
                assertThat(config.tasks.keys, equalTo(setOf("first_task")))
            }

            it("should load the configuration for the task") {
                val task = config.tasks["first_task"]!!
                assertThat(task.name, equalTo("first_task"))
                assertThat(task.runConfiguration!!.container, equalTo("build-env"))
                assertThat(task.runConfiguration!!.command, equalTo(Command.parse("./gradlew doStuff")))
                assertThat(task.dependsOnContainers, equalTo(setOf("dependency-1", "dependency-2")))
                assertThat(task.prerequisiteTasks, isEmpty)
                assertThat(task.description, isEmptyString)
            }

            itReportsTelemetryAboutTheConfigurationFile(taskCount = 1)
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

            val config by runForEachTest { loadConfiguration(configString).configuration }

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single task specified") {
                assertThat(config.tasks.keys, equalTo(setOf("first_task")))
            }

            it("should load the configuration for the task") {
                val task = config.tasks["first_task"]!!
                assertThat(task.name, equalTo("first_task"))
                assertThat(task.runConfiguration!!.container, equalTo("build-env"))
                assertThat(task.runConfiguration!!.command, equalTo(Command.parse("./gradlew doStuff")))
                assertThat(task.dependsOnContainers, isEmpty)
                assertThat(task.prerequisiteTasks, equalTo(listOf("other-task", "another-task")))
                assertThat(task.description, isEmptyString)
            }

            itReportsTelemetryAboutTheConfigurationFile(taskCount = 1)
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("The prerequisite 'dependency-1' is given more than once") and withLineNumber(9) and withFileName(testFileName) and withPath("tasks.first_task.prerequisites[1]")))
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

            val config by runForEachTest { loadConfiguration(configString).configuration }

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single container specified") {
                assertThat(config.containers.keys, equalTo(setOf("container-1")))
            }

            it("should load the build directory specified for the container") {
                val container = config.containers["container-1"]!!
                assertThat(container.name, equalTo("container-1"))
                assertThat(container.imageSource, equalTo(BuildImage(LiteralValue("container-1-build-dir"), DefaultPathResolutionContext(fileSystem.getPath("/")))))
            }

            itReportsTelemetryAboutTheConfigurationFile(containerCount = 1)
        }

        on("loading a valid configuration file with a container with just an image configured") {
            val configString = """
                    |project_name: the_cool_project
                    |
                    |containers:
                    |  container-1:
                    |    image: some-image:1.2.3
            """.trimMargin()

            val config by runForEachTest { loadConfiguration(configString).configuration }

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

            itReportsTelemetryAboutTheConfigurationFile(containerCount = 1)
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("One of either build_directory or image must be specified for each container, but neither have been provided for this container.") and withFileName(testFileName) and withLineNumber(5) and withPath("containers.container-1")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Only one of build_directory or image can be specified for a container, but both have been provided for this container.") and withFileName(testFileName) and withLineNumber(5) and withPath("containers.container-1")))
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
                    |    entrypoint: sh
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

            val config by runForEachTest { loadConfiguration(configString).configuration }

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single container specified") {
                assertThat(config.containers.keys, equalTo(setOf("container-1")))
            }

            it("should load all of the configuration specified for the container") {
                val container = config.containers["container-1"]!!
                assertThat(container.name, equalTo("container-1"))
                assertThat(container.imageSource, equalTo(BuildImage(LiteralValue("container-1-build-dir"), DefaultPathResolutionContext(fileSystem.getPath("/")))))
                assertThat(container.command, equalTo(Command.parse("do-the-thing.sh some-param")))
                assertThat(container.entrypoint, equalTo(Command.parse("sh")))
                assertThat(
                    container.environment,
                    equalTo(
                        mapOf(
                            "OPTS" to LiteralValue("-Dthing"),
                            "INT_VALUE" to LiteralValue("1"),
                            "FLOAT_VALUE" to LiteralValue("12.6000"),
                            "BOOL_VALUE" to LiteralValue("true"),
                            "OTHER_VALUE" to LiteralValue("the value"),
                        ),
                    ),
                )
                assertThat(container.workingDirectory, equalTo("/here"))
                assertThat(container.portMappings, equalTo(setOf(PortMapping(1234, 5678), PortMapping(9012, 3456))))
                assertThat(container.healthCheckConfig, equalTo(HealthCheckConfig(2.seconds, 10, 1.seconds)))
                assertThat(container.runAsCurrentUserConfig, equalTo(RunAsCurrentUserConfig.RunAsCurrentUser("/home/something")))
                assertThat(
                    container.volumeMounts,
                    equalTo(
                        setOf(
                            LocalMount(LiteralValue("../"), DefaultPathResolutionContext(fileSystem.getPath("/")), "/here", null),
                            LocalMount(LiteralValue("/somewhere"), DefaultPathResolutionContext(fileSystem.getPath("/")), "/else", "ro"),
                        ),
                    ),
                )
            }

            itReportsTelemetryAboutTheConfigurationFile(containerCount = 1)
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

            val config by runForEachTest { loadConfiguration(configString).configuration }

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single container specified") {
                assertThat(config.containers.keys, equalTo(setOf("container-1")))
            }

            it("should load all of the configuration specified for the container") {
                val container = config.containers["container-1"]!!
                assertThat(container.name, equalTo("container-1"))
                assertThat(container.imageSource, equalTo(BuildImage(LiteralValue("container-1-build-dir"), DefaultPathResolutionContext(fileSystem.getPath("/")))))
                assertThat(
                    container.volumeMounts,
                    equalTo(
                        setOf(
                            LocalMount(LiteralValue("../"), DefaultPathResolutionContext(fileSystem.getPath("/")), "/here", null),
                            LocalMount(LiteralValue("/somewhere"), DefaultPathResolutionContext(fileSystem.getPath("/")), "/else", "ro"),
                        ),
                    ),
                )
            }

            itReportsTelemetryAboutTheConfigurationFile(containerCount = 1)
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

            val config by runForEachTest { loadConfiguration(configString).configuration }

            it("should load the project name") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single container specified") {
                assertThat(config.containers.keys, equalTo(setOf("container-1")))
            }

            it("should load all of the configuration specified for the container") {
                val container = config.containers["container-1"]!!
                assertThat(container.name, equalTo("container-1"))
                assertThat(container.imageSource, equalTo(BuildImage(LiteralValue("container-1-build-dir"), DefaultPathResolutionContext(fileSystem.getPath("/")))))
                assertThat(container.portMappings, equalTo(setOf(PortMapping(1234, 5678), PortMapping(9012, 3456))))
            }

            itReportsTelemetryAboutTheConfigurationFile(containerCount = 1)
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

            val config by runForEachTest { loadConfiguration(configString).configuration }

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

            itReportsTelemetryAboutTheConfigurationFile(containerCount = 1)
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("The dependency 'container-2' is given more than once") and withLineNumber(8) and withFileName(testFileName) and withPath("containers.container-1.dependencies[1]")))
            }
        }

        on("loading a valid configuration file with a config variable with no optional fields specified") {
            val configString = """
                    |project_name: the_cool_project
                    |
                    |config_variables:
                    |  my-config-var: {}
            """.trimMargin()

            val config by runForEachTest { loadConfiguration(configString).configuration }

            it("should load the config variable specified") {
                val configVariable = config.configVariables.getValue("my-config-var")
                assertThat(configVariable, equalTo(ConfigVariableDefinition("my-config-var", null, null)))
            }

            itReportsTelemetryAboutTheConfigurationFile(configVariableCount = 1)
        }

        on("loading a valid configuration file with a config variable with all optional fields specified") {
            val configString = """
                    |project_name: the_cool_project
                    |
                    |config_variables:
                    |  my-config-var:
                    |    description: This is my config variable
                    |    default: my-default-value
            """.trimMargin()

            val config by runForEachTest { loadConfiguration(configString).configuration }

            it("should load the config variable specified") {
                val configVariable = config.configVariables.getValue("my-config-var")
                assertThat(configVariable, equalTo(ConfigVariableDefinition("my-config-var", "This is my config variable", "my-default-value")))
            }

            itReportsTelemetryAboutTheConfigurationFile(configVariableCount = 1)
        }

        on("loading a configuration file where a config variable is defined twice") {
            val config = """
                    |project_name: the_cool_project
                    |
                    |config_variables:
                    |  my-config-var: {}
                    |  my-config-var: {}
            """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Duplicate key 'my-config-var'. It was previously given at line 4, column 3.") and withLineNumber(5) and withFileName(testFileName) and withPath("config_variables.my-config-var")))
            }
        }

        on("loading a configuration file where the project name is given twice") {
            val config = """
                |project_name: the_cool_project
                |project_name: the_really_cool_project
            """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Duplicate key 'project_name'. It was previously given at line 1, column 1.") and withLineNumber(2) and withFileName(testFileName) and withPath("project_name")))
            }
        }

        on("loading a configuration file where an unknown field name is used") {
            val config = """
                |project_name: the_cool_project
                |thing: value
            """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Unknown property 'thing'. Known properties are: config_variables, containers, forbid_telemetry, include, project_name, tasks") and withLineNumber(2) and withFileName(testFileName) and withPath("thing")))
            }
        }

        on("loading a configuration file with a full-line comment") {
            val configString = """
                |# This is a comment
                |project_name: the_cool_project
            """.trimMargin()

            val config by runForEachTest { loadConfiguration(configString).configuration }

            it("should ignore the comment") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }
        }

        on("loading a configuration file with an end-of-line comment") {
            val config by runForEachTest { loadConfiguration("project_name: the_cool_project # This is a comment").configuration }

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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Duplicate key 'first_task'. It was previously given at line 4, column 3.") and withLineNumber(7) and withFileName(testFileName) and withPath("tasks.first_task")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Duplicate key 'container-1'. It was previously given at line 4, column 3.") and withLineNumber(6) and withFileName(testFileName) and withPath("containers.container-1")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Duplicate key 'THING'. It was previously given at line 7, column 7.") and withLineNumber(8) and withFileName(testFileName) and withPath("containers.container-1.environment.THING")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Duplicate key 'THING'. It was previously given at line 8, column 9.") and withLineNumber(9) and withFileName(testFileName) and withPath("tasks.the-task.run.environment.THING")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Value for 'THING' is invalid: Unexpected null or empty value for non-null field.") and withLineNumber(7) and withFileName(testFileName) and withPath("containers.container-1.environment.THING")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Value for 'THING' is invalid: Unexpected null or empty value for non-null field.") and withLineNumber(8) and withFileName(testFileName) and withPath("tasks.the-task.run.environment.THING")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Running as the current user has not been enabled, but a home directory for that user has been provided.") and withFileName(testFileName) and withLineNumber(7) and withPath("containers.container-1.run_as_current_user")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Running as the current user has been enabled, but a home directory for that user has not been provided.") and withFileName(testFileName) and withLineNumber(7) and withPath("containers.container-1.run_as_current_user")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Running as the current user has not been enabled, but a home directory for that user has been provided.") and withFileName(testFileName) and withLineNumber(7) and withPath("containers.container-1.run_as_current_user")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Value for 'container-2' is invalid: Unexpected null or empty value for non-null field.") and withLineNumber(6) and withFileName(testFileName) and withPath("containers.container-2")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Value for 'task-1' is invalid: Unexpected null or empty value for non-null field.") and withLineNumber(7) and withFileName(testFileName) and withPath("tasks.task-1")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Port range 'abc123' is invalid. It must be in the form 'port' or 'from-to' and each port must be a positive integer.") and withLineNumber(7) and withFileName(testFileName) and withPath("containers.container-1.ports[0].local")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Port range 'abc123' is invalid. It must be in the form 'port' or 'from-to' and each port must be a positive integer.") and withLineNumber(8) and withFileName(testFileName) and withPath("containers.container-1.ports[0].container")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Port mapping definition 'abc123:1000' is invalid. It must be in the form 'local:container', 'local:container/protocol', 'from-to:from-to' or 'from-to:from-to/protocol' and each port must be a positive integer.") and withLineNumber(7) and withFileName(testFileName) and withPath("containers.container-1.ports[0]")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Port range 'abc123' is invalid. It must be in the form 'port' or 'from-to' and each port must be a positive integer.") and withLineNumber(8) and withFileName(testFileName) and withPath("tasks.task-1.run.ports[0].local")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Port range 'abc123' is invalid. It must be in the form 'port' or 'from-to' and each port must be a positive integer.") and withLineNumber(9) and withFileName(testFileName) and withPath("tasks.task-1.run.ports[0].container")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Port mapping definition 'abc123:1000' is invalid. It must be in the form 'local:container', 'local:container/protocol', 'from-to:from-to' or 'from-to:from-to/protocol' and each port must be a positive integer.") and withLineNumber(8) and withFileName(testFileName) and withPath("tasks.task-1.run.ports[0]")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Command `'` is invalid: it contains an unbalanced single quote") and withLineNumber(7) and withFileName(testFileName) and withPath("tasks.the-task.run.command")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Command `'` is invalid: it contains an unbalanced single quote") and withLineNumber(6) and withFileName(testFileName) and withPath("containers.container-1.command")))
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
                assertThat({ loadConfiguration(configString) }, throws(withMessage("Unknown property 'something_else'. Known properties are: container, local, name, options, type") and withLineNumber(7) and withFileName(testFileName) and withPath("containers.container-1.volumes[0].something_else")))
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
                assertThat({ loadConfiguration(configString) }, throws(withMessage("Property 'container' is required but it is missing.") and withLineNumber(5) and withFileName(testFileName) and withPath("containers.container-1.ports[0]")))
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
                assertThat({ loadConfiguration(configString) }, throws(withMessage("Property 'local' is required but it is missing.") and withLineNumber(5) and withFileName(testFileName) and withPath("containers.container-1.ports[0]")))
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
                assertThat({ loadConfiguration(configString) }, throws(withMessage("Unknown property 'something_else'. Known properties are: container, local, protocol") and withLineNumber(7) and withFileName(testFileName) and withPath("containers.container-1.ports[0].something_else")))
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
                assertThat({ loadConfiguration(configString) }, throws(withMessage("Property 'container' is required but it is missing.") and withLineNumber(6) and withFileName(testFileName) and withPath("tasks.task-1.run.ports[0]")))
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
                assertThat({ loadConfiguration(configString) }, throws(withMessage("Property 'local' is required but it is missing.") and withLineNumber(6) and withFileName(testFileName) and withPath("tasks.task-1.run.ports[0]")))
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
                assertThat({ loadConfiguration(configString) }, throws(withMessage("Unknown property 'something_else'. Known properties are: container, local, protocol") and withLineNumber(8) and withFileName(testFileName) and withPath("tasks.task-1.run.ports[0].something_else")))
            }
        }

        on("loading a configuration file with an extension defined") {
            val configString = """
                .name: &name the_cool_project

                project_name: *name
            """.trimIndent()

            val config by runForEachTest { loadConfiguration(configString).configuration }

            it("should return a populated configuration object with the extension value used where referenced") {
                assertThat(config.projectName, equalTo("the_cool_project"))
            }
        }

        on("loading a configuration file with an invalid container name") {
            val configString = """
                |containers:
                |  -invalid:
                |       image: some-image:1.2.3
            """.trimMargin()

            it("should fail with an error message") {
                assertThat(
                    { loadConfiguration(configString) },
                    throws(
                        withMessage("Invalid container name '-invalid'. Container names must be valid Docker references: they must contain only lowercase letters, digits, dashes (-), single consecutive periods (.) or one or two consecutive underscores (_), and must not start or end with dashes, periods or underscores.")
                            and withLineNumber(2)
                            and withColumn(3)
                            and withFileName(testFileName)
                            and withPath("containers.-invalid"),
                    ),
                )
            }
        }

        on("loading a configuration file with an invalid project name") {
            val configString = """
                |project_name: -invalid
            """.trimMargin()

            it("should fail with an error message") {
                assertThat(
                    { loadConfiguration(configString) },
                    throws(
                        withMessage("Invalid project name '-invalid'. The project name must be a valid Docker reference: it must contain only lowercase letters, digits, dashes (-), single consecutive periods (.) or one or two consecutive underscores (_), and must not start or end with dashes, periods or underscores.")
                            and withLineNumber(1)
                            and withColumn(15)
                            and withFileName(testFileName)
                            and withPath("project_name"),
                    ),
                )
            }
        }

        on("loading a configuration file with an include for a file that does not exist") {
            val configString = """
                |include:
                | - does_not_exist.yml
            """.trimMargin()

            it("should fail with an error message") {
                assertThat(
                    { loadConfiguration(configString) },
                    throws(
                        withMessage("Included file 'does_not_exist.yml' (resolved to '/does_not_exist.yml') does not exist.")
                            and withLineNumber(2)
                            and withColumn(4)
                            and withFileName(testFileName)
                            and withPath("include[0]"),
                    ),
                )
            }
        }

        on("loading a configuration file that references other configuration files") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }
            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - 1.yml
                        | - 2.yml
                        | - 3.yml
                    """.trimMargin(),
                    fileSystem.getPath("/project/1.yml") to """
                        |tasks:
                        |  task-1:
                        |    run:
                        |      container: container-1
                    """.trimMargin(),
                    fileSystem.getPath("/project/2.yml") to """
                        |containers:
                        |  container-1:
                        |    image: alpine:1.2.3
                    """.trimMargin(),
                    fileSystem.getPath("/project/3.yml") to """
                        |config_variables:
                        |  config-var-1: {}
                    """.trimMargin(),
                )
            }

            val loadResult by runForEachTest { loadConfiguration(files, rootConfigPath) }
            val config by runForEachTest { loadResult.configuration }

            it("should merge the tasks from the referenced files") {
                assertThat(config.tasks, equalTo(TaskMap(Task("task-1", TaskRunConfiguration("container-1")))))
            }

            it("should merge the containers from the referenced files") {
                assertThat(config.containers, equalTo(ContainerMap(Container("container-1", PullImage("alpine:1.2.3")))))
            }

            it("should merge the config variables from the referenced files") {
                assertThat(config.configVariables, equalTo(ConfigVariableMap(ConfigVariableDefinition("config-var-1"))))
            }

            it("should infer the project name based on the root configuration file's directory") {
                assertThat(config.projectName, equalTo("project"))
            }

            it("should return the set of all files loaded") {
                assertThat(
                    loadResult.pathsLoaded,
                    equalTo(
                        setOf(
                            rootConfigPath,
                            fileSystem.getPath("/project/1.yml"),
                            fileSystem.getPath("/project/2.yml"),
                            fileSystem.getPath("/project/3.yml"),
                        ),
                    ),
                )
            }

            itReportsTelemetryAboutTheConfigurationFile(taskCount = 1, containerCount = 1, configVariableCount = 1, fileIncludeCount = 3)
        }

        on("loading a configuration file that references another configuration file in expanded format") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }
            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - type: file
                        |   path: 1.yml
                    """.trimMargin(),
                    fileSystem.getPath("/project/1.yml") to """
                        |tasks:
                        |  task-1:
                        |    run:
                        |      container: container-1
                        |
                        |containers:
                        |  container-1:
                        |    image: alpine:1.2.3
                        |
                        |config_variables:
                        |  config-var-1: {}
                    """.trimMargin(),
                )
            }

            val config by runForEachTest { loadConfiguration(files, rootConfigPath).configuration }

            it("should merge the tasks from the referenced files") {
                assertThat(config.tasks, equalTo(TaskMap(Task("task-1", TaskRunConfiguration("container-1")))))
            }

            it("should merge the containers from the referenced files") {
                assertThat(config.containers, equalTo(ContainerMap(Container("container-1", PullImage("alpine:1.2.3")))))
            }

            it("should merge the config variables from the referenced files") {
                assertThat(config.configVariables, equalTo(ConfigVariableMap(ConfigVariableDefinition("config-var-1"))))
            }

            it("should infer the project name based on the root configuration file's directory") {
                assertThat(config.projectName, equalTo("project"))
            }

            itReportsTelemetryAboutTheConfigurationFile(taskCount = 1, containerCount = 1, configVariableCount = 1, fileIncludeCount = 1)
        }

        on("loading a configuration file that references another configuration file which itself references another configuration file") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }
            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - 1.yml
                    """.trimMargin(),
                    fileSystem.getPath("/project/1.yml") to """
                        |tasks:
                        |  task-1:
                        |    run:
                        |      container: container-1
                        |
                        |include:
                        | - 2.yml
                    """.trimMargin(),
                    fileSystem.getPath("/project/2.yml") to """
                        |containers:
                        |  container-1:
                        |    image: alpine:1.2.3
                    """.trimMargin(),
                )
            }

            val config by runForEachTest { loadConfiguration(files, rootConfigPath).configuration }

            it("should merge the tasks from the referenced files") {
                assertThat(config.tasks, equalTo(TaskMap(Task("task-1", TaskRunConfiguration("container-1")))))
            }

            it("should merge the containers from the referenced files") {
                assertThat(config.containers, equalTo(ContainerMap(Container("container-1", PullImage("alpine:1.2.3")))))
            }

            it("should infer the project name based on the root configuration file's directory") {
                assertThat(config.projectName, equalTo("project"))
            }

            itReportsTelemetryAboutTheConfigurationFile(taskCount = 1, containerCount = 1, fileIncludeCount = 2)
        }

        on("loading a configuration file that contains configuration and a reference to another configuration file") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }
            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |tasks:
                        |  task-1:
                        |    run:
                        |      container: container-1
                        |
                        |containers:
                        |  container-1:
                        |    image: alpine:1.2.3
                        |
                        |config_variables:
                        |  config-var-1: {}
                        |
                        |include:
                        | - 1.yml
                    """.trimMargin(),
                    fileSystem.getPath("/project/1.yml") to """
                        |tasks:
                        |  task-2:
                        |    run:
                        |      container: container-2
                        |
                        |containers:
                        |  container-2:
                        |    image: alpine:4.5.6
                        |
                        |config_variables:
                        |  config-var-2: {}
                    """.trimMargin(),
                )
            }

            val config by runForEachTest { loadConfiguration(files, rootConfigPath).configuration }

            it("should merge the tasks from the referenced file with the tasks in the root configuration file") {
                assertThat(
                    config.tasks,
                    equalTo(
                        TaskMap(
                            Task("task-1", TaskRunConfiguration("container-1")),
                            Task("task-2", TaskRunConfiguration("container-2")),
                        ),
                    ),
                )
            }

            it("should merge the containers from the referenced file with the containers in the root configuration file") {
                assertThat(
                    config.containers,
                    equalTo(
                        ContainerMap(
                            Container("container-1", PullImage("alpine:1.2.3")),
                            Container("container-2", PullImage("alpine:4.5.6")),
                        ),
                    ),
                )
            }

            it("should merge the config variables from the referenced file with the config variables in the root configuration file") {
                assertThat(
                    config.configVariables,
                    equalTo(
                        ConfigVariableMap(
                            ConfigVariableDefinition("config-var-1"),
                            ConfigVariableDefinition("config-var-2"),
                        ),
                    ),
                )
            }

            it("should infer the project name based on the root configuration file's directory") {
                assertThat(config.projectName, equalTo("project"))
            }

            itReportsTelemetryAboutTheConfigurationFile(taskCount = 2, containerCount = 2, configVariableCount = 2, fileIncludeCount = 1)
        }

        on("loading a configuration file that contains an explicit project name and references another configuration file") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }
            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |project_name: some_project
                        |include:
                        | - 1.yml
                    """.trimMargin(),
                    fileSystem.getPath("/project/1.yml") to """
                        |containers: {}
                    """.trimMargin(),
                )
            }

            val config by runForEachTest { loadConfiguration(files, rootConfigPath).configuration }

            it("should use the provided project name") {
                assertThat(config.projectName, equalTo("some_project"))
            }
        }

        on("loading a configuration file that references another configuration file which contains an explicit project name") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }
            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - 1.yml
                    """.trimMargin(),
                    fileSystem.getPath("/project/1.yml") to """
                        |project_name: some_project
                    """.trimMargin(),
                )
            }

            it("should fail with an error message") {
                assertThat({ loadConfiguration(files, rootConfigPath) }, throws(withMessage("Only the root configuration file can contain the project name, but this file has a project name.") and withFileName("/project/1.yml")))
            }
        }

        on("loading a configuration file that references another configuration file which forbids telemetry") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }
            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - 1.yml
                    """.trimMargin(),
                    fileSystem.getPath("/project/1.yml") to """
                        |forbid_telemetry: true
                    """.trimMargin(),
                )
            }

            it("should fail with an error message") {
                assertThat({ loadConfiguration(files, rootConfigPath) }, throws(withMessage("Only the root configuration file can forbid telemetry, but this file forbids telemetry.") and withFileName("/project/1.yml")))
            }
        }

        on("loading a configuration file that contains a include dependency cycle") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/1.yml") }
            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |config_variables:
                        |  config-var-1: {}
                        |
                        |include:
                        | - 2.yml
                    """.trimMargin(),
                    fileSystem.getPath("/project/2.yml") to """
                        |config_variables:
                        |  config-var-2: {}
                        |
                        |include:
                        | - 1.yml
                    """.trimMargin(),
                )
            }

            val config by runForEachTest { loadConfiguration(files, rootConfigPath).configuration }

            it("should load and merge the configuration from both files without error") {
                assertThat(
                    config.configVariables,
                    equalTo(
                        ConfigVariableMap(
                            ConfigVariableDefinition("config-var-1"),
                            ConfigVariableDefinition("config-var-2"),
                        ),
                    ),
                )
            }

            itReportsTelemetryAboutTheConfigurationFile(configVariableCount = 2, fileIncludeCount = 1)
        }

        on("loading a configuration file that references another configuration file both of which contain a definition for the same container") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }
            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |containers:
                        |  container-1:
                        |    image: alpine:1.2.3
                        |
                        |include:
                        | - 1.yml
                    """.trimMargin(),
                    fileSystem.getPath("/project/1.yml") to """
                        |containers:
                        |  container-1:
                        |    image: alpine:4.5.6
                    """.trimMargin(),
                )
            }

            it("should fail with an error message") {
                assertThat({ loadConfiguration(files, rootConfigPath) }, throws(withMessage("The container 'container-1' is defined in multiple files: /project/batect.yml and /project/1.yml")))
            }
        }

        on("loading a configuration file that references another configuration file both of which contain a definition for the same task") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }
            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |tasks:
                        |  task-1:
                        |    run:
                        |      container: container-1
                        |
                        |include:
                        | - 1.yml
                    """.trimMargin(),
                    fileSystem.getPath("/project/1.yml") to """
                        |tasks:
                        |  task-1:
                        |    run:
                        |      container: container-2
                    """.trimMargin(),
                )
            }

            it("should fail with an error message") {
                assertThat({ loadConfiguration(files, rootConfigPath) }, throws(withMessage("The task 'task-1' is defined in multiple files: /project/batect.yml and /project/1.yml")))
            }
        }

        on("loading a configuration file that references another configuration file both of which contain a definition for the same config variable") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }
            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |config_variables:
                        |  config-var-1: {}
                        |
                        |include:
                        | - 1.yml
                    """.trimMargin(),
                    fileSystem.getPath("/project/1.yml") to """
                        |config_variables:
                        |  config-var-1: {}
                    """.trimMargin(),
                )
            }

            it("should fail with an error message") {
                assertThat({ loadConfiguration(files, rootConfigPath) }, throws(withMessage("The config variable 'config-var-1' is defined in multiple files: /project/batect.yml and /project/1.yml")))
            }
        }

        on("loading a configuration file that references another configuration file which contains relative paths") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }

            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - includes/1.yml
                    """.trimMargin(),
                    fileSystem.getPath("/project/includes/1.yml") to """
                        |containers:
                        |  container-1:
                        |    build_directory: some_build_directory
                        |    volumes:
                        |      - local: some_mount_directory
                        |        container: /mount
                    """.trimMargin(),
                )
            }

            val config by runForEachTest { loadConfiguration(files, rootConfigPath).configuration }

            it("should resolve paths in the included file relative to the included file, not the root file") {
                assertThat(
                    config.containers.getValue("container-1"),
                    equalTo(
                        Container(
                            "container-1",
                            BuildImage(LiteralValue("some_build_directory"), DefaultPathResolutionContext(fileSystem.getPath("/project/includes"))),
                            volumeMounts = setOf(
                                LocalMount(LiteralValue("some_mount_directory"), DefaultPathResolutionContext(fileSystem.getPath("/project/includes")), "/mount"),
                            ),
                        ),
                    ),
                )
            }
        }

        on("loading a configuration file that references another configuration file from a Git repository") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }

            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: 1.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "1.yml") to """
                        |tasks:
                        |  task-1:
                        |    run:
                        |      container: container-1
                        |
                        |containers:
                        |  container-1:
                        |    image: alpine:1.2.3
                        |
                        |config_variables:
                        |  config-var-1: {}
                    """.trimMargin(),
                )
            }

            val loadResult by runForEachTest { loadConfiguration(files, rootConfigPath) }
            val config by runForEachTest { loadResult.configuration }

            it("should merge the tasks from the referenced files") {
                assertThat(config.tasks, equalTo(TaskMap(Task("task-1", TaskRunConfiguration("container-1")))))
            }

            it("should merge the containers from the referenced files") {
                assertThat(config.containers, equalTo(ContainerMap(Container("container-1", PullImage("alpine:1.2.3")))))
            }

            it("should merge the config variables from the referenced files") {
                assertThat(config.configVariables, equalTo(ConfigVariableMap(ConfigVariableDefinition("config-var-1"))))
            }

            it("should infer the project name based on the root configuration file's directory") {
                assertThat(config.projectName, equalTo("project"))
            }

            it("should report all of the files that were loaded") {
                assertThat(
                    loadResult.pathsLoaded,
                    equalTo(
                        setOf(
                            rootConfigPath,
                            pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "1.yml"),
                        ),
                    ),
                )
            }

            itReportsTelemetryAboutTheConfigurationFile(taskCount = 1, containerCount = 1, configVariableCount = 1, gitIncludeCount = 1)
        }

        on("loading a configuration file that references another configuration file from a Git repository that also references another file from the same repository in the same directory") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }

            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: 1.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "1.yml") to """
                        |tasks:
                        |  task-1:
                        |    run:
                        |      container: container-1
                        |
                        |include:
                        | - 2.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "2.yml") to """
                        |containers:
                        |  container-1:
                        |    image: alpine:1.2.3
                        |
                        |config_variables:
                        |  config-var-1: {}
                    """.trimMargin(),
                )
            }

            val config by runForEachTest { loadConfiguration(files, rootConfigPath).configuration }

            it("should merge the tasks from the referenced files") {
                assertThat(config.tasks, equalTo(TaskMap(Task("task-1", TaskRunConfiguration("container-1")))))
            }

            it("should merge the containers from the referenced files") {
                assertThat(config.containers, equalTo(ContainerMap(Container("container-1", PullImage("alpine:1.2.3")))))
            }

            it("should merge the config variables from the referenced files") {
                assertThat(config.configVariables, equalTo(ConfigVariableMap(ConfigVariableDefinition("config-var-1"))))
            }

            it("should infer the project name based on the root configuration file's directory") {
                assertThat(config.projectName, equalTo("project"))
            }

            itReportsTelemetryAboutTheConfigurationFile(taskCount = 1, containerCount = 1, configVariableCount = 1, gitIncludeCount = 2)
        }

        on("loading a configuration file that references another configuration file from a Git repository that also references another file from a different repository") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }

            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: 1.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "1.yml") to """
                        |tasks:
                        |  task-1:
                        |    run:
                        |      container: container-1
                        |
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/other-bundle.git
                        |   ref: v1.2.3
                        |   path: 2.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/other-bundle.git", "v1.2.3", "2.yml") to """
                        |containers:
                        |  container-1:
                        |    image: alpine:1.2.3
                        |
                        |config_variables:
                        |  config-var-1: {}
                    """.trimMargin(),
                )
            }

            val config by runForEachTest { loadConfiguration(files, rootConfigPath).configuration }

            it("should merge the tasks from the referenced files") {
                assertThat(config.tasks, equalTo(TaskMap(Task("task-1", TaskRunConfiguration("container-1")))))
            }

            it("should merge the containers from the referenced files") {
                assertThat(config.containers, equalTo(ContainerMap(Container("container-1", PullImage("alpine:1.2.3")))))
            }

            it("should merge the config variables from the referenced files") {
                assertThat(config.configVariables, equalTo(ConfigVariableMap(ConfigVariableDefinition("config-var-1"))))
            }

            it("should infer the project name based on the root configuration file's directory") {
                assertThat(config.projectName, equalTo("project"))
            }

            itReportsTelemetryAboutTheConfigurationFile(taskCount = 1, containerCount = 1, configVariableCount = 1, gitIncludeCount = 2)
        }

        on("loading a configuration file that references another configuration file from a Git repository that contains an error") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }

            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: 1.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "1.yml") to """
                        |blah: thing
                    """.trimMargin(),
                )
            }

            it("should fail with an error message with the user-facing configuration file path") {
                assertThat(
                    { loadConfiguration(files, rootConfigPath) },
                    throws(
                        withLineNumber(1) and withColumn(1) and withFileName("https://myrepo.com/bundles/bundle.git@v1.2.3: 1.yml"),
                    ),
                )
            }
        }

        on("loading a configuration file that references another configuration file from a Git repository that also references another file from the same repository in the same directory that contains an error") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }

            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: 1.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "1.yml") to """
                        |include:
                        | - 2.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "2.yml") to """
                        |blah: blah
                    """.trimMargin(),
                )
            }

            it("should fail with an error message with the user-facing configuration file path") {
                assertThat(
                    { loadConfiguration(files, rootConfigPath) },
                    throws(
                        withMessage("Unknown property 'blah'. Known properties are: config_variables, containers, forbid_telemetry, include, project_name, tasks") and
                            withFileName("https://myrepo.com/bundles/bundle.git@v1.2.3: 2.yml"),
                    ),
                )
            }
        }

        on("loading a configuration file that references another configuration file from a Git repository that also references another file from the same repository in a different directory that contains an error") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }

            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: 1.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "1.yml") to """
                        |include:
                        | - thing/2.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "thing/2.yml") to """
                        |blah: blah
                    """.trimMargin(),
                )
            }

            it("should fail with an error message with the user-facing configuration file path") {
                assertThat(
                    { loadConfiguration(files, rootConfigPath) },
                    throws(
                        withMessage("Unknown property 'blah'. Known properties are: config_variables, containers, forbid_telemetry, include, project_name, tasks") and
                            withFileName("https://myrepo.com/bundles/bundle.git@v1.2.3: thing/2.yml"),
                    ),
                )
            }
        }

        on("loading a configuration file that references another configuration file from a Git repository that also references another file from the same repository in a different directory with a relative path that contains an error") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }

            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: things/1.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "things/1.yml") to """
                        |include:
                        | - ../otherthings/2.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "otherthings/2.yml") to """
                        |blah: blah
                    """.trimMargin(),
                )
            }

            it("should fail with an error message with the user-facing configuration file path") {
                assertThat(
                    { loadConfiguration(files, rootConfigPath) },
                    throws(
                        withMessage("Unknown property 'blah'. Known properties are: config_variables, containers, forbid_telemetry, include, project_name, tasks") and
                            withFileName("https://myrepo.com/bundles/bundle.git@v1.2.3: otherthings/2.yml"),
                    ),
                )
            }
        }

        on("loading a configuration file that references another configuration file from a Git repository that contains a project name") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }

            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: 1.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "1.yml") to """
                        |project_name: not-allowed
                    """.trimMargin(),
                )
            }

            it("should fail with an error message with the user-facing configuration file path") {
                assertThat(
                    { loadConfiguration(files, rootConfigPath) },
                    throws(
                        withMessage("Only the root configuration file can contain the project name, but this file has a project name.") and
                            withFileName("https://myrepo.com/bundles/bundle.git@v1.2.3: 1.yml"),
                    ),
                )
            }
        }

        on("loading a configuration file that references another configuration file from a Git repository that also references another file from the same repository that contains a project name") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }

            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: 1.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "1.yml") to """
                        |include:
                        | - 2.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "2.yml") to """
                        |project_name: blah
                    """.trimMargin(),
                )
            }

            it("should fail with an error message with the user-facing configuration file path") {
                assertThat(
                    { loadConfiguration(files, rootConfigPath) },
                    throws(
                        withMessage("Only the root configuration file can contain the project name, but this file has a project name.") and
                            withFileName("https://myrepo.com/bundles/bundle.git@v1.2.3: 2.yml"),
                    ),
                )
            }
        }

        on("loading a configuration file that references another configuration file from a Git repository that forbids telemetry") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }

            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: 1.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "1.yml") to """
                        |forbid_telemetry: true
                    """.trimMargin(),
                )
            }

            it("should fail with an error message with the user-facing configuration file path") {
                assertThat(
                    { loadConfiguration(files, rootConfigPath) },
                    throws(
                        withMessage("Only the root configuration file can forbid telemetry, but this file forbids telemetry.") and
                            withFileName("https://myrepo.com/bundles/bundle.git@v1.2.3: 1.yml"),
                    ),
                )
            }
        }

        on("loading a configuration file that references another configuration file from a Git repository that also references another file from the same repository that forbids telemetry") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }

            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: 1.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "1.yml") to """
                        |include:
                        | - 2.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "2.yml") to """
                        |forbid_telemetry: true
                    """.trimMargin(),
                )
            }

            it("should fail with an error message with the user-facing configuration file path") {
                assertThat(
                    { loadConfiguration(files, rootConfigPath) },
                    throws(
                        withMessage("Only the root configuration file can forbid telemetry, but this file forbids telemetry.") and
                            withFileName("https://myrepo.com/bundles/bundle.git@v1.2.3: 2.yml"),
                    ),
                )
            }
        }

        on("loading a configuration file that references another configuration file from a Git repository that does not exist") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }

            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: something.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "somethingelse.yml") to """
                        |containers: []
                    """.trimMargin(),
                )
            }

            it("should fail with an error message with the user-facing configuration file path") {
                assertThat(
                    { loadConfiguration(files, rootConfigPath) },
                    throws(
                        withMessage("Included file 'something.yml' (something.yml from https://myrepo.com/bundles/bundle.git@v1.2.3) does not exist.") and
                            withFileName("/project/batect.yml"),
                    ),
                )
            }
        }

        on("loading a configuration file that references another configuration file from the root of a Git repository that also references another file from the same repository that does not exist") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }

            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: 1.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "1.yml") to """
                        |include:
                        | - 2.yml
                    """.trimMargin(),
                )
            }

            it("should fail with an error message with the user-facing configuration file path") {
                assertThat(
                    { loadConfiguration(files, rootConfigPath) },
                    throws(
                        withMessage("Included file '2.yml' ('2.yml' from https://myrepo.com/bundles/bundle.git@v1.2.3) does not exist.") and
                            withFileName("https://myrepo.com/bundles/bundle.git@v1.2.3: 1.yml") and
                            withLineNumber(2) and
                            withColumn(4) and
                            withPath("include[0]"),
                    ),
                )
            }
        }

        on("loading a configuration file that references another configuration file from a subdirectory of a Git repository that also references another file from the same repository that does not exist") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }

            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: things/1.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "things/1.yml") to """
                        |include:
                        | - ../2.yml
                    """.trimMargin(),
                )
            }

            it("should fail with an error message with the user-facing configuration file path") {
                assertThat(
                    { loadConfiguration(files, rootConfigPath) },
                    throws(
                        withMessage("Included file '../2.yml' ('2.yml' from https://myrepo.com/bundles/bundle.git@v1.2.3) does not exist.") and
                            withFileName("https://myrepo.com/bundles/bundle.git@v1.2.3: things/1.yml") and
                            withLineNumber(2) and
                            withColumn(4) and
                            withPath("include[0]"),
                    ),
                )
            }
        }

        on("loading a configuration file that references another configuration file in a Git repository both of which contain a definition for the same container") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }
            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |containers:
                        |  container-1:
                        |    image: alpine:1.2.3
                        |
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: 1.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "1.yml") to """
                        |containers:
                        |  container-1:
                        |    image: alpine:4.5.6
                    """.trimMargin(),
                )
            }

            it("should fail with an error message") {
                assertThat({ loadConfiguration(files, rootConfigPath) }, throws(withMessage("The container 'container-1' is defined in multiple files: /project/batect.yml and 1.yml from https://myrepo.com/bundles/bundle.git@v1.2.3")))
            }
        }

        on("loading a configuration file that references another configuration file in a Git repository both of which contain a definition for the same task") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }
            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |tasks:
                        |  task-1:
                        |    run:
                        |      container: container-1
                        |
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: 1.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "1.yml") to """
                        |tasks:
                        |  task-1:
                        |    run:
                        |      container: container-2
                    """.trimMargin(),
                )
            }

            it("should fail with an error message") {
                assertThat({ loadConfiguration(files, rootConfigPath) }, throws(withMessage("The task 'task-1' is defined in multiple files: /project/batect.yml and 1.yml from https://myrepo.com/bundles/bundle.git@v1.2.3")))
            }
        }

        on("loading a configuration file that references another configuration file in a Git repository both of which contain a definition for the same config variable") {
            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }
            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |config_variables:
                        |  config-var-1: {}
                        |
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: 1.yml
                    """.trimMargin(),
                    pathForGitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "1.yml") to """
                        |config_variables:
                        |  config-var-1: {}
                    """.trimMargin(),
                )
            }

            it("should fail with an error message") {
                assertThat({ loadConfiguration(files, rootConfigPath) }, throws(withMessage("The config variable 'config-var-1' is defined in multiple files: /project/batect.yml and 1.yml from https://myrepo.com/bundles/bundle.git@v1.2.3")))
            }
        }

        on("loading a configuration file that references another configuration file from a Git repository that cannot be cloned") {
            beforeEachTest {
                whenever(includeResolver.resolve(GitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3", "1.yml"), gitRepositoryCacheNotificationListener)).doThrow(GitException("Something went wrong."))
            }

            val rootConfigPath by createForEachTest { fileSystem.getPath("/project/batect.yml") }

            val files by createForEachTest {
                mapOf(
                    rootConfigPath to """
                        |include:
                        | - type: git
                        |   repo: https://myrepo.com/bundles/bundle.git
                        |   ref: v1.2.3
                        |   path: 1.yml
                    """.trimMargin(),
                )
            }

            it("should fail with an error message") {
                assertThat(
                    { loadConfiguration(files, rootConfigPath) },
                    throws(
                        withMessage("Could not load include '1.yml' from https://myrepo.com/bundles/bundle.git@v1.2.3: Something went wrong.") and withFileName("/project/batect.yml"),
                    ),
                )
            }
        }
    }
})

private fun withLineNumber(lineNumber: Int): Matcher<ConfigurationFileException> =
    has(ConfigurationFileException::lineNumber, equalTo(lineNumber))

private fun withColumn(column: Int): Matcher<ConfigurationFileException> =
    has(ConfigurationFileException::column, equalTo(column))

private fun withFileName(fileName: String): Matcher<ConfigurationFileException> =
    has(ConfigurationFileException::fileName, equalTo(fileName))

private fun withPath(path: String): Matcher<ConfigurationFileException> =
    has(ConfigurationFileException::path, equalTo(path))
