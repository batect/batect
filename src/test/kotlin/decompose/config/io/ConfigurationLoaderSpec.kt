package decompose.config.io

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import decompose.config.Configuration
import decompose.config.PortMapping
import decompose.config.VolumeMount
import decompose.testutils.withLineNumber
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ConfigurationLoaderSpec : Spek({
    describe("a configuration loader") {
        val loader = ConfigurationLoader()
        val testFileName = "theTestFile.yml"

        fun loadConfiguration(config: String): Configuration {
            return config.byteInputStream().use {
                loader.loadConfig(it, testFileName)
            }
        }

        on("loading an empty configuration file") {
            it("should fail with an error message") {
                assert.that({ loadConfiguration("") }, throws(withMessage("File '$testFileName' is empty")))
            }
        }

        on("loading a valid configuration file with no containers or tasks defined") {
            val config = loadConfiguration("project_name: the_cool_project")

            it("should return a populated configuration object with the project name specified") {
                assert.that(config.projectName, equalTo("the_cool_project"))
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
                assert.that(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single task specified") {
                assert.that(config.tasks.keys, equalTo(setOf("first_task")))
            }

            it("should load the configuration for the task") {
                val task = config.tasks["first_task"]!!
                assert.that(task.name, equalTo("first_task"))
                assert.that(task.runConfiguration.container, equalTo("build-env"))
                assert.that(task.runConfiguration.command, equalTo("./gradlew doStuff"))
                assert.that(task.dependencies, equalTo(emptySet()))
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
                assert.that(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single task specified") {
                assert.that(config.tasks.keys, equalTo(setOf("first_task")))
            }

            it("should load the configuration for the task") {
                val task = config.tasks["first_task"]!!
                assert.that(task.name, equalTo("first_task"))
                assert.that(task.runConfiguration.container, equalTo("build-env"))
                assert.that(task.runConfiguration.command, absent())
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
                assert.that(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single task specified") {
                assert.that(config.tasks.keys, equalTo(setOf("first_task")))
            }

            it("should load the configuration for the task") {
                val task = config.tasks["first_task"]!!
                assert.that(task.name, equalTo("first_task"))
                assert.that(task.runConfiguration.container, equalTo("build-env"))
                assert.that(task.runConfiguration.command, equalTo("./gradlew doStuff"))
                assert.that(task.dependencies, equalTo(setOf("dependency-1", "dependency-2")))
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
                assert.that({ loadConfiguration(config) }, throws(withMessage("Duplicate value 'dependency-1'") and withLineNumber(9)))
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
                assert.that(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single container specified") {
                assert.that(config.containers.keys, equalTo(setOf("container-1")))
            }

            it("should load the build directory specified for the container") {
                val container = config.containers["container-1"]!!
                assert.that(container.name, equalTo("container-1"))
                assert.that(container.buildDirectory, equalTo("container-1-build-dir"))
            }
        }

        on("loading a valid configuration file with a container with all optional fields given") {
            val configString = """
                    |project_name: the_cool_project
                    |
                    |containers:
                    |  container-1:
                    |    build_directory: container-1-build-dir
                    |    environment:
                    |      - OPTS=-Dthing
                    |      - BOOL_VALUE=1
                    |    working_directory: /here
                    |    volumes:
                    |      - ../:/here
                    |      - /somewhere:/else
                    |    ports:
                    |      - "1234:5678"
                    |      - "9012:3456"
                    """.trimMargin()

            val config = loadConfiguration(configString)

            it("should load the project name") {
                assert.that(config.projectName, equalTo("the_cool_project"))
            }

            it("should load the single container specified") {
                assert.that(config.containers.keys, equalTo(setOf("container-1")))
            }

            it("should load all of the configuration specified for the container") {
                val container = config.containers["container-1"]!!
                assert.that(container.name, equalTo("container-1"))
                assert.that(container.buildDirectory, equalTo("container-1-build-dir"))
                assert.that(container.environment, equalTo(mapOf("OPTS" to "-Dthing", "BOOL_VALUE" to "1")))
                assert.that(container.workingDirectory, equalTo("/here"))
                assert.that(container.volumeMounts, equalTo(setOf(VolumeMount("../", "/here"), VolumeMount("/somewhere", "/else"))))
                assert.that(container.portMappings, equalTo(setOf(PortMapping(1234, 5678), PortMapping(9012, 3456))))
            }
        }

        on("loading a configuration file where the project name is given twice") {
            val config = """
                |project_name: the_cool_project
                |project_name: the_really_cool_project
                """.trimMargin()

            it("should fail with an error message") {
                assert.that({ loadConfiguration(config) }, throws(withMessage("Duplicate field 'project_name'") and withLineNumber(2)))
            }
        }

        on("loading a configuration file where an unknown field name is used") {
            val config = """
                |project_name: the_cool_project
                |thing: value
                """.trimMargin()

            it("should fail with an error message") {
                assert.that({ loadConfiguration(config) }, throws(withMessage("Unknown field 'thing'") and withLineNumber(2)))
            }
        }

        on("loading a configuration file with a full-line comment") {
            val configString = """
                |# This is a comment
                |project_name: the_cool_project
                """.trimMargin()

            val config = loadConfiguration(configString)

            it("should ignore the comment") {
                assert.that(config.projectName, equalTo("the_cool_project"))
            }
        }

        on("loading a configuration file with an end-of-line comment") {
            val config = loadConfiguration("project_name: the_cool_project # This is a comment")

            it("should ignore the comment") {
                assert.that(config.projectName, equalTo("the_cool_project"))
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
                assert.that({ loadConfiguration(config) }, throws(withMessage("Missing required field 'project_name'")))
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
                assert.that({ loadConfiguration(config) }, throws(withMessage("Duplicate field 'first_task'") and withLineNumber(7)))
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
                assert.that({ loadConfiguration(config) }, throws(withMessage("Duplicate field 'container-1'") and withLineNumber(6)))
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
                assert.that({ loadConfiguration(config) }, throws(withMessage("Duplicate environment variable 'THING'") and withLineNumber(8)))
            }
        }
    }
})
