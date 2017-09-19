/*
   Copyright 2017 Charles Korn.

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

import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.isEmptyString
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import batect.config.Configuration
import batect.config.PortMapping
import batect.config.VolumeMount
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.mockito.ArgumentMatchers.anyString
import java.nio.file.Files

object ConfigurationLoaderSpec : Spek({
    describe("a configuration loader") {
        val fileSystem = Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix())

        val pathResolver = mock<PathResolver> {
            on { resolve(anyString()) } doAnswer { invocation ->
                val path = invocation.arguments[0] as String
                ResolvedToDirectory("/resolved/$path")
            }
        }

        val pathResolverFactory = mock<PathResolverFactory> {
            on { createResolver(fileSystem.getPath("/")) } doReturn pathResolver
        }

        val testFileName = "/theTestFile.yml"
        val loader = ConfigurationLoader(pathResolverFactory, fileSystem)

        fun loadConfiguration(config: String): Configuration {
            val filePath = fileSystem.getPath(testFileName)
            Files.write(filePath, config.toByteArray())

            try {
                return loader.loadConfig(testFileName)
            } finally {
                Files.delete(filePath)
            }
        }

        on("loading an empty configuration file") {
            it("should fail with an error message") {
                assertThat({ loadConfiguration("") }, throws(withMessage("File '$testFileName' is empty")))
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
                assertThat(task.runConfiguration.command, equalTo("./gradlew doStuff"))
                assertThat(task.dependencies, isEmpty)
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
                assertThat(task.dependencies, isEmpty)
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
                assertThat(task.dependencies, isEmpty)
                assertThat(task.description, equalTo("The very first task."))
            }
        }

        on("loading a valid configuration file with a task with some dependencies") {
            val configString = """
                |project_name: the_cool_project
                |
                |tasks:
                |  first_task:
                |    run:
                |      container: build-env
                |      command: ./gradlew doStuff
                |    start:
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
                assertThat(task.runConfiguration.command, equalTo("./gradlew doStuff"))
                assertThat(task.dependencies, equalTo(setOf("dependency-1", "dependency-2")))
                assertThat(task.description, isEmptyString)
            }
        }

        on("loading a configuration file with a task that has a dependency defined twice") {
            val config = """
                |project_name: the_cool_project
                |
                |tasks:
                |  first_task:
                |    run:
                |      container: build-env
                |    start:
                |      - dependency-1
                |      - dependency-1
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Duplicate value 'dependency-1'") and withLineNumber(9)))
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
                assertThat(container.buildDirectory, equalTo("/resolved/container-1-build-dir"))
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
                    |      - OPTS=-Dthing
                    |      - BOOL_VALUE=1
                    |    working_directory: /here
                    |    volumes:
                    |      - ../:/here
                    |      - /somewhere:/else:ro
                    |    ports:
                    |      - "1234:5678"
                    |      - "9012:3456"
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
                assertThat(container.buildDirectory, equalTo("/resolved/container-1-build-dir"))
                assertThat(container.command, equalTo("do-the-thing.sh some-param"))
                assertThat(container.environment, equalTo(mapOf("OPTS" to "-Dthing", "BOOL_VALUE" to "1")))
                assertThat(container.workingDirectory, equalTo("/here"))
                assertThat(container.portMappings, equalTo(setOf(PortMapping(1234, 5678), PortMapping(9012, 3456))))
                assertThat(container.volumeMounts, equalTo(setOf(
                        VolumeMount("/resolved/../", "/here", null),
                        VolumeMount("/resolved//somewhere", "/else", "ro")
                )))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Duplicate value 'container-2'") and withLineNumber(8)))
            }
        }

        on("loading a configuration file where the project name is given twice") {
            val config = """
                |project_name: the_cool_project
                |project_name: the_really_cool_project
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Duplicate field 'project_name'") and withLineNumber(2)))
            }
        }

        on("loading a configuration file where an unknown field name is used") {
            val config = """
                |project_name: the_cool_project
                |thing: value
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Unknown field 'thing'") and withLineNumber(2)))
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

        on("loading a configuration file with no project name given") {
            val config = """
                |tasks:
                |  first_task:
                |    run:
                |      container: build-env
                |      command: ./gradlew doStuff
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Missing required field 'project_name'")))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Duplicate field 'first_task'") and withLineNumber(7)))
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
                assertThat({ loadConfiguration(config) }, throws(withMessage("Duplicate field 'container-1'") and withLineNumber(6)))
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
                |      - THING=value1
                |      - THING=value2
                """.trimMargin()

            it("should fail with an error message") {
                assertThat({ loadConfiguration(config) }, throws(withMessage("Duplicate environment variable 'THING'") and withLineNumber(8)))
            }
        }

        on("loading attempting to load a configuration file that does not exist") {
            it("should fail with an appropriate error message") {
                assertThat({ loader.loadConfig("/doesntexist.yml") }, throws(withMessage("The file '/doesntexist.yml' does not exist.")))
            }
        }
    }
})
