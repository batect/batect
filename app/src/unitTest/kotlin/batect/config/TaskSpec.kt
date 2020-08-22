/*
   Copyright 2017-2020 Charles Korn.

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

package batect.config

import batect.config.io.ConfigurationException
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.withColumn
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import batect.utils.Json
import com.charleskorn.kaml.Yaml
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TaskSpec : Spek({
    describe("a task") {
        describe("reading a task from YAML") {
            given("a task with a container to run") {
                val yaml = """
                    run:
                        container: the-container
                """.trimIndent()

                val task by createForEachTest { Yaml.default.decodeFromString(Task.serializer(), yaml) }

                it("successfully reads the task") {
                    assertThat(task, equalTo(Task(
                        runConfiguration = TaskRunConfiguration("the-container")
                    )))
                }
            }

            given("a task with all possible fields provided") {
                val yaml = """
                    description: Does the thing.
                    group: Things
                    prerequisites:
                        - the-other-task
                    dependencies:
                        - the-other-container
                    run:
                        container: the-container
                """.trimIndent()

                val task by createForEachTest { Yaml.default.decodeFromString(Task.serializer(), yaml) }

                it("successfully reads the task") {
                    assertThat(task, equalTo(Task(
                        description = "Does the thing.",
                        group = "Things",
                        prerequisiteTasks = listOf("the-other-task"),
                        dependsOnContainers = setOf("the-other-container"),
                        runConfiguration = TaskRunConfiguration("the-container")
                    )))
                }
            }

            given("a task with no container to run and no prerequisites") {
                val yaml = """
                    description: Does the thing.
                """.trimIndent()

                it("throws an appropriate exception") {
                    assertThat({ Yaml.default.decodeFromString(Task.serializer(), yaml) }, throws<ConfigurationException>(withMessage("At least one of 'run' or 'prerequisites' is required.") and withLineNumber(1) and withColumn(1)))
                }
            }

            given("a task with no container to run and some dependencies") {
                val yaml = """
                    description: Does the thing.
                    prerequisites:
                        - the-other-task
                    dependencies:
                        - the-other-container
                """.trimIndent()

                it("throws an appropriate exception") {
                    assertThat({ Yaml.default.decodeFromString(Task.serializer(), yaml) }, throws<ConfigurationException>(withMessage("'run' is required if 'dependencies' is provided.") and withLineNumber(1) and withColumn(1)))
                }
            }
        }

        describe("writing a task as JSON for logging") {
            val task = Task(
                name = "the-task",
                description = "Does the thing.",
                group = "Things",
                prerequisiteTasks = listOf("the-other-task"),
                dependsOnContainers = setOf("the-other-container"),
                runConfiguration = TaskRunConfiguration("the-container")
            )

            it("serializes to the expected JSON") {
                assertThat(Json.forLogging.encodeToString(Task.serializer(), task), equivalentTo("""
                    {
                        "description": "Does the thing.",
                        "group": "Things",
                        "prerequisites": ["the-other-task"],
                        "dependencies": ["the-other-container"],
                        "run": {
                            "container": "the-container",
                            "command": null,
                            "entrypoint": null,
                            "environment": {},
                            "ports": [],
                            "working_directory": null
                        }
                    }
                """.trimIndent()))
            }
        }
    }
})
