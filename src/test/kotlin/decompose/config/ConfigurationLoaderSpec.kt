package decompose.config

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ConfigurationLoaderSpec : Spek({
    describe("a configuration loader") {
        val loader = ConfigurationLoader()
        val testFileName = "theTestFile.yml"

        fun loadConfiguration(config: String): ConfigurationFile {
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
            it("should return a populated configuration object") {
                val config = loadConfiguration("project_name: the_cool_project")

                assert.that(config.projectName, equalTo("the_cool_project"))
            }
        }

        on("loading a valid configuration file with a task with no dependencies") {
            it("should return a populated configuration object") {
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
                assert.that(config.projectName, equalTo("the_cool_project"))
                assert.that(config.tasks.keys, equalTo(setOf("first_task")))

                val task = config.tasks["first_task"]!!
                assert.that(task.runConfiguration.container, equalTo("build-env"))
                assert.that(task.runConfiguration.command, equalTo("./gradlew doStuff"))
            }
        }

        on("loading a valid configuration file with a task with no command") {
            it("should return a populated configuration object") {
                val configString = """
                    |project_name: the_cool_project
                    |
                    |tasks:
                    |  first_task:
                    |    run:
                    |      container: build-env
                    """.trimMargin()

                val config = loadConfiguration(configString)
                assert.that(config.projectName, equalTo("the_cool_project"))
                assert.that(config.tasks.keys, equalTo(setOf("first_task")))

                val task = config.tasks["first_task"]!!
                assert.that(task.runConfiguration.container, equalTo("build-env"))
                assert.that(task.runConfiguration.command, absent())
            }
        }

        on("loading a valid configuration file with a task with some dependencies") {
            it("should return a populated configuration object") {
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
                assert.that(config.projectName, equalTo("the_cool_project"))
                assert.that(config.tasks.keys, equalTo(setOf("first_task")))

                val task = config.tasks["first_task"]!!
                assert.that(task.runConfiguration.container, equalTo("build-env"))
                assert.that(task.runConfiguration.command, equalTo("./gradlew doStuff"))
                assert.that(task.dependencies, equalTo(listOf("dependency-1", "dependency-2")))
            }
        }

        on("loading a configuration file where the project name is given twice") {
            it("should fail with an error message") {
                val config = """
                    |project_name: the_cool_project
                    |project_name: the_really_cool_project
                    """.trimMargin()

                assert.that({ loadConfiguration(config) }, throws(withMessage("Duplicate field 'project_name'")))
            }
        }

        on("loading a configuration file where an unknown field name is used") {
            it("should fail with an error message") {
                val config = """
                    |project_name: the_cool_project
                    |thing: value
                    """.trimMargin()

                assert.that({ loadConfiguration(config) }, throws(withMessage("Unknown field 'thing'")))
            }
        }

        on("loading a configuration file with a full-line comment") {
            it("should ignore the comment") {
                val configString = """
                    |# This is a comment
                    |project_name: the_cool_project
                    """.trimMargin()

                val config = loadConfiguration(configString)

                assert.that(config.projectName, equalTo("the_cool_project"))
            }
        }

        on("loading a configuration file with an end-of-line comment") {
            it("should ignore the comment") {
                val config = loadConfiguration("project_name: the_cool_project # This is a comment")

                assert.that(config.projectName, equalTo("the_cool_project"))
            }
        }

        on("loading a configuration file with no project name given") {
            it("should fail with an error message") {
                val config = """
                    |tasks:
                    |  first_task:
                    |    run:
                    |      container: build-env
                    |      command: ./gradlew doStuff
                    """.trimMargin()

                assert.that({ loadConfiguration(config) }, throws(withMessage("Missing required field 'project_name'")))
            }
        }

        on("loading a configuration file with a task defined twice") {
            it("should fail with an error message") {
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

                assert.that({ loadConfiguration(config) }, throws(withMessage("Duplicate field 'first_task'")))
            }
        }
    }
})
