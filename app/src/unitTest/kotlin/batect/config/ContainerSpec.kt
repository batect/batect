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

package batect.config

import batect.config.io.deserializers.PathDeserializer
import batect.os.Command
import batect.os.PathResolutionContext
import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withColumn
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import batect.testutils.withPath
import com.charleskorn.kaml.Yaml
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import kotlinx.serialization.modules.serializersModuleOf
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.time.Duration.Companion.seconds

object ContainerSpec : Spek({
    describe("a container") {
        val pathResolverContext by createForEachTest { mock<PathResolutionContext>() }

        val pathResolver by createForEachTest {
            mock<PathResolver> {
                on { context } doReturn pathResolverContext
            }
        }

        val pathDeserializer by createForEachTest {
            mock<PathDeserializer> {
                on { this.pathResolver } doReturn pathResolver
            }
        }

        val parser by createForEachTest { Yaml(serializersModule = serializersModuleOf(PathResolutionResult::class, pathDeserializer)) }

        given("the config file has just a build directory") {
            val yaml = "build_directory: /some_build_dir"

            on("loading the configuration from the config file") {
                val result by runForEachTest { parser.decodeFromString(Container.Companion, yaml) }

                it("returns the expected container configuration") {
                    assertThat(result, equalTo(Container("UNNAMED-FROM-CONFIG-FILE", BuildImage(LiteralValue("/some_build_dir"), pathResolverContext, emptyMap(), "Dockerfile"))))
                }
            }
        }

        given("the config file has just an image") {
            val yaml = "image: some_image"

            on("loading the configuration from the config file") {
                val result by runForEachTest { parser.decodeFromString(Container.Companion, yaml) }

                it("returns the expected container configuration") {
                    assertThat(result, equalTo(Container("UNNAMED-FROM-CONFIG-FILE", PullImage("some_image"))))
                }
            }
        }

        given("the config file has both a build directory and an image") {
            val yaml = """
                image: some_image
                build_directory: /build_dir
            """.trimIndent()

            on("loading the configuration from the config file") {
                it("throws an appropriate exception") {
                    assertThat(
                        { parser.decodeFromString(Container.Companion, yaml) },
                        throws(withMessage("Only one of build_directory or image can be specified for a container, but both have been provided for this container.") and withLineNumber(1) and withColumn(1) and withPath("<root>"))
                    )
                }
            }
        }

        given("the config file has both an image and build args") {
            val yaml = """
                image: some_image
                build_args:
                  SOME_ARG: some_value
            """.trimIndent()

            on("loading the configuration from the config file") {
                it("throws an appropriate exception") {
                    assertThat(
                        { parser.decodeFromString(Container.Companion, yaml) },
                        throws(withMessage("build_args cannot be used with image, but both have been provided.") and withLineNumber(1) and withColumn(1) and withPath("<root>"))
                    )
                }
            }
        }

        given("the config file has both an image and a Dockerfile") {
            val yaml = """
                image: some_image
                dockerfile: some-Dockerfile
            """.trimIndent()

            on("loading the configuration from the config file") {
                it("throws an appropriate exception") {
                    assertThat(
                        { parser.decodeFromString(Container.Companion, yaml) },
                        throws(withMessage("dockerfile cannot be used with image, but both have been provided.") and withLineNumber(1) and withColumn(1) and withPath("<root>"))
                    )
                }
            }
        }

        given("the config file has both an image and a target stage") {
            val yaml = """
                image: some_image
                build_target: some-build-target
            """.trimIndent()

            on("loading the configuration from the config file") {
                it("throws an appropriate exception") {
                    assertThat(
                        { parser.decodeFromString(Container.Companion, yaml) },
                        throws(withMessage("build_target cannot be used with image, but both have been provided.") and withLineNumber(1) and withColumn(1) and withPath("<root>"))
                    )
                }
            }
        }

        given("the config file has neither a build directory nor an image") {
            val yaml = """
                command: do-the-thing
            """.trimIndent()

            on("loading the configuration from the config file") {
                it("throws an appropriate exception") {
                    assertThat(
                        { parser.decodeFromString(Container.Companion, yaml) },
                        throws(withMessage("One of either build_directory or image must be specified for each container, but neither have been provided for this container.") and withLineNumber(1) and withColumn(1) and withPath("<root>"))
                    )
                }
            }
        }

        given("the config file has all optional fields specified") {
            val yaml = """
                build_directory: /container-1-build-dir
                build_args:
                  SOME_ARG: some_value
                  SOME_DYNAMIC_VALUE: ${'$'}{host_var}
                build_target: some-build-stage
                dockerfile: some-Dockerfile
                command: do-the-thing.sh some-param
                entrypoint: sh
                environment:
                  OPTS: -Dthing
                  INT_VALUE: 1
                  FLOAT_VALUE: 12.6000
                  BOOL_VALUE: true
                  OTHER_VALUE: "the value"
                working_directory: /here
                volumes:
                  - /volume1:/here
                  - /somewhere:/else:ro
                devices:
                  - /dev/ttyUSB0:/dev/ttyUSB0
                  - /dev/sda:/dev/xvdc:r
                ports:
                  - "1234:5678"
                  - "9012:3456"
                health_check:
                  interval: 2s
                  retries: 10
                  start_period: 1s
                  timeout: 4s
                  command: exit 0
                run_as_current_user:
                  enabled: true
                  home_directory: /home/something
                privileged: true
                enable_init_process: true
                capabilities_to_add:
                  - NET_ADMIN
                capabilities_to_drop:
                  - KILL
                additional_hostnames:
                  - extra-name
                additional_hosts:
                  does.not.exist: 1.2.3.4
                setup_commands:
                  - command: /do/the/thing.sh
                  - command: /some/other/thing.sh
                    working_directory: /some/dir
                log_driver: my_log_driver
                log_options:
                  option_1: value_1
                image_pull_policy: Always
                shm_size: 2G
            """.trimIndent()

            on("loading the configuration from the config file") {
                val result by runForEachTest { parser.decodeFromString(Container.Companion, yaml) }

                it("returns the expected container configuration") {
                    assertThat(
                        result.imageSource,
                        equalTo(
                            BuildImage(
                                LiteralValue("/container-1-build-dir"),
                                pathResolverContext,
                                mapOf("SOME_ARG" to LiteralValue("some_value"), "SOME_DYNAMIC_VALUE" to EnvironmentVariableReference("host_var")),
                                "some-Dockerfile",
                                ImagePullPolicy.Always,
                                "some-build-stage"
                            )
                        )
                    )

                    assertThat(result.command, equalTo(Command.parse("do-the-thing.sh some-param")))
                    assertThat(result.entrypoint, equalTo(Command.parse("sh")))
                    assertThat(
                        result.environment,
                        equalTo(
                            mapOf(
                                "OPTS" to LiteralValue("-Dthing"),
                                "INT_VALUE" to LiteralValue("1"),
                                "FLOAT_VALUE" to LiteralValue("12.6000"),
                                "BOOL_VALUE" to LiteralValue("true"),
                                "OTHER_VALUE" to LiteralValue("the value")
                            )
                        )
                    )
                    assertThat(result.workingDirectory, equalTo("/here"))
                    assertThat(
                        result.volumeMounts,
                        equalTo(
                            setOf(
                                LocalMount(LiteralValue("/volume1"), pathResolverContext, "/here", null),
                                LocalMount(LiteralValue("/somewhere"), pathResolverContext, "/else", "ro")
                            )
                        )
                    )
                    assertThat(
                        result.deviceMounts,
                        equalTo(
                            setOf(
                                DeviceMount("/dev/ttyUSB0", "/dev/ttyUSB0", null),
                                DeviceMount("/dev/sda", "/dev/xvdc", "r")
                            )
                        )
                    )
                    assertThat(result.portMappings, equalTo(setOf(PortMapping(1234, 5678), PortMapping(9012, 3456))))
                    assertThat(result.healthCheckConfig, equalTo(HealthCheckConfig(2.seconds, 10, 1.seconds, 4.seconds, "exit 0")))
                    assertThat(result.runAsCurrentUserConfig, equalTo(RunAsCurrentUserConfig.RunAsCurrentUser("/home/something")))
                    assertThat(result.privileged, equalTo(true))
                    assertThat(result.enableInitProcess, equalTo(true))
                    assertThat(result.capabilitiesToAdd, equalTo(setOf(Capability.NET_ADMIN)))
                    assertThat(result.capabilitiesToDrop, equalTo(setOf(Capability.KILL)))
                    assertThat(result.additionalHostnames, equalTo(setOf("extra-name")))
                    assertThat(result.additionalHosts, equalTo(mapOf("does.not.exist" to "1.2.3.4")))
                    assertThat(
                        result.setupCommands,
                        equalTo(
                            listOf(
                                SetupCommand(Command.parse("/do/the/thing.sh")),
                                SetupCommand(Command.parse("/some/other/thing.sh"), "/some/dir")
                            )
                        )
                    )
                    assertThat(result.logDriver, equalTo("my_log_driver"))
                    assertThat(result.logOptions, equalTo(mapOf("option_1" to "value_1")))
                    assertThat(result.shmSize, equalTo(BinarySize.of(2, BinaryUnit.Gigabyte)))
                }
            }
        }
    }
})
