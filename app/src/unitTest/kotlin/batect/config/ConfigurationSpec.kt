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

package batect.config

import batect.docker.Capability
import batect.os.Command
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.osIndependentPath
import batect.utils.Json
import com.natpryce.hamkrest.assertion.assertThat
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration

object ConfigurationSpec : Spek({
    describe("a set of configuration") {
        describe("converting it to JSON for logging") {
            given("a single task") {
                val task = Task(
                    "the-task",
                    TaskRunConfiguration(
                        "the-container",
                        Command.parse("the-command"),
                        Command.parse("the-entrypoint"),
                        mapOf(
                            "SOME_VAR" to LiteralValue("blah"),
                            "SOME_REFERENCE" to ReferenceValue("REFERENCE_TO"),
                            "SOME_REFERENCE_WITH_DEFAULT" to ReferenceValue("REF_2", "the-default")
                        ),
                        setOf(PortMapping(123, 456)),
                        "/some/work/dir"
                    ),
                    "Does the thing",
                    "Group 1",
                    setOf("container-1"),
                    listOf("task-1")
                )

                val configuration = Configuration("the-project", TaskMap(task), ContainerMap())

                val json by createForEachTest { Json.parser.stringify(Configuration.serializer(), configuration) }

                it("serializes the configuration to the expected value") {
                    assertThat(json, equivalentTo("""
                        {
                            "project_name": "the-project",
                            "tasks": {
                                "the-task": {
                                    "run": {
                                        "container": "the-container",
                                        "command": ["the-command"],
                                        "entrypoint": ["the-entrypoint"],
                                        "environment": {
                                            "SOME_VAR": "blah",
                                            "SOME_REFERENCE": "${'$'}REFERENCE_TO",
                                            "SOME_REFERENCE_WITH_DEFAULT": "${'$'}{REF_2:-the-default}"
                                        },
                                        "ports": [
                                            { "local": 123, "container": 456 }
                                        ],
                                        "working_directory": "/some/work/dir"
                                    },
                                    "description": "Does the thing",
                                    "group": "Group 1",
                                    "dependencies": ["container-1"],
                                    "prerequisites": ["task-1"]
                                }
                            },
                            "containers": {},
                            "config_variables": {}
                        }
                    """.trimIndent()))
                }
            }

            given("a single container with many options that pulls an image and runs as the current user") {
                val container = Container(
                    "the-container",
                    PullImage("the-image"),
                    Command.parse("the-command"),
                    Command.parse("sh"),
                    mapOf("SOME_VAR" to LiteralValue("some-value")),
                    "/some/working/dir",
                    setOf(VolumeMount("/local/path", "/container/path", "some-options")),
                    setOf(DeviceMount("/dev/local", "/dev/container", "device-options")),
                    setOf(PortMapping(123, 456)),
                    setOf("other-container"),
                    HealthCheckConfig(Duration.ofSeconds(1), 23, Duration.ofSeconds(4)),
                    RunAsCurrentUserConfig.RunAsCurrentUser("/some/home/dir"),
                    true,
                    true,
                    setOf(Capability.AUDIT_CONTROL),
                    setOf(Capability.BLOCK_SUSPEND),
                    setOf("other-name"),
                    listOf(SetupCommand(Command.parse("some-command"), "/some/dir"))
                )

                val configuration = Configuration("the-project", TaskMap(), ContainerMap(container))

                val json by createForEachTest { Json.parser.stringify(Configuration.serializer(), configuration) }

                it("serializes the configuration to the expected value") {
                    assertThat(json, equivalentTo("""
                        {
                            "project_name": "the-project",
                            "tasks": {},
                            "containers": {
                                "the-container": {
                                    "image": "the-image",
                                    "command": ["the-command"],
                                    "entrypoint": ["sh"],
                                    "environment": {
                                        "SOME_VAR": "some-value"
                                    },
                                    "working_directory": "/some/working/dir",
                                    "volumes": [
                                        {
                                            "local": "/local/path",
                                            "container": "/container/path",
                                            "options": "some-options"
                                        }
                                    ],
                                    "devices": [
                                        {
                                            "local": "/dev/local",
                                            "container": "/dev/container",
                                            "options": "device-options"
                                        }
                                    ],
                                    "ports": [
                                        { "local": 123, "container": 456 }
                                    ],
                                    "dependencies": ["other-container"],
                                    "health_check": {
                                        "interval": "1s",
                                        "retries": 23,
                                        "start_period": "4s"
                                    },
                                    "run_as_current_user": {
                                        "enabled": true,
                                        "home_directory": "/some/home/dir"
                                    },
                                    "privileged": true,
                                    "enable_init_process": true,
                                    "capabilities_to_add": ["AUDIT_CONTROL"],
                                    "capabilities_to_drop": ["BLOCK_SUSPEND"],
                                    "additional_hostnames": ["other-name"],
                                    "setup_commands": [
                                        { "command": ["some-command"], "working_directory": "/some/dir" }
                                    ]
                                }
                            },
                            "config_variables": {}
                        }
                    """.trimIndent()))
                }
            }

            given("a single container with few options that builds an image and runs as the default container user") {
                val container = Container(
                    "the-container",
                    BuildImage(
                        osIndependentPath("/some/build/dir"),
                        mapOf(
                            "SOME_VAR" to LiteralValue("blah"),
                            "SOME_REFERENCE" to ReferenceValue("REFERENCE_TO"),
                            "SOME_REFERENCE_WITH_DEFAULT" to ReferenceValue("REF_2", "the-default")
                        ),
                        "some-dockerfile"
                    ),
                    runAsCurrentUserConfig = RunAsCurrentUserConfig.RunAsDefaultContainerUser
                )

                val configuration = Configuration("the-project", TaskMap(), ContainerMap(container))

                val json by createForEachTest { Json.parser.stringify(Configuration.serializer(), configuration) }

                it("serializes the configuration to the expected value") {
                    assertThat(json, equivalentTo("""
                        {
                            "project_name": "the-project",
                            "tasks": {},
                            "containers": {
                                "the-container": {
                                    "build_directory": "/some/build/dir",
                                    "build_args": {
                                        "SOME_VAR": "blah",
                                        "SOME_REFERENCE": "${'$'}REFERENCE_TO",
                                        "SOME_REFERENCE_WITH_DEFAULT": "${'$'}{REF_2:-the-default}"
                                    },
                                    "dockerfile": "some-dockerfile",
                                    "command": null,
                                    "entrypoint": null,
                                    "environment": {},
                                    "working_directory": null,
                                    "volumes": [],
                                    "devices": [],
                                    "ports": [],
                                    "dependencies": [],
                                    "health_check": {
                                        "interval": null,
                                        "retries": null,
                                        "start_period": null
                                    },
                                    "run_as_current_user": {
                                        "enabled": false
                                    },
                                    "privileged": false,
                                    "enable_init_process": false,
                                    "capabilities_to_add": [],
                                    "capabilities_to_drop": [],
                                    "additional_hostnames": [],
                                    "setup_commands": []
                                }
                            },
                            "config_variables": {}
                        }
                    """.trimIndent()))
                }
            }

            given("a single config variable") {
                val configVariable = ConfigVariableDefinition("some-variable", "Some description", "Some default")
                val configuration = Configuration("the-project", TaskMap(), ContainerMap(), ConfigVariableMap(configVariable))
                val json by createForEachTest { Json.parser.stringify(Configuration.serializer(), configuration) }

                it("serializes the configuration to the expected value") {
                    assertThat(json, equivalentTo("""
                        {
                            "project_name": "the-project",
                            "tasks": {},
                            "containers": {},
                            "config_variables": {
                                "some-variable": {
                                    "description": "Some description",
                                    "default": "Some default"
                                }
                            }
                        }
                    """.trimIndent()))
                }
            }
        }
    }
})
